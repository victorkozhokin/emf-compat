package strm.emfcompat.animationadditions.lookat;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Look at a target: a player who has done nothing but stand or walk for a while - the camera left
 * alone - turns the head to the nearest creature in view, and back to where the camera looks the
 * moment they do anything else (turn the camera, swing, use an item, jump, crouch, change slot) or
 * the creature is out of view.
 *
 * <p>Only the head: vanilla and the packs already turn the body after the head once it is far
 * enough round, and that is left as it is - the head is kept within {@link #NECK_YAW} of the body
 * instead. The aim is worked out in the model's own space ({@link IKFrame}), like the ParCool and
 * Hackers 'n Slashers head looks: yaw from {@code atan2(-x, -z)}, pitch from {@code asin(y)}
 * (model forward is -z, down is +y).</p>
 */
public final class LookAt {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatLookAt");

    public static final String KEY_ENABLED = "lookat.enabled";

    /** Idle this long, seconds, before the head starts looking round. */
    private static final double IDLE_SECONDS = 3.0;
    /** A turn of the camera smaller than this, degrees, still counts as idle. */
    private static final float LOOK_JITTER = 0.5f;
    private static final double RANGE = 8.0;
    /** How far the head turns from the body at most. */
    private static final float NECK_YAW = (float) Math.toRadians(70);
    private static final float NECK_PITCH = (float) Math.toRadians(60);
    private static final double TURN_TO_SECONDS = 0.3;
    private static final double TURN_BACK_SECONDS = 0.1;
    private static final long SOLVE_EVERY_NANOS = 2_000_000L;
    private static final long STALE_NANOS = 200_000_000L;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private LookAt() {
    }

    private static final class State {
        float lastYaw = Float.NaN, lastPitch;
        int lastSlot = -1;
        boolean lastCrouching;
        long idleSince;
        int targetId = -1;
        float yaw, pitch, weight;
        long solvedAt, seenAt;
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Look at nearby creatures", true,
                "On", "Left idle for a while (walking is fine), the head turns to the nearest creature in view; do anything else and it looks where you look again.",
                "Off", "The head always follows the camera.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    /** Called with the model's space right before it is animated. */
    public static void modelPose(AbstractClientPlayer player, IKFrame frame) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        if (STATES.size() > 64) STATES.clear();
        State state = STATES.computeIfAbsent(uuid, k -> new State());
        state.seenAt = now;
        if (state.solvedAt != 0 && now - state.solvedAt < SOLVE_EVERY_NANOS) return;
        double dt = state.solvedAt == 0 ? 0 : Math.min(0.1, (now - state.solvedAt) / 1e9);
        state.solvedAt = now;

        // Idle: nothing done but walking for a while. The camera turning, a swing, an item in use,
        // a jump, a crouch or another hotbar slot all count as doing something.
        float yRot = player.getYRot(), xRot = player.getXRot();
        boolean looked = Float.isNaN(state.lastYaw)
                || Math.abs(yRot - state.lastYaw) > LOOK_JITTER || Math.abs(xRot - state.lastPitch) > LOOK_JITTER;
        int slot = player.getInventory().selected;
        boolean crouching = player.isCrouching();
        boolean acted = looked || player.swinging || player.isUsingItem() || !player.onGround()
                || slot != state.lastSlot || crouching != state.lastCrouching;
        state.lastYaw = yRot;
        state.lastPitch = xRot;
        state.lastSlot = slot;
        state.lastCrouching = crouching;
        if (acted || state.idleSince == 0) state.idleSince = now;
        boolean idle = (now - state.idleSince) / 1e9 >= IDLE_SECONDS;

        LivingEntity target = null;
        if (idle && isEnabled() && EMFCompatCore.isCompatEnabled() && !player.isSleeping()
                && !EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            target = pick(player, frame, state);
        }
        int id = target == null ? -1 : target.getId();
        if (id != state.targetId) {
            LOGGER.info("[LookAt] {} {}", player.getName().getString(),
                    target == null ? "camera" : "-> " + target.getName().getString());
            state.targetId = id;
        }

        if (target != null) {
            float[] aim = aim(frame, target);
            state.yaw = aim[0];
            state.pitch = aim[1];
        }
        double tau = target != null ? TURN_TO_SECONDS : TURN_BACK_SECONDS;
        float k = dt == 0 ? 0f : (float) (1 - Math.exp(-dt / tau));
        state.weight += ((target != null ? 1f : 0f) - state.weight) * k;
    }

    /** The nearest creature in view, keeping the current one while it still is. */
    private static LivingEntity pick(AbstractClientPlayer player, IKFrame frame, State state) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE))) {
            if (e == player || !e.isAlive() || e.isInvisible() || e.isSpectator()) continue;
            if (player.isPassengerOfSameVehicle(e) || e.hasPassenger(player) || player.hasPassenger(e)) continue;
            double distance = e.distanceTo(player);
            if (distance > RANGE) continue;
            float[] aim = aim(frame, e);
            if (Math.abs(aim[0]) > NECK_YAW || Math.abs(aim[1]) > NECK_PITCH) continue;
            if (!player.hasLineOfSight(e)) continue;
            if (e.getId() == state.targetId) return e;
            if (distance < bestDistance) {
                best = e;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** {yaw, pitch} of the head, model space, that points it at the target's eyes. */
    private static float[] aim(IKFrame frame, LivingEntity target) {
        Vec3 eyes = target.getEyePosition();
        Vector3f head = frame.relativeToJoint(eyes, new Vector3f(0, 0, 0));
        // Model pixels from the neck pivot; model forward is -z, down is +y.
        head.normalize();
        float yaw = (float) Math.atan2(-head.x, -head.z);
        float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, head.y)));
        return new float[]{yaw, pitch};
    }

    /** Blends the head towards the target, over whatever it was animated to. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.get(uuid);
        if (state == null || state.weight < 1e-3f || System.nanoTime() - state.seenAt > STALE_NANOS) return;
        ModelPart head = parts.apply("head");
        if (head == null) return;
        head.yRot += IKMath.wrap(state.yaw - head.yRot) * state.weight;
        head.xRot += IKMath.wrap(state.pitch - head.xRot) * state.weight;
    }
}
