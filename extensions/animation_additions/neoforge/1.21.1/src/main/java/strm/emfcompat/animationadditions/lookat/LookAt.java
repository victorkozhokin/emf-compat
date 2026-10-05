package strm.emfcompat.animationadditions.lookat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;

import java.util.List;


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
 *
 * <p>An idle provider for the head: anything else that wants the head takes it. The idle timer and
 * the kept target are its own state, kept per player by the runtime.</p>
 */
public final class LookAt implements InteractionProvider {

    public static final LookAt INSTANCE = new LookAt();
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.3, 0.1, 0);

    public static final String KEY_ENABLED = "lookat.enabled";

    /** Idle this long, seconds, before the head starts looking round. */
    private static final double IDLE_SECONDS = 3.0;
    /** A turn of the camera smaller than this, degrees, still counts as idle. */
    private static final float LOOK_JITTER = 0.5f;
    private static final double RANGE = 8.0;
    /** Who to look at is chosen this often, not every frame: the creatures round the player and a line of sight to each. */
    private static final long PICK_EVERY_NANOS = 150_000_000L;
    /** How far the head turns from the body at most. */
    private static final float NECK_YAW = (float) Math.toRadians(70);
    private static final float NECK_PITCH = (float) Math.toRadians(60);
    private LookAt() {
    }

    private static final class State {
        float lastYaw = Float.NaN, lastPitch;
        int lastSlot = -1;
        boolean lastCrouching;
        long idleSince, pickedAt;
        int targetId = -1;
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Look at nearby creatures", true,
                "On", "Left idle for a while (walking is fine), the head turns to the nearest creature in view; do anything else and it looks where you look again.",
                "Off", "The head always follows the camera.");
    }

    @Override
    public String id() {
        return "LookAt";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        IKFrame frame = context.frame();
        long now = context.now();
        State state = context.data(State::new);

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
        if (!idle || player.isSleeping()) {
            state.pickedAt = 0;
        } else if (now - state.pickedAt >= PICK_EVERY_NANOS) {
            target = pick(player, frame, state);
            state.pickedAt = now;
        } else if (state.targetId != -1 && player.level().getEntity(state.targetId) instanceof LivingEntity kept && kept.isAlive()) {
            // Between two looks round the head stays on the one it has.
            target = kept;
        }
        state.targetId = target == null ? -1 : target.getId();
        if (target == null) {
            context.decide("camera");
            return;
        }
        float[] aim = aim(frame, target);
        // The head part takes {xRot, yRot}: pitch, then yaw.
        out.add(Candidate.single(id(), Category.IDLE, 10, 1f, TIMING, Effector.HEAD, new float[]{aim[1], aim[0]}));
        context.decide("-> " + target.getName().getString());
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
}
