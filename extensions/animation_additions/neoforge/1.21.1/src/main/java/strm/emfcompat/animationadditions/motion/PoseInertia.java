package strm.emfcompat.animationadditions.motion;

import strm.emfcompat.animationadditions.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKMath;

import java.util.UUID;
import java.util.function.Function;

/**
 * Inertia for the pack's own animation: when it cuts from one pose to another - a run into a
 * stand, one clip into the next - the limbs carry on from where they were and settle into the new
 * pose, instead of jumping.
 *
 * <p>Dead blending (after Daniel Holden): each part's pose is watched frame to frame. A change far
 * faster than any animation moves is a cut; the gap it opens is kept as an offset on top of the
 * new pose, with the speed the part had, and a critically damped spring takes the offset to
 * nothing. Out of a cut nothing is added, so the pack plays as it is. Works on whatever the pack
 * drew, before every other layer: the feet, the torso and the hands then correct the settled pose.</p>
 *
 * <p>Advances once per frame (EMF runs the hook for every draw of the player); every other draw
 * that frame, the armour's included, gets the same offsets.</p>
 */
public final class PoseInertia {

    public static final String KEY_ENABLED = "motion.inertia";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatMotion");

    private static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    /** Half-life of the settling, seconds, per part: the limbs quicker than the torso and head. */
    private static final double[] HALFLIFE = {0.10, 0.12, 0.09, 0.09, 0.07, 0.07};
    /**
     * A cut: a change in one frame that misses where the part was going by this much (radians),
     * at this speed (radians a second) or more. Fast animation - a sprint's legs - keeps going the
     * way it went and stays under it; a switch to another pose does not.
     */
    private static final float CUT_TURN = 0.3f;
    private static final float CUT_TURN_SPEED = 15f;
    /** The same for a pivot moved: pixels, pixels a second. */
    private static final float CUT_MOVE = 2f;
    private static final float CUT_MOVE_SPEED = 60f;
    /** The speed carried over a cut is at most this, radians or pixels a second: no wild overshoot. */
    private static final float MAX_CARRY_TURN = 6f;
    private static final float MAX_CARRY_MOVE = 20f;
    private static final double MAX_FRAME = 0.1;
    /** After crouching or standing up, seconds, nothing counts as a cut. */
    private static final double CROUCH_SECONDS = 0.3;
    private static final int CHANNELS = 6;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private PoseInertia() {
    }

    private static final class State {
        float frame = Float.NaN;
        long at, crouchedAt;
        boolean seen, crouching;
        /** Per part and channel (xRot, yRot, zRot, x, y, z): the pack's value last frame, its speed, the offset. */
        final float[][] last = new float[PARTS.length][CHANNELS];
        final float[][] speed = new float[PARTS.length][CHANNELS];
        final Spring[][] offset = new Spring[PARTS.length][CHANNELS];
        final boolean[] partSeen = new boolean[PARTS.length];

        State() {
            for (Spring[] row : offset) for (int c = 0; c < CHANNELS; c++) row[c] = new Spring();
        }
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Pose inertia", true,
                "On", "When the pack cuts from one pose to another, the limbs carry on and settle into it instead of jumping.",
                "Off", "Leave the pack's animation as it is.");
    }

    /** Called first after the pack has animated, on the model and on the armour. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts, float frame) {
        if (!EMFCompatConfig.getBoolean(KEY_ENABLED, true) || !EMFCompatCore.isCompatEnabled()
                || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        long now = System.nanoTime();
        State s = STATES.seen(uuid, now).value;
        boolean advance = frame != s.frame;
        double dt = s.seen ? Math.min(MAX_FRAME, (now - s.at) / 1e9) : 0;
        if (advance) {
            s.frame = frame;
            s.at = now;
            s.seen = true;
            Player player = Minecraft.getInstance().level == null ? null
                    : Minecraft.getInstance().level.getPlayerByUUID(uuid);
            boolean crouching = player != null && player.isCrouching();
            if (crouching != s.crouching) s.crouchedAt = now;
            s.crouching = crouching;
        }
        // Crouching and standing up the pack moves the head, the torso and the arms at once and on
        // purpose, some by their pivots, some by their turns: settling each on its own tears the
        // head off the torso, and crouching again and again piles the offsets up. No cuts then.
        boolean noCuts = (now - s.crouchedAt) / 1e9 < CROUCH_SECONDS;
        for (int i = 0; i < PARTS.length; i++) {
            ModelPart part = parts.apply(PARTS[i]);
            if (part == null) continue;
            float[] value = {part.xRot, part.yRot, part.zRot, part.x, part.y, part.z};
            if (advance) advance(s, i, value, dt, uuid, noCuts);
            Spring[] off = s.offset[i];
            part.xRot += off[0].value;
            part.yRot += off[1].value;
            part.zRot += off[2].value;
            part.x += off[3].value;
            part.y += off[4].value;
            part.z += off[5].value;
        }
    }

    private static void advance(State s, int i, float[] value, double dt, UUID uuid, boolean noCuts) {
        float[] last = s.last[i];
        float[] speed = s.speed[i];
        Spring[] off = s.offset[i];
        if (!s.partSeen[i] || dt <= 0) {
            System.arraycopy(value, 0, last, 0, CHANNELS);
            s.partSeen[i] = true;
            return;
        }
        for (int c = 0; c < CHANNELS; c++) {
            // The head's pitch and yaw are the look: they follow the camera as it is, never late.
            if (i == 0 && c < 2) continue;
            boolean turn = c < 3;
            float change = turn ? IKMath.wrap(value[c] - last[c]) : value[c] - last[c];
            float rate = (float) (change / dt);
            float miss = Math.abs(change - (float) (speed[c] * dt));
            boolean cut = turn
                    ? miss > CUT_TURN && Math.abs(rate) > CUT_TURN_SPEED
                    : miss > CUT_MOVE && Math.abs(rate) > CUT_MOVE_SPEED;
            if (cut && !noCuts) {
                // Keep drawing where it was, going the way it went; the spring then lets it go.
                float max = turn ? MAX_CARRY_TURN : MAX_CARRY_MOVE;
                off[c].value -= change;
                off[c].velocity = Math.max(-max, Math.min(max, off[c].velocity + speed[c]));
                if (DebugLog.trace()) {
                    LOGGER.info("[Inertia] {} {}[{}] cut {}", uuid, PARTS[i], c, String.format("%.2f", change));
                }
            } else {
                speed[c] = rate;
            }
            off[c].update(0f, HALFLIFE[i], dt);
            last[c] = value[c];
        }
    }
}
