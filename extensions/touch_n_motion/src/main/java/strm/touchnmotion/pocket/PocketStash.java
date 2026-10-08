package strm.touchnmotion.pocket;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.interaction.Candidate;
import strm.touchnmotion.interaction.Category;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.InteractionContext;
import strm.touchnmotion.interaction.InteractionProvider;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.torso.BraceSteps;
import strm.touchnmotion.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import strm.touchnmotion.interaction.Body;

/**
 * Some time after the last of a run of pick-ups the player puts it all away: one look down at the
 * right hip, the right hand up and into the pocket, a short change of stance. The items are in the
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

    private static final float PITCH = (float) Math.toRadians(8), YAW = (float) Math.toRadians(17),
            ROLL = (float) Math.toRadians(10), TWIST = (float) Math.toRadians(4);
    /** The right foot out and back to open the hip, then the left a little forward under the weight. */
    private static final Vector3f RIGHT_FOOT = new Vector3f(-1f, 0, 1.2f), LEFT_FOOT = new Vector3f(.4f, 0, -.8f),
            HOME = new Vector3f();

    private static final float NECK = (float) Math.toRadians(70);

    private static boolean anyone;
    private static long lastPickup;

    private static final class State {
        final BraceSteps.State stance = new BraceSteps.State();
        AbstractClientPlayer player;
        IKFrame frame;
        boolean pending, playing;
        long pickedAt;
        float phase = 1;
    }

    public String id() {
        return "PocketStash";
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
        if (s.playing) return;
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
            s.phase = 0;
        }
        if (!s.playing) {
            context.decide("idle");
            return;
        }
        if (s.phase >= PocketMotion.LETS_GO) {
            context.decide("let go");
            return;
        }
        Map<Effector, float[]> aims = new EnumMap<>(Effector.class);
        float[] arm = PocketMotion.arm(s.phase);
        aims.put(Effector.RIGHT_ARM, new float[]{arm[0], arm[1]});
        // The head leaves from where it looks and comes back to it, whatever the camera does meanwhile.
        float[] head = PocketMotion.head(s.phase);
        float yaw = Body.headYaw(player);
        aims.put(Effector.HEAD, new float[]{(float) Math.toRadians(player.getXRot()) + head[0],
                Math.max(-NECK, Math.min(NECK, yaw + head[1]))});
        out.add(Candidate.of(id(), Category.PASSIVE, 20, 1f, TIMING, aims));
        context.decide(s.phase < PocketMotion.IN_POCKET ? "stash" : "return");
    }

    /** On firm ground with the right hand doing nothing else. */
    private static boolean ready(AbstractClientPlayer player) {
        return player.onGround() && !player.isPassenger() && !player.isSleeping() && !player.isSpectator()
                && !strm.touchnmotion.platform.Platform.inWater(player) && !player.isFallFlying() && !strm.touchnmotion.platform.Platform.swinging(player) && !player.isUsingItem()
                && InteractionRuntime.weight(player.getUUID(), Effector.RIGHT_ARM) < .05f;
    }

    private static float shown(UUID uuid, State s) {
        if (!s.playing || !INSTANCE.isEnabled()) return 0;
        return InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, INSTANCE.id());
    }

    public static TorsoLean.Hint torsoHint(UUID uuid) {
        if (quiet(System.nanoTime())) return null;
        State s = STATES.fresh(uuid);
        if (s == null) return null;
        float lean = PocketMotion.lean(s.phase) * shown(uuid, s);
        return lean < 1e-3f ? null : TorsoLean.Hint.turn(PITCH * lean, YAW * lean, ROLL * lean);
    }

    /**
     * The runtime aims an arm level; the sweep round the outside of the body is the roll put back, and
     * the dip into the pocket is the whole arm moved.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts) {
        if (quiet(System.nanoTime())) return;
        State s = STATES.fresh(uuid);
        if (s == null) return;
        float shown = shown(uuid, s);
        ModelPart arm = parts.apply(Effector.RIGHT_ARM.part);
        if (shown < 1e-3f || arm == null) return;
        float[] pose = PocketMotion.arm(s.phase);
        arm.zRot += (pose[2] - arm.zRot) * shown;
        arm.y += pose[3] * shown;
    }

    /** The feet, before the torso: one short step each, held through the gesture, then back. */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        if (quiet(System.nanoTime())) return;
        State s = STATES.fresh(uuid);
        if (s == null || s.player == null) return;
        // Back under the body the way they left it: a step each, not a slide - and to the end of
        // the gesture, whoever has the arm by then.
        boolean apart = PocketMotion.apart(s.phase);
        float shown = shown(uuid, s);
        float held = apart || !s.playing || !INSTANCE.isEnabled() ? shown : 1;
        if (held < .05f && s.stance.resting()) return;
        BraceSteps.apply(s.stance, s.player, s.frame, parts, apart ? RIGHT_FOOT : HOME, apart ? LEFT_FOOT : HOME,
                held, PocketMotion.lean(s.phase) * shown * TWIST, LOGGER, "PocketStance");
    }
}
