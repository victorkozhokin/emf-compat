package strm.touchnmotion.interaction;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;
import strm.emfcompat.core.ik.IKMath;

/**
 * Points an arm at a point from where its shoulder is drawn this frame - after the pack's
 * breathing and swing and the torso's lean have moved it. The same angles as {@code OneBoneIK}:
 * the arm hangs along +y.
 */
public final class ArmAim {

    private ArmAim() {
    }

    /** {xRot, yRot} that point a limb hanging along +y the way of {x, y, z}; {@code null} for no way at all. */
    public static float[] angles(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-3f) return null;
        return new float[]{-(float) Math.acos(Math.max(-1f, Math.min(1f, y / length))), (float) Math.atan2(-x, -z)};
    }

    /**
     * Turns {@code arm} towards {@code point} (model pixels) by {@code weight}, 0..1; with
     * {@code levelRoll} its roll goes to nothing by as much.
     */
    public static void towards(ModelPart arm, Vector3fc point, float weight, boolean levelRoll) {
        float[] aim = angles(point.x() - arm.x, point.y() - arm.y, point.z() - arm.z);
        if (aim == null) return;
        arm.xRot += IKMath.wrap(aim[0] - arm.xRot) * weight;
        arm.yRot += IKMath.wrap(aim[1] - arm.yRot) * weight;
        if (levelRoll) arm.zRot *= 1f - weight;
    }
}
