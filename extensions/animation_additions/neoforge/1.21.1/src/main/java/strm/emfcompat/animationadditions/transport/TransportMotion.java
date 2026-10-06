package strm.emfcompat.animationadditions.transport;

import org.joml.Vector3d;
import strm.emfcompat.animationadditions.interaction.Smoothing;

/** Tick samples of one fixed point on a craft, independent of walking or camera yaw. */
public final class TransportMotion {
    private Vector3d point, velocity = new Vector3d();
    public final Vector3d acceleration = new Vector3d();
    int tick = Integer.MIN_VALUE;
    public double speed;
    public boolean warped;
    public void sample(int tick, Vector3d point) {
        if (this.tick == tick) return;
        int elapsed = tick - this.tick;
        warped = this.point != null && this.point.distance(point) > 2;
        if (this.point == null || elapsed < 1 || elapsed > 5 || warped) {
            this.point = new Vector3d(point);
            velocity.zero();
            acceleration.zero();
            speed = 0;
        } else {
            double dt = elapsed / 20.0;
            Vector3d next = new Vector3d(point).sub(this.point).div(dt);
            Vector3d a = new Vector3d(next).sub(velocity).div(dt);
            if (a.length() > 12) a.normalize(12);
            acceleration.lerp(a, Smoothing.follow(dt, .18));
            speed = next.length();
            velocity.set(next);
            this.point.set(point);
        }
        this.tick = tick;
    }
}
