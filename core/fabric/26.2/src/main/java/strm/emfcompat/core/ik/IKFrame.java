package strm.emfcompat.core.ik;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The model/world transforms captured for one rendered entity evaluation. */
public record IKFrame(Matrix4f modelToWorld, Matrix4f worldToModel, Vec3 camera) {

    public static IKFrame capture(Matrix4f pose, Vec3 camera) {
        Matrix4f modelToWorld = new Matrix4f(pose);
        return new IKFrame(modelToWorld, new Matrix4f(modelToWorld).invert(), camera);
    }

    /** Converts a world-space point to model pixels relative to a joint pivot. */
    public Vector3f relativeToJoint(Vec3 worldPoint, Vector3f joint) {
        Vec3 target = worldPoint.subtract(camera);
        return worldToModel.transformPosition(new Vector3f(
                (float) target.x, (float) target.y, (float) target.z)).mul(16f).sub(joint);
    }

    /** Converts a model-space pivot in pixels to its world-space position. */
    public Vec3 jointWorld(Vector3f joint) {
        Vector3f blocks = modelToWorld.transformPosition(new Vector3f(joint).div(16f));
        return new Vec3(blocks.x, blocks.y, blocks.z).add(camera);
    }
}
