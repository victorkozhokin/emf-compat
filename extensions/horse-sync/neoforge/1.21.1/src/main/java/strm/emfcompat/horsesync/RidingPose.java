package strm.emfcompat.horsesync;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Our own horse-riding pose for the player: a forward-leaning "jockey" seat — the whole upper body
 * (head, torso, arms) tips forward over the horse's neck as one unit while the legs grip the sides,
 * with a small looping bob driven by the horse's gait (still when it stands, bobs when it moves).
 *
 * <p>The {@code crouch} parameter tilts the upper body forward <em>about the hip</em> as a rigid
 * body — each of the head/body/arms is rotated and repositioned so they stay joined at the seat
 * (rotating a single part about its own neck pivot would detach it from the hips). The legs are
 * left out of the crouch so they keep hugging the barrel.</p>
 *
 * <p>Every value lives in {@link #PARAMS} and can be tuned live with {@code /hsride}
 * ({@link RidingPoseCommand}); {@link #apply} reads them each frame. Axis notes: negative
 * {@code xRot} = forward for arms; body/head pivot at the neck so positive {@code crouch}/
 * {@code bodyLean} lean forward; legs are spread by position, not rotation.</p>
 */
public final class RidingPose {

    /** Model-space Y of the hip / seat that the upper body pivots around. */
    private static final float HIP_Y = 12.0f;
    private static final float ARM_BASE_X = 5.0f;
    private static final float ARM_BASE_Y = 2.0f;

    private static final Map<String, Float> PARAMS = new LinkedHashMap<>();
    private static final Map<String, Float> DEFAULTS;

    static {
        // Legs: grip the sides (spread by position + roll), hanging straight down.
        PARAMS.put("legPitch", 0.0f);
        PARAMS.put("legBaseX", 1.9f);
        PARAMS.put("legSpreadPos", 1.0f);
        PARAMS.put("legRoll", -0.50f);
        // Arms: forward and inward on the reins.
        PARAMS.put("armPitch", -0.85f);
        PARAMS.put("armYaw", 0.12f);
        PARAMS.put("armRoll", -0.10f);
        // Torso-only micro-lean (on top of the crouch).
        PARAMS.put("bodyLean", 0.10f);
        // Whole-upper-body forward lean about the hip (the gallop seat).
        PARAMS.put("crouch", 0.30f);
        // Gait-driven bob.
        PARAMS.put("gaitFreq", 0.70f);
        PARAMS.put("moveGain", 2.0f);
        PARAMS.put("legBob", 0.15f);
        PARAMS.put("armBob", 0.08f);
        PARAMS.put("bodyBob", 0.04f);

        DEFAULTS = Map.copyOf(PARAMS);
    }

    private RidingPose() {
    }

    private static float p(String key) {
        Float v = PARAMS.get(key);
        return v == null ? 0.0f : v;
    }

    public static boolean set(String key, float value) {
        if (!PARAMS.containsKey(key)) return false;
        PARAMS.put(key, value);
        return true;
    }

    public static Set<String> keys() {
        return Collections.unmodifiableSet(PARAMS.keySet());
    }

    public static Map<String, Float> snapshot() {
        return new LinkedHashMap<>(PARAMS);
    }

    public static void reset() {
        PARAMS.clear();
        PARAMS.putAll(DEFAULTS);
    }

    /** The head is only captured/posed (losing its idle look-sway) while the crouch is active. */
    public static boolean capturesHead() {
        return p("crouch") != 0.0f;
    }

    /**
     * Writes the riding pose onto the model parts.
     *
     * @param horseLimbSwing  the horse's accumulated limb-swing position (drives the bob phase)
     * @param horseLimbSpeed  the horse's limb-swing speed (0 when standing still)
     * @param upperBody       when {@code false}, only the leg seat is posed and the arms/torso/head
     *                        are left for an active action pose (gun aim, melee swing) to control
     */
    public static void apply(PlayerModel<?> model, float horseLimbSwing, float horseLimbSpeed, boolean upperBody) {
        float move = Mth.clamp(horseLimbSpeed * p("moveGain"), 0.0f, 1.0f);
        float bob = Mth.sin(horseLimbSwing * p("gaitFreq")) * move;

        // Legs grip the sides — always posed, even while an action controls the upper body.
        float legX = p("legBaseX") + p("legSpreadPos");
        float legPitch = p("legPitch") + bob * p("legBob");
        float legRoll = p("legRoll");

        ModelPart rightLeg = model.rightLeg;
        rightLeg.x = -legX;
        rightLeg.xRot = legPitch;
        rightLeg.yRot = 0.0f;
        rightLeg.zRot = -legRoll;

        ModelPart leftLeg = model.leftLeg;
        leftLeg.x = legX;
        leftLeg.xRot = legPitch;
        leftLeg.yRot = 0.0f;
        leftLeg.zRot = legRoll;

        // Upper body (arms/torso/head/crouch) is skipped while an action pose owns the arms.
        if (!upperBody) {
            return;
        }

        // Arm base rotation (reins). Crouch adds to this below.
        float armPitch = p("armPitch") + bob * p("armBob");
        float armYaw = p("armYaw");
        float armRoll = p("armRoll");

        ModelPart rightArm = model.rightArm;
        rightArm.xRot = armPitch;
        rightArm.yRot = armYaw;
        rightArm.zRot = armRoll;

        ModelPart leftArm = model.leftArm;
        leftArm.xRot = armPitch;
        leftArm.yRot = -armYaw;
        leftArm.zRot = -armRoll;

        // Torso base rotation (micro-lean). Crouch adds to this below.
        model.body.xRot = p("bodyLean") + bob * p("bodyBob");
        model.body.yRot = 0.0f;
        model.body.zRot = 0.0f;

        // Gallop crouch: tilt head + body + arms forward about the hip, as one rigid unit, so they
        // stay joined at the seat. The head keeps its look rotation (we only add to it).
        float crouch = p("crouch");
        if (crouch != 0.0f) {
            leanAboutHip(model.body, 0.0f, 0.0f, crouch);
            leanAboutHip(model.head, 0.0f, 0.0f, crouch);
            leanAboutHip(rightArm, -ARM_BASE_X, ARM_BASE_Y, crouch);
            leanAboutHip(leftArm, ARM_BASE_X, ARM_BASE_Y, crouch);
        }
    }

    /**
     * Rotates {@code part} forward by {@code a} radians about the hip point {@code (0, HIP_Y, 0)},
     * repositioning its pivot so the upper body tilts as a rigid unit instead of detaching. The
     * part's own {@code xRot} is added to (not overwritten), preserving e.g. head look-tracking.
     *
     * @param baseX the part's default pivot X offset
     * @param baseY the part's default pivot Y offset
     */
    private static void leanAboutHip(ModelPart part, float baseX, float baseY, float a) {
        float cos = Mth.cos(a);
        float sin = Mth.sin(a);
        float dy = baseY - HIP_Y;
        part.x = baseX;
        part.y = HIP_Y + dy * cos;
        part.z = dy * sin;
        part.xRot += a;
    }
}
