package strm.emfcompat.animationadditions.gesture;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.interaction.ArmAim;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.animationadditions.torso.BraceSteps;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.core.ik.IKFrame;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A short authored gesture that follows something the player really did: it is set off, waits
 * for a fit moment, plays once over its phase 0..1 and gives everything back. A gesture says
 * where the hands go, how the head and the torso join in and how the feet stand
 * ({@link #pose}); the arbiter, the runtime's fades, the torso and the brace steps do the rest,
 * the same for all of them. A gesture ends in the pose it left, so letting go of it shows nothing.
 */
public abstract class Gesture implements InteractionProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatGesture");
    private static final List<Gesture> ALL = new ArrayList<>();
    /** A short release: the runtime eases off the last pose over some six times this (see PocketStash). */
    private static final Candidate.Timing TIMING = new Candidate.Timing(.22, .05, .08);
    private static final long BUSY_NANOS = 14_000_000_000L;
    private static final float NECK = (float) Math.toRadians(70);
    private static final Vector3f HOME = new Vector3f();

    /** What a gesture asks for at one moment. Angles in radians, points in model pixels. */
    public static final class Pose {
        /** {xRot, yRot, zRot, the whole arm moved along y}; {@code null} leaves the arm alone. */
        public float[] right, left;
        /**
         * A point the hand goes to instead, from where the shoulder really is, and how far it has
         * got there from hanging by the side, 0..1. {@code onBody}: the point is a place on the
         * player's own body, given for an upright one, and goes with the shoulder as the torso bends.
         */
        public Vector3f rightAt, leftAt;
        public float rightReach, leftReach;
        public boolean onBody;

        public void hand(boolean right, Vector3f at, float reach) {
            if (right) {
                rightAt = at;
                rightReach = reach;
            } else {
                leftAt = at;
                leftReach = reach;
            }
        }
        /** {pitch, yaw} added to where the head looks. */
        public float[] head;
        public float pitch, yaw, roll;
        /** Where the soles stand from where the pack has them, while {@link #apart}. */
        public Vector3f rightFoot, leftFoot;
        public boolean apart;
        /** From this phase on the arms and the head are the pack's again. */
        public float letGo = .9f;

        void reset() {
            right = left = head = null;
            rightAt = leftAt = rightFoot = leftFoot = null;
            pitch = yaw = roll = 0;
            apart = onBody = false;
            rightReach = leftReach = 0;
            letGo = .9f;
        }
    }

    /** One player's run of a gesture. */
    public static final class Play {
        public AbstractClientPlayer player;
        public IKFrame frame;
        public boolean pending, playing;
        public long at;
        public float phase = 1;
        /** Seconds this run has been held at {@link Gesture#holdAt}. */
        public float held;
        public int kind;
        public Vec3 point;
        public int entity = -1;
        public boolean right = true;
        /** A gesture's own notes for this player. */
        public Object notes;
        final Pose pose = new Pose();
        final BraceSteps.State stance = new BraceSteps.State();
        Effector primary = Effector.RIGHT_ARM;
        boolean shows;
    }

    private final EntityStates<Play> states = new EntityStates<>(Play::new);
    private long busyAt = System.nanoTime() - BUSY_NANOS;

    protected Gesture() {
        ALL.add(this);
    }

    /** How long the gesture takes, seconds. */
    protected abstract double seconds(Play play);

    /** What it asks for at {@code phase}; {@code out} comes empty. */
    protected abstract void pose(Play play, float phase, Pose out);

    /** From being set off to starting, and how long after that it is still worth showing. */
    protected long waitNanos() {
        return 0;
    }

    protected long giveUpNanos() {
        return 2_500_000_000L;
    }

    /** A gesture that is held: at this phase it stays for as long as {@link #sustain} says. */
    protected float holdAt() {
        return 2f;
    }

    protected boolean sustain(Play play) {
        return false;
    }

    /** Looked at every frame for every player, before anything else: for gestures that set themselves off. */
    protected void watch(InteractionContext context, Play play) {
    }

    protected boolean watches() {
        return false;
    }

    protected boolean ready(AbstractClientPlayer player) {
        return !player.isSleeping() && !player.isSpectator() && !player.isFallFlying() && !player.isSwimming();
    }

    protected final Play play(AbstractClientPlayer player) {
        return states.seen(player.getUUID(), System.nanoTime()).value;
    }

    /** Sets the gesture off for this player; one already under way is not started again. */
    public final Play trigger(AbstractClientPlayer player, int kind, Vec3 point) {
        long now = System.nanoTime();
        Play play = states.seen(player.getUUID(), now).value;
        busyAt = now;
        if (play.playing && !again(play, kind, point)) return play;
        if (!play.playing) {
            play.pending = true;
            play.at = now;
        }
        play.kind = kind;
        play.point = point;
        return play;
    }

    /** Set off again while it plays: {@code true} takes the new kind and point into the run under way. */
    protected boolean again(Play play, int kind, Vec3 point) {
        return false;
    }

    private boolean quiet(long now) {
        return now - busyAt > BUSY_NANOS;
    }

    /** A world point in model pixels this frame. */
    protected static Vector3f model(Play play, Vec3 world) {
        return play.frame.relativeToJoint(world, new Vector3f());
    }

    protected static float smooth(float v) {
        v = Math.max(0, Math.min(1, v));
        return v * v * (3 - 2 * v);
    }

    /** 0 → 1 over {@code in}, held, 1 → 0 from {@code out} to {@code end}. */
    protected static float bell(float phase, float in, float out, float end) {
        return smooth(phase / in) * (1 - smooth((phase - out) / (end - out)));
    }

    @Override
    public final void collect(InteractionContext context, List<Candidate> out) {
        long now = context.now();
        AbstractClientPlayer player = context.player();
        Play play = null;
        if (watches()) {
            play = states.seen(player.getUUID(), now).value;
            play.player = player;
            play.frame = context.frame();
            watch(context, play);
        }
        if (quiet(now)) return;
        if (play == null) play = states.seen(player.getUUID(), now).value;
        play.player = player;
        play.frame = context.frame();
        play.shows = false;
        if (play.playing) {
            busyAt = now;
            float step = (float) (Math.min(.1, context.dt()) / Math.max(.2, seconds(play)));
            float hold = holdAt();
            if (play.phase < hold && play.phase + step >= hold && sustain(play)) {
                play.phase = hold;
                play.held += (float) Math.min(.1, context.dt());
            } else if (play.phase == hold && sustain(play)) {
                play.held += (float) Math.min(.1, context.dt());
            } else {
                play.phase += step;
            }
            if (play.phase >= 1) {
                play.phase = 1;
                play.playing = false;
            }
        } else if (play.pending) {
            long since = now - play.at;
            if (since < waitNanos()) {
                context.decide("wait");
                return;
            }
            if (since > waitNanos() + giveUpNanos()) {
                play.pending = false;
                context.decide("dropped");
                return;
            }
            if (!ready(player)) {
                context.decide("defer");
                return;
            }
            play.pending = false;
            play.playing = true;
            play.phase = 0;
            play.held = 0;
        }
        if (!play.playing) {
            context.decide("idle");
            return;
        }
        Pose pose = play.pose;
        pose.reset();
        pose(play, play.phase, pose);
        if (play.phase >= pose.letGo) {
            context.decide("let go");
            return;
        }
        Map<Effector, float[]> aims = new EnumMap<>(Effector.class);
        float[] r = arm(pose.right, pose.rightAt, pose.rightReach, Skeleton.RIGHT_SHOULDER);
        float[] l = arm(pose.left, pose.leftAt, pose.leftReach, Skeleton.LEFT_SHOULDER);
        if (r != null) aims.put(Effector.RIGHT_ARM, r);
        if (l != null) aims.put(Effector.LEFT_ARM, l);
        if (pose.head != null) {
            // The head leaves from where it looks and comes back to it, whatever the camera does meanwhile.
            float yaw = (float) Math.toRadians(Mth.wrapDegrees(player.yHeadRot - player.yBodyRot));
            aims.put(Effector.HEAD, new float[]{(float) Math.toRadians(player.getXRot()) + pose.head[0],
                    Math.max(-NECK, Math.min(NECK, yaw + pose.head[1]))});
        }
        if (aims.isEmpty()) {
            context.decide("body");
            play.shows = true;
            play.primary = null;
            return;
        }
        play.primary = r != null ? Effector.RIGHT_ARM : l != null ? Effector.LEFT_ARM : Effector.HEAD;
        play.shows = true;
        out.add(Candidate.of(id(), Category.USE, 6, 1f, TIMING, aims));
        // What set it off was a click, and a click swings the arm: the gesture is what shows.
        context.claimArms();
        context.decide(play.phase < holdAt() ? "play" : "hold");
    }

    /**
     * The aim of an arm from {@code pivot} at a point, {@code reach} of the way there from hanging.
     * The way is taken in the arm's angles, not along a line for the hand: a hand brought down
     * from the head along a line passes the shoulder itself and the arm whips round it.
     */
    private static float[] arm(float[] angles, Vector3f at, float reach, Vector3f pivot) {
        if (angles != null) return new float[]{angles[0], angles[1]};
        if (at == null) return null;
        float[] aim = towards(at.x - pivot.x, at.y - pivot.y, at.z - pivot.z);
        if (aim == null) return null;
        reach = Math.max(0, Math.min(1, reach));
        return new float[]{aim[0] * reach, aim[1] * reach};
    }

    /**
     * {xRot, yRot} that point an arm that way. Which way round a hanging arm is turned says nothing
     * - nearly straight down, a point a pixel behind the shoulder would spin it half round - so the
     * turn is taken only as far as the point is out to a side.
     */
    static float[] towards(float x, float y, float z) {
        float[] aim = ArmAim.angles(x, y, z);
        if (aim == null) return null;
        aim[1] *= smooth((float) Math.hypot(x, z) / 5f);
        return aim;
    }

    private float shown(UUID uuid, Play play) {
        if (!play.playing || !isEnabled()) return 0;
        if (play.primary == null) return play.shows ? 1 : 0;
        return InteractionRuntime.weight(uuid, play.primary, id());
    }

    /** What all the gestures under way ask of this player's torso. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        long now = System.nanoTime();
        float pitch = 0, yaw = 0, roll = 0;
        for (Gesture gesture : ALL) {
            if (gesture.quiet(now)) continue;
            Play play = gesture.states.fresh(uuid);
            if (play == null) continue;
            float shown = gesture.shown(uuid, play);
            pitch += play.pose.pitch * shown;
            yaw += play.pose.yaw * shown;
            roll += play.pose.roll * shown;
        }
        return pitch == 0 && yaw == 0 && roll == 0 ? null : TorsoLean.Hint.turn(pitch, yaw, roll);
    }

    /** The feet, before the torso: a step each to the gesture's stance, held, a step each back. */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        long now = System.nanoTime();
        for (Gesture gesture : ALL) {
            if (gesture.quiet(now)) continue;
            Play play = gesture.states.fresh(uuid);
            if (play == null || play.player == null) continue;
            Pose pose = play.pose;
            boolean apart = play.playing && pose.apart && pose.rightFoot != null && pose.leftFoot != null;
            float shown = gesture.shown(uuid, play);
            float held = apart ? shown : play.playing && gesture.isEnabled() && !play.stance.resting() ? 1 : shown;
            if (held < .05f && play.stance.resting()) continue;
            BraceSteps.apply(play.stance, play.player, play.frame, parts, apart ? pose.rightFoot : HOME,
                    apart ? pose.leftFoot : HOME, held, 0, LOGGER, gesture.id());
        }
    }

    /** After the runtime: a hand that goes to a point goes there from the shoulder as it is drawn; the roll and the lift put back. */
    public static void aimArms(UUID uuid, Function<String, ModelPart> parts) {
        long now = System.nanoTime();
        for (Gesture gesture : ALL) {
            if (gesture.quiet(now)) continue;
            Play play = gesture.states.fresh(uuid);
            if (play == null || !play.playing) continue;
            float shown = gesture.shown(uuid, play);
            if (shown < 1e-3f || play.phase >= play.pose.letGo) continue;
            Pose pose = play.pose;
            one(parts.apply("right_arm"), pose.right, pose.rightAt, pose.rightReach, pose.onBody, true, InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, gesture.id()));
            one(parts.apply("left_arm"), pose.left, pose.leftAt, pose.leftReach, pose.onBody, false, InteractionRuntime.weight(uuid, Effector.LEFT_ARM, gesture.id()));
        }
    }

    private static void one(ModelPart arm, float[] angles, Vector3f at, float reach, boolean onBody, boolean right, float weight) {
        if (arm == null || weight < 1e-3f) return;
        if (angles != null) {
            arm.zRot += (angles[2] - arm.zRot) * weight;
            arm.y += angles[3] * weight;
        } else if (at != null) {
            // A place on the body is aimed at as the torso has turned the arm already; a point in the
            // world, from the shoulder where it is drawn.
            if (onBody) return;
            float[] aim = arm(null, at, reach, new Vector3f(arm.x, arm.y, arm.z));
            if (aim == null) return;
            arm.xRot += strm.emfcompat.core.ik.IKMath.wrap(aim[0] - arm.xRot) * weight;
            arm.yRot += strm.emfcompat.core.ik.IKMath.wrap(aim[1] - arm.yRot) * weight;
        }
    }
}
