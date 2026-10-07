package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.torso.PelvisFollow;
import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.FrameClock;
import strm.emfcompat.animationadditions.torso.Stride;

/** Small grounded setup steps; rotation transfers weight rather than walking every turn. */
final class CrankStance {
    static final class State {
        AbstractClientPlayer player;
        IKFrame space;
        Float angle;
        int direction;
        boolean eligible, turning;
        float activity, load;
        long motionAt, loggedAt;
        final Stride stride = new Stride();
        final FrameClock clock = new FrameClock();
    }

    static void observe(State s, AbstractClientPlayer player, IKFrame space, Float angle) {
        s.player = player;
        s.space = space;
        s.eligible = Body.planted(player);
        boolean moved = angle != null && s.angle != null && Math.abs(CrankStanceMath.delta(s.angle, angle)) > .05f;
        if (moved) { s.direction = CrankStanceMath.delta(s.angle, angle) > 0 ? 1 : -1; s.motionAt = System.nanoTime(); }
        s.turning = s.eligible && angle != null && System.nanoTime() - s.motionAt < 150_000_000L;
        s.angle = angle;
    }

    static float apply(State s, Function<String, ModelPart> parts, float owned) {
        if (s.player == null || s.space == null) return 0;
        ModelPart[] legs = {parts.apply("right_leg"), parts.apply("left_leg")};
        if (legs[0] == null || legs[1] == null) return 0;
        Stride stride = s.stride;
        double dt = s.clock.tick();
        if (dt >= 0) {
            if (!s.eligible) {
                stride.clear();
                s.activity = s.load = 0;
            } else {
                s.activity += (s.turning && owned > .5f ? 1 - s.activity : -s.activity) * Smoothing.follow(dt, .18);
                boolean turning = s.activity > .15f;
                if (stride.stepping < 0) {
                    for (int i = 0; i < 2; i++) {
                        Vector3f desired = CrankStanceMath.stance(i == 0, s.player.isCrouching(), s.direction, turning);
                        if (desired.distanceSquared(stride.feet[i]) > .01f
                                && Stride.level(s.player, s.space, legs[i], stride.feet[i], desired)) {
                            stride.begin(i, desired);
                            break;
                        }
                    }
                }
                if (stride.stepping >= 0) {
                    // Recheck the landing before lifting: a changed block cancels the step.
                    if (!Stride.level(s.player, s.space, legs[stride.stepping], stride.start, stride.end)) {
                        stride.feet[stride.stepping].set(stride.start);
                        stride.stepping = -1;
                    } else stride.advance(dt, .32f);
                }
                float desiredLoad = s.angle == null ? 0 : s.activity * s.direction * (float) Math.sin(Math.toRadians(s.angle)) * .7f;
                s.load += (desiredLoad - s.load) * Smoothing.follow(dt, .18);
            }
        }
        Vector3f[] soles = stride.drawn(s.player.isCrouching() ? .4f : .65f);
        Vector3f r = soles[0], l = soles[1];
        float twist = (float) Math.toRadians(4) * s.activity;
        PelvisFollow.step(parts, r, l, -twist, twist);
        if (strm.emfcompat.animationadditions.DebugLog.trace()
                && System.nanoTime() - s.loggedAt > 100_000_000L) {
            s.loggedAt = System.nanoTime();
            org.slf4j.LoggerFactory.getLogger("EMFCompatBlockUse").info(
                    "[StanceTrace] turning={} direction={} activity={} step={} progress={} right={} left={} load={}",
                    s.turning, s.direction, s.activity, stride.stepping, stride.progress, r, l, s.load);
        }
        return s.load;
    }
}
