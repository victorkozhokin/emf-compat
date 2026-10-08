package strm.touchnmotion.interaction;

import org.joml.Vector3f;

/** A palm on the same plane at straight-arm length, from the final shoulder. */
public final class PlaneContact {
    public static Vector3f fit(Vector3f shoulder, Vector3f target, Vector3f normal, float length) {
        Vector3f n = new Vector3f(normal).normalize();
        float distance = new Vector3f(target).sub(shoulder).dot(n);
        float radius2 = length * length - distance * distance;
        if (radius2 < 0) return null;
        Vector3f centre = new Vector3f(shoulder).fma(distance, n);
        Vector3f along = new Vector3f(target).sub(centre);
        if (along.lengthSquared() < 1e-8f) {
            along.set(0, 1, 0).fma(-n.y, n);
            if (along.lengthSquared() < 1e-8f) along.set(1, 0, 0).fma(-n.x, n);
        }
        return along.normalize().mul((float) Math.sqrt(radius2)).add(centre);
    }
    private PlaneContact() {}
}
