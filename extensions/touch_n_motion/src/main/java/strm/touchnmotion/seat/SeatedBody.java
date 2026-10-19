package strm.touchnmotion.seat;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;
import strm.touchnmotion.interaction.Body;

import java.util.function.Function;

/**
 * What every seat that turns the body to what it faces does the same way - a craft's wheel, a train's
 * controls, a cart, a fence leant on: the head looking where the player does, the upper body turned
 * about the waist, the body swaying against the vehicle's pull.
 */
public final class SeatedBody {
    private static final String[] UPPER = {"body", "head", "hat", "right_arm", "left_arm"};
    /** The sway against a vehicle's acceleration: radians per block/s² and at most. */
    private static final float SWAY = .025f, MAX_SWAY = .14f;

    private SeatedBody() {
    }

    /** The head to {@code yaw}, {@code pitch} (radians, against the body), {@code weight} of the way; the hat with it when given one. */
    public static void turnHead(ModelPart head, ModelPart hat, float yaw, float pitch, float weight) {
        if (head == null) return;
        head.yRot += IKMath.wrap(yaw - head.yRot) * weight;
        head.xRot += IKMath.wrap(pitch - head.xRot) * weight;
        if (hat != null) {
            hat.yRot = head.yRot;
            hat.xRot = head.xRot;
        }
    }

    /** The torso, head and arms turned by {@code turn} about {@code waist}: moved round it, the torso and the arms turned with it. */
    public static void turnUpperBody(Function<String, ModelPart> parts, Quaternionf turn, Vector3f waist) {
        for (String name : UPPER) {
            ModelPart part = parts.apply(name);
            if (part == null) continue;
            Vector3f at = turn.transform(new Vector3f(part.x, part.y, part.z).sub(waist)).add(waist);
            part.setPos(at.x, at.y, at.z);
            if (!name.equals("head") && !name.equals("hat")) {
                Vector3f angles = new Quaternionf(turn).mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot)).getEulerAnglesZYX(new Vector3f());
                part.setRotation(angles.x, angles.y, angles.z);
            }
        }
    }

    /** The torso's sway {pitch, 0, roll} against a vehicle's horizontal acceleration (world, blocks/s²), in the body of {@code frame}: back speeding up, forwards braking, out of a bend. */
    public static Vector3f sway(IKFrame frame, Vector3d acceleration) {
        Vec3 origin = frame.jointWorld(new Vector3f());
        Vector3f force = Body.model(frame, origin.add(acceleration.x, 0, acceleration.z)).div(16);
        return new Vector3f(clamp(force.z * SWAY), 0, clamp(-force.x * SWAY));
    }

    private static float clamp(float v) {
        return Math.max(-MAX_SWAY, Math.min(MAX_SWAY, v));
    }
}
