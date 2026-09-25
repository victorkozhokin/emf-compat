package strm.emfcompat.animationadditions.footgrounding.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;

import java.util.UUID;
import java.util.function.Function;

/**
 * Foot IK for horses, donkeys and mules: the same idea as {@link FootGrounding}, on four legs.
 *
 * <p>Each hoof looks for its floor ({@link #drop}). The body is lowered onto the lowest one and,
 * when the front hooves stand higher or lower than the hind ones - stairs, a slab edge taken head
 * on - it is pitched round the middle of its footprint so both pairs reach their floors. What
 * that leaves, a hoof higher than the other one of its pair, its leg takes by sliding up into the
 * body (the legs are one bone).</p>
 *
 * <p>Vanilla horse geometry, model pixels: hips at y 14, x ±4, z 7 (hind) and -10 (front); the
 * hooves are 4x4 and reach y 24, their centres at x ±3, z 8 and -9.9. The renderer's own scale
 * (foals, the 1.1 of horses) is already on the pose stack.</p>
 */
public final class HorseFootGrounding {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatFootGrounding");

    /** Hoof centres at the ground, model pixels, and the parts they belong to. */
    private static final float GROUND = 24f;
    private static final float[][] HOOVES = {{-3f, 8f}, {3f, 8f}, {-3f, -9.9f}, {3f, -9.9f}};
    private static final String[][] LEGS = {
            {"right_hind_leg", "right_hind_baby_leg"}, {"left_hind_leg", "left_hind_baby_leg"},
            {"right_front_leg", "right_front_baby_leg"}, {"left_front_leg", "left_front_baby_leg"}};
    private static final float SPAN = 17.9f;
    private static final float MIDDLE_Z = -0.95f;
    private static final float[][] PROBES = {{0, 0}, {1.8f, 1.8f}, {-1.8f, 1.8f}, {1.8f, -1.8f}, {-1.8f, -1.8f}};
    private static final double RAY_UP = 0.1;
    private static final double RAY_DOWN = 1.2;

    private static final float MIN_STEP = 0.75f;
    /** Past this a hoof is over a drop, not a step. A horse steps up whole blocks. */
    private static final float MAX_STEP = 17f;
    private static final float MAX_LOWER = 17f;
    /** How far a leg slides up into the body at most; the leg is 10 pixels. */
    private static final float MAX_LIFT = 8f;
    private static final double MAX_TILT = Math.toRadians(11);

    /** Slow enough that hooves crossing edges at a run do not shake the body. */
    private static final double LOWER_SECONDS = 0.15;
    private static final double TILT_SECONDS = 0.25;
    private static final double LIFT_SECONDS = 0.03;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private HorseFootGrounding() {
    }

    private static final class State {
        float lower, tilt;
        final float[] lift = new float[4];
        /**
         * What the lowering and the pitch do to the horse, in the world, so the rider gets the
         * same: x -> pivot + turn * (x - pivot) + shift, the pivot kept relative to the horse.
         */
        final Vector3f pivot = new Vector3f(), shift = new Vector3f();
        final Matrix3f turn = new Matrix3f();
        boolean moved;
        String logged = "";
    }

    public static boolean handles(Object entity) {
        return entity instanceof AbstractHorse && !(entity instanceof Llama) && !(entity instanceof Camel);
    }

    /** Called with the pose stack right before the model is animated; lowers and pitches it. */
    public static void modelPose(AbstractHorse horse, PoseStack stack, float partialTick) {
        UUID uuid = horse.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        State state = entry.value;
        double dt = EntityStates.due(entry, now);
        if (dt >= 0) solve(horse, stack, state, dt);

        state.moved = Math.abs(state.lower) > 1e-3f || Math.abs(state.tilt) > 1e-4f;
        if (state.moved) worldMove(horse, stack, state, partialTick);

        if (Math.abs(state.lower) > 1e-3f) stack.translate(0f, state.lower / 16f, 0f);
        if (Math.abs(state.tilt) > 1e-4f) {
            // Round the middle of the footprint, on the ground: front up is -y at -z.
            stack.translate(0f, GROUND / 16f, MIDDLE_Z / 16f);
            stack.mulPose(Axis.XP.rotation(-state.tilt));
            stack.translate(0f, -GROUND / 16f, -MIDDLE_Z / 16f);
        }
    }

    /** The model's lowering and pitch carried into the world: M * T * M^-1. */
    private static void worldMove(AbstractHorse horse, PoseStack stack, State state, float partialTick) {
        Matrix4f toWorld = stack.last().pose();
        Matrix3f linear = new Matrix3f();
        toWorld.get3x3(linear);
        Matrix3f back = new Matrix3f(linear).invert();
        // Model pixels -> model units: the pivot at the ground, in the middle of the footprint.
        Vector3f pivotModel = new Vector3f(0f, GROUND / 16f, MIDDLE_Z / 16f);
        Vector3f pivotWorld = toWorld.transformPosition(new Vector3f(pivotModel));
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 at = horse.getPosition(partialTick);
        state.pivot.set((float) (pivotWorld.x + camera.x - at.x), (float) (pivotWorld.y + camera.y - at.y),
                (float) (pivotWorld.z + camera.z - at.z));
        state.turn.set(linear).mul(new Matrix3f().rotationX(-state.tilt)).mul(back);
        state.shift.set(0f, state.lower / 16f, 0f);
        linear.transform(state.shift);
    }

