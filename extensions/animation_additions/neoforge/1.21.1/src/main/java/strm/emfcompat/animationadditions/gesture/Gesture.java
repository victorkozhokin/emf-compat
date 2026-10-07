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
import strm.emfcompat.animationadditions.interaction.Ease;

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
    private static final Candidate.Timing TIMING = new Candidate.Timing(.07, .05, .05);
    private static final long BUSY_NANOS = 14_000_000_000L;
    private static final float NECK = (float) Math.toRadians(70);
    private static final Vector3f HOME = new Vector3f();
    private static final float FIT = (float) Math.toRadians(20);

    /** Off by default: the game does what was clicked at once and the gesture goes with it. */
    public static final String KEY_ACT_AFTER = "gesture.actAfter";
    public static final String KEY_POISE = "gesture.poise", KEY_OTHERS = "gesture.others", KEY_FIT = "gesture.fit",
            KEY_STANCE = "gesture.stance", KEY_LOOK = "gesture.look", KEY_CALL_OFF = "gesture.callOff", KEY_FREE_ARM = "gesture.freeArm";

    protected static boolean on(String key) {
        return strm.emfcompat.core.EMFCompatConfig.getBoolean(key, true);
    }

    public static void register(strm.emfcompat.core.ConfigRegistry.Group config) {
        config.addBoolean(KEY_ACT_AFTER, "Act when the hand gets there", false,
                "On", "Feeding, milking, shearing, dressing a stand and planting happen when the hand reaches its place, a moment after the click - not before the hand has moved.",
                "Off", "The game acts on the click at once, as it always does; the hand finishes its way after.");
        config.addBoolean(KEY_POISE, "Hand out before the click", true,
                "On", "With the right thing in hand and the right thing under the crosshair, the hand is already held out; the click only finishes the move.",
                "Off", "A gesture starts at the click, from a hand hanging by the side.");
        config.addBoolean(KEY_OTHERS, "Other players' gestures", true,
                "On", "Feeding, milking, shearing, dressing a stand and planting also show on other players, told from where they look and what they do.",
                "Off", "These gestures show on your own player only.");
        config.addBoolean(KEY_FIT, "Lean in until the hand is there", true,
                "On", "The torso turns and the hips shift, a little, so the hand lands on what it reaches for.",
                "Off", "The arm only points at it; the body bends as the gesture says and no further.");
        config.addBoolean(KEY_STANCE, "Step into a stance", true,
                "On", "The feet step apart for a gesture, the weight goes over them, and they step back after.",
                "Off", "The feet stay where the pack has them.");
        config.addBoolean(KEY_LOOK, "Look at what the hands do", true,
                "On", "The head turns to the animal, the stand, the bed or the chest for as long as the hands are at it.",
                "Off", "The head stays with the camera.");
        config.addBoolean(KEY_CALL_OFF, "Give a gesture up when leaving", true,
                "On", "Turned away or walked off from what a gesture is done to, the hands come back to the body at once.",
                "Off", "A gesture plays to its end wherever the player goes.");
        config.addBoolean(KEY_FREE_ARM, "The other arm joins in", true,
                "On", "The arm with nothing to do waits half raised for its turn, or goes out against the lean.",
                "Off", "It hangs by the side until it is needed.");
    }

    public static boolean actsAfter() {
        return strm.emfcompat.core.EMFCompatConfig.getBoolean(KEY_ACT_AFTER, false);
    }

    private static boolean replaying;

    /** Whether the click now going through the game is one held back earlier, on its way at last. */
    public static boolean replaying() {
        return replaying;
    }

    /** Lets a held-back click through. */
    public static void replay(Runnable click) {
        replaying = true;
        try {
            click.run();
        } finally {
            replaying = false;
        }
    }

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
        /**
         * What the torso is fitted to, if not the hand that is further out: this arm's hand on this
         * point, this much, followed this slowly - for a gesture whose hands come and go while the
         * body should make one move of it.
         */
        public Vector3f fitAt;
        public boolean fitRight;
        public float fitWeight, fitSeconds;

        public void hand(boolean right, Vector3f at, float reach) {
            if (right) {
                rightAt = at;
                rightReach = reach;
            } else {
                leftAt = at;
                leftReach = reach;
            }
        }
        /** The arm with nothing to do, out from the side and back a little, as an arm goes when the body leans the other way. */
        public void free(boolean right, float amount) {
            if (!on(KEY_FREE_ARM)) return;
            float[] angles = {.42f * amount, 0, (right ? .2f : -.2f) * amount, 0};
            if (right) this.right = angles;
            else left = angles;
        }

        /** {pitch, yaw} added to where the head looks. */
        public float[] head;
        /** A point the eyes go to, and how much of the way from where they look, 0..1. */
        public Vector3f look;
        public float looking;
        public float pitch, yaw, roll;
        /** Grounded weight transfer in model pixels, before solving the hands. */
        public float weightSide, weightForward;
        /** {xRot, yRot, zRot} added to a leg after the feet are set: a leg lifted, a foot shaken. */
        public float[] rightLeg, leftLeg;
        /** Where the soles stand from where the pack has them, while {@link #apart}. */
        public Vector3f rightFoot, leftFoot;
        public boolean apart;
        /** From this phase on the arms and the head are the pack's again. */
        public float letGo = .9f;

        void reset() {
            right = left = head = rightLeg = leftLeg = null;
            rightAt = leftAt = rightFoot = leftFoot = look = fitAt = null;
            fitWeight = 0;
            fitSeconds = .14f;
            looking = 0;
            pitch = yaw = roll = weightSide = weightForward = 0;
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
        /**
         * For a gesture that is poised before the act ({@link Gesture#poises}): whether the player
         * is about to act, whether the act has come, how far out the hands are (0..1, eased by a
         * spring so it answers at once and never jumps), and how far through the work it is (0..1).
         */
        public boolean poised, acted, back;
        /** For another player: whether the arm is swinging, how far through, and whether a swing began this frame. */
        boolean swinging;
        int swingTime;
        public boolean swingBegan;
        /** The act itself, held back until the hand has got there (see {@link Gesture#defer}). */
        Runnable deferred;
        public float level, work;
        float speed;
        /** Where the hands are drawn to this frame: the pose's points, followed with a little give. */
        final Vector3f[] hand = {new Vector3f(), new Vector3f()}, handSpeed = {new Vector3f(), new Vector3f()};
        final boolean[] handSet = new boolean[2];
        /** The hands' points and the eyes' as last asked for at work, model pixels: what they come back from. */
        final Vector3f[] kept = new Vector3f[3];
        /** The fit of the torso and the pelvis that brings the hand on to its point, and the hand and point it was last made for. */
        final strm.emfcompat.animationadditions.torso.LowReach.State fit = new strm.emfcompat.animationadditions.torso.LowReach.State();
        final Vector3f fitAt = new Vector3f();
        boolean fitRight = true;
        boolean keeps;
    }

    private final EntityStates<Play> states = new EntityStates<>(Play::new);
    private long busyAt = System.nanoTime() - BUSY_NANOS;

    protected Gesture() {
        ALL.add(this);
    }

    /** How long the gesture takes, seconds. */
    protected double seconds(Play play) {
        return 1;
    }

    /**
     * A gesture that begins before the act: while this player is {@link #poised} to do it - the
     * right thing in the hand, the right thing under the crosshair - the hands are already part of
     * the way there, so when the click comes the work starts at once instead of after it. Its
     * {@link #pose} gets the work's progress as its phase and reads {@link Play#level}.
     */
    protected boolean poises() {
        return false;
    }

    /** Whether this player is about to act; sets the play's kind, point and hand while it has not acted yet. */
    protected boolean poised(InteractionContext context, Play play) {
        return false;
    }

    /**
     * For another player, whose clicks are not known: looked at every frame to tell that the act
     * has come. The arm beginning a swing while poised is the click, near enough; a gesture that
     * can see the act's result in the world goes by that instead.
     */
    protected void remote(InteractionContext context, Play play) {
        if (play.swingBegan && play.poised && !(play.acted && !play.back)) acted(play);
    }

    /** The act has come for a player who was poised to do it. */
    protected final void acted(Play play) {
        int entity = play.entity;
        boolean right = play.right;
        Play started = trigger(play.player, play.kind, play.point);
        started.entity = entity;
        started.right = right;
    }

    /** What this player has under the crosshair; for another player, as near as it can be told. */
    protected static net.minecraft.world.phys.HitResult sight(InteractionContext context) {
        return Sight.of(context.player(), context.now());
    }

    /** Once it has acted: whether the player has gone from what the gesture is done to, which calls it off. */
    protected boolean lost(Play play) {
        return false;
    }

    /** Whether a point in the world is out to a side of the body or behind it: too far round for a hand at it. */
    protected static boolean turnedFrom(Play play, Vec3 point) {
        Vector3f at = model(play, point);
        return Math.atan2(Math.abs(at.x), -at.z) > Math.toRadians(62);
    }

    /** How far out the hands wait, 0..1, and how long the work takes once the act has come, seconds. */
    protected float approach(Play play) {
        return .7f;
    }

    protected double work(Play play) {
        return 1;
    }

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

    protected int priority() {
        return 6;
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
        if (poises()) {
            // The act itself: the hands go the rest of the way and the work begins, whatever came before.
            if (!(play.playing && play.acted && !play.back && again(play, kind, point))) play.work = 0;
            play.playing = play.acted = true;
            play.back = play.pending = false;
            play.kind = kind;
            play.point = point;
            return play;
        }
        if (play.playing && !again(play, kind, point)) return play;
        if (!play.playing) {
            play.pending = true;
            play.at = now;
        }
        play.kind = kind;
        play.point = point;
        return play;
    }

    /**
     * The hand goes all the way first and {@code act} is done when it arrives - for the option that
     * makes the game wait for the gesture instead of the gesture following the game. Returns whether
     * it took the act on; while one waits, another is swallowed.
     */
    public final boolean defer(AbstractClientPlayer player, int kind, Vec3 point, Runnable act) {
        long now = System.nanoTime();
        Play play = states.seen(player.getUUID(), now).value;
        busyAt = now;
        if (play.deferred != null) return true;
        if (!ready(player)) return false;
        play.playing = play.acted = true;
        play.back = play.pending = false;
        play.work = 0;
        play.kind = kind;
        play.point = point;
        play.deferred = act;
        return true;
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

    /** The same, but out at once: fast off the mark and easing into place, for what must not lag behind its cause. */
    protected static float swell(float phase, float in, float out, float end) {
        float t = Ease.unit(phase / in), k = 1 - t;
        return (1 - k * k * k) * (1 - Ease.smooth((phase - out) / (end - out)));
    }

    /** 0 → 1 over {@code in}, held, 1 → 0 from {@code out} to {@code end}. */
    protected static float bell(float phase, float in, float out, float end) {
        return Ease.smooth(phase / in) * (1 - Ease.smooth((phase - out) / (end - out)));
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
        boolean mine = player == net.minecraft.client.Minecraft.getInstance().player;
        if (poises() && (mine || on(KEY_OTHERS))) {
            if (play == null) play = states.seen(player.getUUID(), now).value;
            play.player = player;
            play.frame = context.frame();
            play.poised = isEnabled() && ready(player) && poised(context, play);
            if (!mine) {
                play.swingBegan = player.swinging && (!play.swinging || player.swingTime < play.swingTime);
                play.swinging = player.swinging;
                play.swingTime = player.swingTime;
                // Told of the click itself (see ClientHands), there is nothing to guess from the swing.
                if (isEnabled() && !strm.emfcompat.animationadditions.net.Inputs.told(player)) remote(context, play);
            }
            if (play.poised && on(KEY_POISE)) {
                busyAt = now;
                if (!play.playing) {
                    play.playing = true;
                    play.acted = play.back = false;
                    play.work = 0;
                }
            }
        }
        if (quiet(now)) return;
        if (play == null) play = states.seen(player.getUUID(), now).value;
        play.player = player;
        play.frame = context.frame();
        play.shows = false;
        if (poises()) {
            if (play.playing) {
                busyAt = now;
                spring(play, (float) Math.min(.1, context.dt()));
            }
        } else if (play.playing) {
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
        pose(play, poises() ? play.work : play.phase, pose);
        float dt = (float) Math.min(.1, context.dt());
        if (poises()) {
            // Coming back, the hands leave from where they were by the body - not from the thing they
            // were at, which a player who has turned away from it has behind them by now.
            if (!play.back) {
                play.kept[0] = pose.rightAt == null ? null : new Vector3f(pose.rightAt);
                play.kept[1] = pose.leftAt == null ? null : new Vector3f(pose.leftAt);
                play.kept[2] = pose.look == null ? null : new Vector3f(pose.look);
                play.keeps = true;
            } else if (play.keeps) {
                pose.rightAt = play.kept[0] == null ? null : new Vector3f(play.kept[0]);
                pose.leftAt = play.kept[1] == null ? null : new Vector3f(play.kept[1]);
                pose.look = play.kept[2] == null ? null : new Vector3f(play.kept[2]);
            }
        }
        pose.rightAt = follow(play, 0, pose.rightAt, pose.rightReach, dt);
        pose.leftAt = follow(play, 1, pose.leftAt, pose.leftReach, dt);
        if (!on(KEY_LOOK)) pose.look = null;
        if (pose.look != null) {
            // The eyes go to it from where they look, and lead the hands there.
            float length = pose.look.length(), amount = Ease.unit(pose.looking);
            if (length > 1e-3f) {
                float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, pose.look.y / length)));
                float yaw = (float) Math.atan2(-pose.look.x, -pose.look.z);
                float nowPitch = (float) Math.toRadians(player.getXRot()), nowYaw = (float) Math.toRadians(Mth.wrapDegrees(player.yHeadRot - player.yBodyRot));
                float[] add = pose.head == null ? new float[2] : pose.head;
                pose.head = new float[]{add[0] + (pitch - nowPitch) * amount, add[1] + Mth.wrapDegrees((float) Math.toDegrees(yaw - nowYaw)) * (float) (Math.PI / 180) * amount};
            }
        }
        if (!poises() && play.phase >= pose.letGo) {
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
        // The click swings the arm; with the hands already on their way that swing is not shown over them.
        out.add(Candidate.of(id(), Category.USE, priority(), 1f, TIMING, aims).withQuietSwing(true));
        // What set it off was a click, and a click swings the arm: the gesture is what shows.
        context.claimArms();
        context.decide(play.phase < holdAt() ? "play" : "hold");
    }

    /**
     * The aim of an arm from {@code pivot} at a point, {@code reach} of the way there from hanging.
     * The way is taken in the arm's angles, not along a line for the hand: a hand brought down
     * from the head along a line passes the shoulder itself and the arm whips round it.
     */
    /**
     * One frame of a poised gesture: out to where it waits, all the way and through the work once
     * the act has come, back when the work is done or the player turned away. The level follows
     * its target as a critically damped spring - it starts moving the frame it is asked to, eases
     * into place, and a change of mind half way is one curve, not two joined.
     */
    private void spring(Play play, float dt) {
        float target;
        if (play.back) target = 0;
        else if (play.acted && on(KEY_CALL_OFF) && lost(play)) {
            // Run off from it: nothing is finished, the hands just come back.
            play.back = true;
            play.deferred = null;
            target = 0;
        } else if (play.acted) {
            target = 1;
            if (play.deferred != null) {
                // The act waits for the hand: it is done the moment the hand is there, and the work starts with it.
                if (play.level >= .93f) {
                    Runnable act = play.deferred;
                    play.deferred = null;
                    act.run();
                }
            } else {
                play.work = Math.min(1, play.work + dt / (float) Math.max(.05, work(play)));
                if (play.work >= 1) play.back = true;
            }
        } else if (play.poised) target = approach(play);
        else {
            play.back = true;
            target = 0;
        }
        // Out a touch short of critical - it arrives, carries a hair past and settles; back, exactly critical.
        float stiff = play.back ? 10f : 17f, damp = play.back ? 1f : .78f;
        int steps = Math.max(1, (int) Math.ceil(dt / .008f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            play.speed += (stiff * stiff * (target - play.level) - 2 * damp * stiff * play.speed) * h;
            play.level += play.speed * h;
        }
        play.level = Math.max(0, Math.min(1.1f, play.level));
        if (play.back && play.level < .015f && Math.abs(play.speed) < .25f) {
            play.playing = play.acted = play.back = play.keeps = false;
            play.level = play.speed = play.work = 0;
        }
    }

    /**
     * The point a hand is drawn to: the pose's, followed by a spring a touch short of critical, so
     * a hand at work swings through its turns instead of tracking them, overshoots a hair and
     * settles; and lifted in passing, so the way out and back is an arc and not a line.
     */
    private static Vector3f follow(Play play, int which, Vector3f at, float reach, float dt) {
        if (at == null) {
            play.handSet[which] = false;
            return null;
        }
        Vector3f now = play.hand[which], speed = play.handSpeed[which];
        if (!play.handSet[which]) {
            now.set(at);
            speed.zero();
            play.handSet[which] = true;
        } else {
            int steps = Math.max(1, (int) Math.ceil(dt / .008f));
            float h = dt / steps, stiff = 24f, damp = .8f;
            for (int i = 0; i < steps; i++) {
                speed.add(new Vector3f(at).sub(now).mul(stiff * stiff).sub(new Vector3f(speed).mul(2 * damp * stiff)).mul(h));
                now.add(new Vector3f(speed).mul(h));
            }
        }
        float way = Ease.unit(reach);
        return new Vector3f(now).sub(0, 1.8f * 4 * way * (1 - way), 0);
    }

    private static float[] arm(float[] angles, Vector3f at, float reach, Vector3f pivot) {
        if (angles != null) return new float[]{angles[0], angles[1]};
        if (at == null) return null;
        float[] aim = towards(at.x - pivot.x, at.y - pivot.y, at.z - pivot.z);
        if (aim == null) return null;
        // A hair past is let through: the arm carries on a little and comes back.
        reach = Math.max(0, Math.min(1.1f, reach));
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
        aim[1] *= Ease.smooth((float) Math.hypot(x, z) / 5f);
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
            boolean stance = on(KEY_STANCE);
            boolean apart = stance && play.playing && pose.apart && pose.rightFoot != null && pose.leftFoot != null;
            float shown = gesture.shown(uuid, play);
            float held = apart ? shown : play.playing && gesture.isEnabled() && !play.stance.resting() ? 1 : shown;
            if (!(held < .05f && play.stance.resting()))
                BraceSteps.apply(play.stance, play.player, play.frame, parts, apart ? pose.rightFoot : HOME,
                        apart ? pose.leftFoot : HOME, held, 0, LOGGER, gesture.id());
            if (!play.playing || shown < 1e-3f) continue;
            if (stance && play.player.onGround() && !play.player.isPassenger()
                    && play.player.getDeltaMovement().horizontalDistanceSqr() < .0004)
                strm.emfcompat.animationadditions.torso.PelvisFollow.shift(parts, pose.weightSide * shown, pose.weightForward * shown);
            leg(parts.apply("right_leg"), pose.rightLeg, shown);
            leg(parts.apply("left_leg"), pose.leftLeg, shown);
        }
    }

    private static void leg(ModelPart leg, float[] turn, float weight) {
        if (leg == null || turn == null) return;
        leg.xRot += turn[0] * weight;
        leg.yRot += turn[1] * weight;
        leg.zRot += turn[2] * weight;
    }

    /**
     * After the torso: an arm is as long as it is, so pointing it at a thing does not put the hand
     * on it - the torso turns about the hips and the pelvis shifts over the soles until the hand
     * that leads is on its point, the other hand's point weighed in when it is out as well.
     * Standing still only; a point on the player's own body needs none of it.
     */
    public static void reach(UUID uuid, Function<String, ModelPart> parts) {
        long now = System.nanoTime();
        for (Gesture gesture : ALL) {
            if (gesture.quiet(now)) continue;
            Play play = gesture.states.fresh(uuid);
            if (play == null || play.player == null) continue;
            Pose pose = play.pose;
            float weight = 0;
            Vector3f other = null;
            if (play.playing && play.shows && !pose.onBody && on(KEY_FIT) && play.player.onGround() && !play.player.isPassenger()
                    && play.player.getDeltaMovement().horizontalDistanceSqr() < .0004) {
                float r = pose.rightAt == null ? 0 : Math.min(1, pose.rightReach), l = pose.leftAt == null ? 0 : Math.min(1, pose.leftReach);
                // The hand further out leads; it gives the lead up only to one clearly further.
                if (play.fitRight ? l > r + .15f : r > l + .15f) play.fitRight = !play.fitRight;
                Vector3f at = play.fitRight ? pose.rightAt : pose.leftAt, second = play.fitRight ? pose.leftAt : pose.rightAt;
                if (pose.fitAt != null) {
                    play.fitRight = pose.fitRight;
                    play.fitAt.set(pose.fitAt);
                    weight = gesture.shown(uuid, play) * Ease.unit(pose.fitWeight);
                } else if (at != null) {
                    play.fitAt.set(at);
                    weight = gesture.shown(uuid, play) * Ease.smooth((Math.max(r, l) - .5f) / .5f);
                    if (second != null && Math.min(r, l) > .6f) other = second;
                }
            }
            if (weight < 1e-3f && !play.fit.active()) continue;
            // A moderate lean after the hand and no more: a thing further off is held out to, not lunged at.
            play.fit.angleLimit = FIT;
            play.fit.followSeconds = pose.fitSeconds;
            strm.emfcompat.animationadditions.torso.LowReach.apply(parts, play.fitRight, play.fitAt, other, weight, play.fit);
        }
    }

    /** After the runtime: a hand that goes to a point goes there from the shoulder as it is drawn; the roll and the lift put back. */
    public static void aimArms(UUID uuid, Function<String, ModelPart> parts, Map<Effector, float[]> base) {
        long now = System.nanoTime();
        for (Gesture gesture : ALL) {
            if (gesture.quiet(now)) continue;
            Play play = gesture.states.fresh(uuid);
            if (play == null || !play.playing) continue;
            float shown = gesture.shown(uuid, play);
            if (shown < 1e-3f || (!gesture.poises() && play.phase >= play.pose.letGo)) continue;
            Pose pose = play.pose;
            one(parts.apply("right_arm"), pose.right, contact(parts, pose.rightAt, pose.onBody), pose.rightReach, base.get(Effector.RIGHT_ARM), InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, gesture.id()));
            one(parts.apply("left_arm"), pose.left, contact(parts, pose.leftAt, pose.onBody), pose.leftReach, base.get(Effector.LEFT_ARM), InteractionRuntime.weight(uuid, Effector.LEFT_ARM, gesture.id()));
        }
    }

    private static Vector3f contact(Function<String, ModelPart> parts, Vector3f at, boolean onBody) {
        if (at == null || !onBody) return at;
        ModelPart body = parts.apply("body"), r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (body == null || r == null || l == null) return at;
        return GestureMath.bodyPoint(at, new Vector3f((r.x + l.x) * .5f, (r.y + l.y) * .5f, (r.z + l.z) * .5f),
                body.xRot, body.yRot, body.zRot);
    }

    private static void one(ModelPart arm, float[] angles, Vector3f at, float reach, float[] base, float weight) {
        if (arm == null || weight < 1e-3f) return;
        if (angles != null) {
            arm.zRot += (angles[2] - arm.zRot) * weight;
            arm.y += angles[3] * weight;
        } else if (at != null && base != null) {
            float[] aim = arm(null, at, reach, new Vector3f(arm.x, arm.y, arm.z));
            if (aim == null) return;
            // Replace the preliminary canonical-shoulder aim. Blend once from the post-torso base.
            arm.xRot = base[0] + strm.emfcompat.core.ik.IKMath.wrap(aim[0] - base[0]) * weight;
            arm.yRot = base[1] + strm.emfcompat.core.ik.IKMath.wrap(aim[1] - base[1]) * weight;
            arm.zRot = base[2] * (1 - weight);
        }
    }
}
