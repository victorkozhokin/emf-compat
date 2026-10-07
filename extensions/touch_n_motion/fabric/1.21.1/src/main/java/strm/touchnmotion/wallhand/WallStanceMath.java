package strm.touchnmotion.wallhand;
import org.joml.Quaternionf;
import org.joml.Vector3f;
final class WallStanceMath {
    static Vector3f offset(Vector3f relative, float turn) {
        Vector3f delta = new Quaternionf().rotationY(turn * .55f).transform(new Vector3f(relative)).sub(relative);
        delta.y = 0;
        float length = delta.length();
        if (length > 1.6f) delta.mul(1.6f / length);
        return delta;
    }
    static Vector3f retreat(float amount) { return new Vector3f(0, 0, Math.max(0, Math.min(3.5f, amount))); }
}
