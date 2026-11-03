package strm.touchnmotion.ride;

import strm.emfcompat.core.ik.Skeleton;
import net.minecraft.world.phys.Vec3;

/**
 * A rider's body on its seat as a weight on a spring, for a cart, a horse - anything carried. The
 * vehicle's own change of speed, tick by tick, shoves the seat; the body is left behind and drawn
 * back, overshooting as much as the damping lets it. So it goes back as the vehicle sets off,
 * forward as it stops, out of a bend, down as it lands and up as it springs.
 *
 * <p>The shove is read from where the vehicle was and is, not from the speed it claims: on a slope,
 * off a drop and onto the ground that is what is felt. A client moves it in catches, towards where
 * the server last said it was, so its speed is evened over a few ticks before its change is
 * taken.</p>
 */
public final class SeatSpring {
    /** How fast the body comes back, a second; how soon it settles, 1 with no overshoot. */
    private final double stiff, damp;
    /** Blocks a second a second: a harder shove is no harder on the body; the share of a tick's speed taken in. */
    private final double hardest, even;

    private int tick = -1;
    private boolean moving;
    private Vec3 at = Vec3.ZERO;
    /** Blocks a second, evened out; blocks a second as this tick went, not evened; blocks a second a second. */
    public Vec3 speed = Vec3.ZERO, went = Vec3.ZERO, shove = Vec3.ZERO;
    /** The body against its seat, blocks and blocks a second, along the axes the shove was given in. */
    public final double[] off = new double[3], rate = new double[3];

    public SeatSpring(double stiff, double damp, double hardest, double even) {
        this.stiff = stiff;
        this.damp = damp;
        this.hardest = hardest;
        this.even = even;
    }

    /** The vehicle where it is now, at its {@code tickCount}: a new tick's shove is taken from it. */
    public void sample(Vec3 now, int tickCount) {
        if (tickCount == tick) return;
        // A frame may span more than one tick.
        int ticks = tickCount - tick;
        boolean next = tick >= 0 && ticks >= 1 && ticks <= 5 && now.distanceToSqr(at) < 9.0 * ticks * ticks;
        Vec3 raw = next ? now.subtract(at).scale(20.0 / ticks) : Vec3.ZERO;
        went = raw;
        Vec3 evened = next && moving ? speed.lerp(raw, 1.0 - Math.pow(1.0 - even, ticks)) : raw;
        shove = next && moving ? evened.subtract(speed).scale(20.0 / ticks) : Vec3.ZERO;
        moving = next;
        double hard = shove.length();
        if (hard > hardest) shove = shove.scale(hardest / hard);
        speed = evened;
        at = now;
        tick = tickCount;
    }

    /**
     * The body left behind by {@code push} - the shove along the body's own three axes - and drawn back
     * to its seat, over {@code dt} seconds in steps short enough for the spring.
     */
    public void settle(double[] push, double dt) {
        for (double left = Math.min(dt, 0.1); left > 1e-6; left -= 1.0 / 240) {
            double step = Math.min(left, 1.0 / 240);
            for (int i = 0; i < 3; i++) {
                rate[i] += (-push[i] - stiff * stiff * off[i] - 2 * damp * stiff * rate[i]) * step;
                off[i] += rate[i] * step;
            }
        }
    }

    /**
     * How a seat's spring shows on the body: radians of lean a block left behind ({@code give}) and driven
     * down ({@code bow}); the most back, forward, bowed and to a side; pixels risen a block up, and the most.
     */
    public record Shape(float give, float bow, float back, float forward, float bowLimit, float side, float rise, float riseLimit) {
    }

    /** The body on its seat now: leant forward and to its left, radians, how fast each, and risen off it, pixels. */
    public record Lean(float pitch, float pitchRate, float roll, float rollRate, float rise) {
    }

    /** The spring as the body shows it: left behind is a lean back (-xRot), pushed right a lean left (-zRot), driven down a bow forward. */
    public Lean lean(Shape shape) {
        float give = (float) off[0] * shape.give(), down = (float) Math.max(0.0, -off[2]);
        float pitch = soft(give, give < 0 ? shape.back() : shape.forward()) + soft(down * shape.bow(), shape.bowLimit());
        float pitchRate = (float) rate[0] * shape.give() - (off[2] < 0 ? (float) rate[2] * shape.bow() : 0f);
        float roll = soft((float) off[1] * shape.give(), shape.side());
        float rollRate = (float) rate[1] * shape.give();
        float rise = soft((float) Math.max(0.0, off[2]) * 16f / strm.emfcompat.core.ik.Skeleton.SCALE * shape.rise(), shape.riseLimit());
        return new Lean(pitch, pitchRate, roll, rollRate, rise);
    }

    /** Off the vehicle: still, and starting over. */
    public void reset() {
        tick = -1;
        moving = false;
        at = speed = went = shove = Vec3.ZERO;
        for (int i = 0; i < 3; i++) off[i] = rate[i] = 0;
    }

    /** {@code value}, but never past {@code limit} either way: the nearer the limit the less it gives. */
    public static float soft(float value, float limit) {
        return limit * (float) Math.tanh(value / limit);
    }
}