    private static void solve(AbstractHorse horse, PoseStack stack, State state, double dt) {
        float targetLower = 0f, targetTilt = 0f;
        float[] targetLift = new float[4];
        float[] drops = new float[4];
        String decided;

        String why = ineligible(horse);
        IKFrame frame = null;
        if (why != null) {
            decided = "off:" + why;
        } else {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            frame = IKFrame.capture(stack.last().pose(), camera);
            float low = 0f;
            for (int i = 0; i < 4; i++) {
                drops[i] = drop(horse, frame, HOOVES[i]);
                if (drops[i] <= MAX_STEP) low = Math.max(low, drops[i]);
            }
            if (low >= MIN_STEP) {
                low = Math.min(low, MAX_LOWER);
                // How far each hoof must rise against the lowered body.
                float[] plant = new float[4];
                for (int i = 0; i < 4; i++) plant[i] = Math.max(0f, Math.min(MAX_STEP, low - drops[i]));
                float hind = Math.min(plant[0], plant[1]);
                float front = Math.min(plant[2], plant[3]);
                // The pitch lifts one pair and drops the other by half; the body goes up so that
                // neither pair floats. A pitch held back by MAX_TILT leaves the rest to the legs.
                float tilt = (float) Math.max(-MAX_TILT, Math.min(MAX_TILT, Math.atan2(front - hind, SPAN)));
                float half = (float) Math.sin(tilt) * SPAN / 2f;
                float up = Math.min(front - half, hind + half);
                targetTilt = tilt;
                targetLower = low - up;
                targetLift[0] = plant[0] - (up - half);
                targetLift[1] = plant[1] - (up - half);
                targetLift[2] = plant[2] - (up + half);
                targetLift[3] = plant[3] - (up + half);
                for (int i = 0; i < 4; i++) targetLift[i] = Math.max(0f, Math.min(MAX_LIFT, targetLift[i]));
                decided = Math.abs(tilt) > 0.02f ? "tilted" : "lowered";
            } else {
                decided = "flat";
            }
        }

        float kLower = Smoothing.snapFirst(dt, LOWER_SECONDS);
        state.lower += (targetLower - state.lower) * kLower;
        float kTilt = Smoothing.snapFirst(dt, TILT_SECONDS);
        state.tilt += (targetTilt - state.tilt) * kTilt;
        float kLift = Smoothing.snapFirst(dt, LIFT_SECONDS);
        for (int i = 0; i < 4; i++) state.lift[i] += (targetLift[i] - state.lift[i]) * kLift;

        if (!decided.equals(state.logged)) {
            LOGGER.info("[HorseFootGrounding] {} {} (RH={} LH={} RF={} LF={} lower={} tilt={}) y={} body={}",
                    horse.getName().getString(), decided,
                    String.format("%.2f", drops[0]), String.format("%.2f", drops[1]),
                    String.format("%.2f", drops[2]), String.format("%.2f", drops[3]),
                    String.format("%.2f", targetLower), String.format("%.1f", Math.toDegrees(targetTilt)),
                    String.format("%.3f", horse.getY()), String.format("%.1f", horse.yBodyRot));
            state.logged = decided;
        }
    }

    private static String ineligible(AbstractHorse horse) {
        if (!FootGroundingFeature.isEnabled() || !FootGroundingFeature.isHorsesEnabled()
                || !EMFCompatCore.isCompatEnabled()) return "disabled";
        // Not onGround(): a horse with no AI never sets it on the client. Something right under
        // the hitbox is enough.
        if (horse.level().noCollision(horse, horse.getBoundingBox().move(0, -0.08, 0))) return "airborne";
        if (horse.isInWaterOrBubble() || horse.isStanding() || horse.isPassenger()) return "state";
        return null;
    }

    /** How far below the hoof its floor is, model pixels; the ray length if there is none. */
    private static float drop(AbstractHorse horse, IKFrame frame, float[] hoof) {
        Vector3f base = new Vector3f(hoof[0], GROUND, hoof[1]);
        Vec3 best = null;
        for (float[] probe : PROBES) {
            Vec3 foot = frame.jointWorld(new Vector3f(base).add(probe[0], 0f, probe[1]));
            BlockHitResult hit = horse.level().clip(new ClipContext(
                    foot.add(0, RAY_UP, 0), foot.add(0, -RAY_DOWN, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, horse));
            if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) continue;
            if (best == null || hit.getLocation().y > best.y) best = hit.getLocation();
        }
        if (best == null) return (float) (RAY_DOWN * 16);
        Vec3 centre = frame.jointWorld(base);
        return frame.relativeToJoint(new Vec3(centre.x, best.y, centre.z), new Vector3f(0f, GROUND, 0f)).y;
    }

    /** Slides each raised leg up into the body, on top of its animation. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        for (int i = 0; i < 4; i++) {
            if (state.lift[i] < 0.05f) continue;
            for (String name : LEGS[i]) {
                ModelPart leg = parts.apply(name);
                if (leg != null) leg.y -= state.lift[i];
            }
        }
    }

    /**
     * Moves and turns the rider with the horse: the pose stack is at the rider, world-aligned,
     * right before the rider is drawn. Returns false when the horse is not moved.
     */
    public static boolean moveRider(AbstractHorse horse, net.minecraft.world.entity.Entity rider,
                                    PoseStack stack, float partialTick) {
        State state = STATES.fresh(horse.getUUID());
        if (state == null || !state.moved) return false;
        Vec3 offset = horse.getPosition(partialTick).subtract(rider.getPosition(partialTick));
        Vector3f pivot = new Vector3f(state.pivot).add((float) offset.x, (float) offset.y, (float) offset.z);
        stack.translate(pivot.x + state.shift.x, pivot.y + state.shift.y, pivot.z + state.shift.z);
        stack.mulPose(new Matrix4f().set(state.turn));
        stack.translate(-pivot.x, -pivot.y, -pivot.z);
        return true;
    }
}
