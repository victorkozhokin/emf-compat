package strm.touchnmotion.pocket;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.interaction.Candidate;
import strm.touchnmotion.interaction.Category;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.InteractionContext;
import strm.touchnmotion.interaction.InteractionProvider;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.torso.TorsoLean;
import strm.touchnmotion.pose.BodyPose;
import strm.touchnmotion.pose.PoseRunner;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Some time after the last of a run of pick-ups the player puts it all away: one look down at the
 * hip, the main hand up and into the pocket, a short change of stance - authored for the right hand,
 * mirrored for a left-handed player ({@link BodyPose#mirror}). The items are in the
 * inventory already - nothing is held back and nothing is drawn in the hand.
 */
public final class PocketStash implements InteractionProvider {
    public static final PocketStash INSTANCE = new PocketStash();
    public static final String KEY_ENABLED = "pocket.enabled";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatPocket");
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    /**
     * The runtime's release holds the last drawn pose and eases off it over some six times this;
     * right for a hand leaving a contact, far too long for an arm that is only on its way to rest.
     */
    private static final double RELEASE = .05;
    private static final Candidate.Timing TIMING = new Candidate.Timing(.32, RELEASE, .1);

    /** From the last pick-up of a run to the gesture. */
    private static final long WAIT_NANOS = 2_000_000_000L;
    /** A gesture that could not start for this long after its time is not shown at all. */
    private static final long GIVE_UP_NANOS = 8_000_000_000L;
    /** After the last pick-up by anyone: the longest a gesture and its feet can still be going. */
    private static final long BUSY_NANOS = WAIT_NANOS + GIVE_UP_NANOS + 4_000_000_000L;
    /** A gesture not carried on for this long - the body not drawn, in first person - is over: the next pick-up starts a new one. */
    private static final long STALE_NANOS = 1_000_000_000L;

    private static final float PITCH = (float) Math.toRadians(8), YAW = (float) Math.toRadians(17),
            ROLL = (float) Math.toRadians(10), TWIST = (float) Math.toRadians(4);
    /** The right foot out and back to open the hip, then the left a little forward under the weight. */
    private static final Vector3f RIGHT_FOOT = new Vector3f(-1f, 0, 1.2f), LEFT_FOOT = new Vector3f(.4f, 0, -.8f);

    private static boolean anyone;
    private static long lastPickup;

    private static final class State {
        /** What the stash asks of the body now, and its carrying out: the feet's steps among it. */
        final BodyPose pose = new BodyPose();
        final PoseRunner run = new PoseRunner();
        AbstractClientPlayer player;
        IKFrame frame;
        boolean pending, playing;
        /** Whether the hand going to the pocket is the right one: the main hand, as the gesture starts. */
        boolean right = true;
        long pickedAt, playedAt;
        float phase = 1;
    }

    public String id() {
        return "PocketStash";
    }

    @Override
    public String packState() {
        return "tnm_gesture";
    }

    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Pocket what was picked up", true,
                "On", "A moment after picking items up, the player glances down and tucks them away at the hip.",
                "Off", "Picking items up shows nothing.");
    }

    /** A real pick-up, as the server reports it; the next one of the run puts the gesture off again. */
    public static void picked(AbstractClientPlayer player) {
        long now = System.nanoTime();
        State s = STATES.seen(player.getUUID(), now).value;
        // What comes in while the hand is at the pocket goes in with the rest.
        // - unless the gesture has not gone on for a while: the body was not drawn, and it is over.
        if (s.playing && now - s.playedAt < STALE_NANOS) return;
        s.playing = false;
        s.pending = true;
        s.pickedAt = now;
        anyone = true;
        lastPickup = now;
    }

    private static boolean quiet(long now) {
        return !anyone || now - lastPickup > BUSY_NANOS;
    }

    public void collect(InteractionContext context, List<Candidate> out) {
        long now = context.now();
        if (quiet(now)) return;
        AbstractClientPlayer player = context.player();
        State s = STATES.seen(player.getUUID(), now).value;
        s.player = player;
        s.frame = context.frame();
        if (s.playing) {
            s.playedAt = now;
            s.phase += (float) (Math.min(.1, context.dt()) / PocketMotion.SECONDS);
            if (s.phase >= 1) {
                s.phase = 1;
                s.playing = false;
            }
        } else if (s.pending) {
            long since = now - s.pickedAt;
            if (since < WAIT_NANOS) {
                context.decide("wait");
                return;
            }
            if (since > WAIT_NANOS + GIVE_UP_NANOS) {
                s.pending = false;
                context.decide("dropped");
                return;
            }
            if (!ready(player)) {
                context.decide("defer");
                return;
            }
            s.pending = false;
            s.playing = true;
            s.playedAt = now;
            s.phase = 0;
            s.right = Body.right(player, true);
        }
        if (!s.playing) {
            context.decide("idle");
            return;
        }
        pose(s.pose, s.phase, s.right);
        if (s.phase >= PocketMotion.LETS_GO) {
            context.decide("let go");
            return;
        }
        Map<Effector, float[]> aims = PoseRunner.aims(s.pose, player);
        out.add(Candidate.of(id(), Category.PASSIVE, 20, 1f, TIMING, aims));
        context.decide(s.phase < PocketMotion.IN_POCKET ? "stash" : "return");
    }

    /** The stash as a pose at {@code phase}: the arm's one arc into the pocket, the glance down, the bend over it, the feet apart. */
    private static void pose(BodyPose pose, float phase, boolean right) {
        pose.reset();
        pose.right = PocketMotion.arm(phase);
        pose.head = PocketMotion.head(phase);
        float lean = PocketMotion.lean(phase);
        pose.pitch = PITCH * lean;
        pose.yaw = YAW * lean;
        pose.roll = ROLL * lean;
        pose.apart = PocketMotion.apart(phase);
        pose.rightFoot = RIGHT_FOOT;
        pose.leftFoot = LEFT_FOOT;
        pose.letGo = PocketMotion.LETS_GO;
        if (!right) pose.mirror();
    }

    /** On firm ground with the main hand doing nothing else. */
    private static boolean ready(AbstractClientPlayer player) {
        return player.onGround() && !player.isPassenger() && !player.isSleeping() && !player.isSpectator()
                && !strm.touchnmotion.platform.Platform.inWater(player) && !player.isFallFlying() && !strm.touchnmotion.platform.Platform.swinging(player) && !player.isUsingItem()
                && InteractionRuntime.weight(player.getUUID(), Body.right(player, true) ? Effector.RIGHT_ARM : Effector.LEFT_ARM) < .05f;
    }

    private static float shown(UUID uuid, State s) {
        if (!s.playing || !INSTANCE.isEnabled()) return 0;
        return InteractionRuntime.weight(uuid, s.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
    }

    public static TorsoLean.Hint torsoHint(UUID uuid) {
        if (quiet(System.nanoTime())) return null;
        State s = STATES.fresh(uuid);
        if (s == null) return null;
        float shown = shown(uuid, s);
        return PocketMotion.lean(s.phase) * shown < 1e-3f ? null
                : TorsoLean.Hint.turn(s.pose.pitch * shown, s.pose.yaw * shown, s.pose.roll * shown);
    }

    /**
     * The runtime aims an arm level; the sweep round the outside of the body is the roll put back, and
     * the dip into the pocket is the whole arm moved.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts, Map<Effector, float[]> base) {
        if (quiet(System.nanoTime())) return;
        State s = STATES.fresh(uuid);
        if (s == null || !s.playing) return;
        float shown = shown(uuid, s);
        PoseRunner.aimArms(s.pose, parts, base, s.right ? shown : 0f, s.right ? 0f : shown);
    }

    /** The feet, before the torso: one short step each, held through the gesture, then back. */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        if (quiet(System.nanoTime())) return;
        State s = STATES.fresh(uuid);
        if (s == null || s.player == null) return;
        // Back under the body the way they left it: a step each, not a slide - and to the end of
        // the gesture, whoever has the arm by then.
        float shown = shown(uuid, s);
        s.run.support(s.pose, s.player, s.frame, parts, s.playing, INSTANCE.isEnabled(), true, shown,
                PocketMotion.lean(s.phase) * shown * (s.right ? TWIST : -TWIST), LOGGER, "PocketStance");
    }
}
