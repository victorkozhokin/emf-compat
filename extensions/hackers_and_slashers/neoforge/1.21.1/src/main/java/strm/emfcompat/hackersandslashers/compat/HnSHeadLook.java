package strm.emfcompat.hackersandslashers.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the head on the camera while Hackers 'n Slashers swings.
 *
 * <p>The attack animations twist the torso, and the animation library applies the torso to the
 * whole pose stack before the model is animated, so everything above the hips turns with it. The
 * animations also key the head, relative to that twisted body, so the head ends up looking wherever
 * the swing points the chest - away from where the player is actually looking.</p>
 *
 * <p>So the head is aimed from the look vector instead: the pose stack as it stands right before
 * the model is animated is the model's own space, twist included; the look vector is carried into
 * it and turned into the head's yaw and pitch. The same approach the ParCool addon uses for the
 * head in a hang.</p>
 */
public final class HnSHeadLook {

    /** How far the neck turns from the chest, degrees. Past this the head stops, as a neck would. */
    private static final float NECK_YAW = 80f;
    private static final float NECK_PITCH = 70f;
    private static final float NECK_ROLL = 45f;

    /** Each player's model space as last set up for drawing. Render thread only. */
    private static final Map<UUID, IKFrame> FRAMES = new HashMap<>();

    private HnSHeadLook() {
    }

    /** Called with the pose stack as it stands right before the model is animated. */
    public static void modelPose(AbstractClientPlayer player, Matrix4f pose) {
        if (FRAMES.size() > 64) FRAMES.clear();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        FRAMES.put(player.getUUID(), IKFrame.capture(pose, camera));
    }

    /**
     * The head's {x, y, z} rotation in radians that points it along the player's look and keeps it
     * level with the world, in this frame's model space; {@code null} before the first frame.
     *
     * <p>Aim first: yaw and pitch from the look vector, each within what a neck turns. Then level:
     * the swing tilts the torso too, and the head inherited that roll. The roll is taken about the
     * head's own line of sight, so limiting it can never move where the head looks - limiting a
     * roll about the model's axis did, since that turns the aim with it. The whole rotation is then
     * taken apart in the order a {@code ModelPart} applies it (z, then y, then x).</p>
     */
    public static float[] headRotation(AbstractClientPlayer player) {
        IKFrame frame = FRAMES.get(player.getUUID());
        if (frame == null) return null;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 look = player.getViewVector(partial);
        // Model space: forward is -z, down is +y. A head turned by y then x looks at
        // (-sin y cos x, sin x, -cos y cos x).
        Vector3f d = frame.worldToModel()
                .transformDirection(new Vector3f((float) look.x, (float) look.y, (float) look.z)).normalize();
        float yaw = clamp((float) Math.atan2(-d.x, -d.z), NECK_YAW);
        float pitch = clamp((float) Math.asin(Math.max(-1f, Math.min(1f, d.y))), NECK_PITCH);
        Matrix3f aim = new Matrix3f().rotationZYX(0f, yaw, pitch);

        Vector3f back = aim.transform(new Vector3f(0f, 0f, 1f));
        Vector3f headDown = aim.transform(new Vector3f(0f, 1f, 0f));
        Vector3f worldDown = frame.worldToModel().transformDirection(new Vector3f(0f, -1f, 0f)).normalize();
        Vector3f level = worldDown.fma(-worldDown.dot(back), back);
        float roll = 0f;
        if (level.lengthSquared() > 1e-6f) {
            level.normalize();
            // Signed angle from the head's down to the world's, about the line of sight.
            roll = clamp((float) Math.atan2(new Vector3f(headDown).cross(level).dot(back), headDown.dot(level)),
                    NECK_ROLL);
        }

        Vector3f angles = aim.rotateZ(roll).getEulerAnglesZYX(new Vector3f());
        return new float[]{angles.x, angles.y, angles.z};
    }

    private static float clamp(float radians, float limitDegrees) {
        float limit = (float) Math.toRadians(limitDegrees);
        return Math.max(-limit, Math.min(limit, radians));
    }
}
