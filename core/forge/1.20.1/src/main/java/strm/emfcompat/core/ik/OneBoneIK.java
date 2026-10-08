package strm.emfcompat.core.ik;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Closed-form aiming for a single rigid limb whose neutral direction is model-space +y. */
public final class OneBoneIK {
    private static final float EPSILON = 1e-3f;

    private OneBoneIK() {
    }

    /**
     * Aims a limb using x/y rotations. When the target is too far above the joint, the joint can
     * move upward by {@code maxLift} model pixels. {@code backSpread} moves targets behind the
     * model outward, avoiding an inside-out arm directly over the shoulder.
     */
    @Nullable
    public static IKResult solveXY(IKFrame frame, Vector3f joint, Vec3 target,
                                   float limbLength, float maxLift, float backSpread) {
        Vector3f relative = frame.relativeToJoint(target, joint);
        float distance = relative.length();
        if (distance < EPSILON) return null;

        float flat = (float) Math.sqrt(relative.x * relative.x + relative.z * relative.z);
        float behind = flat < EPSILON ? 0f : Math.max(0f, relative.z / flat);
        if (behind > 0f && backSpread != 0f) {
            relative.x += Math.signum(joint.x) * backSpread * behind;
            distance = relative.length();
        }
        float reach = distance / limbLength;

        float lift = 0f;
        float shortfall = 0f;
        float horizontal = (float) Math.sqrt(relative.x * relative.x + relative.z * relative.z);
        if (distance > limbLength && relative.y < 0f) {
            float vertical = (float) Math.sqrt(Math.max(0f,
                    limbLength * limbLength - horizontal * horizontal));
            float needed = -relative.y - vertical;
            lift = Math.min(maxLift, needed);
            shortfall = needed - lift;
            relative.y += lift;
            distance = relative.length();
        }
        relative.div(distance);

        float x = -(float) Math.acos(Math.max(-1f, Math.min(1f, relative.y)));
        float y = (float) Math.atan2(-relative.x, -relative.z);
        return new IKResult(x, y, reach, lift, shortfall);
    }
}
