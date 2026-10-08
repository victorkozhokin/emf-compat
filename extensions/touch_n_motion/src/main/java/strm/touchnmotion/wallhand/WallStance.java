package strm.touchnmotion.wallhand;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.Smoothing;
import strm.touchnmotion.torso.PelvisFollow;
import strm.emfcompat.core.ik.IKFrame;
import traben.entity_model_features.models.animation.state.EMFState;
import java.util.function.Function;
import strm.touchnmotion.interaction.FrameClock;
import strm.touchnmotion.torso.Stride;
import strm.touchnmotion.DebugLog;

/** Short alternating stance adjustments; moving feet follow the existing walking phase. */
final class WallStance {
    final Stride stride = new Stride();
    final FrameClock clock = new FrameClock();
    float turn;
    final DebugLog.Pace tracePace = new DebugLog.Pace();
    private strm.touchnmotion.torso.ClearanceOffset hipOffset = new strm.touchnmotion.torso.ClearanceOffset();

    void reset() { stride.clear(); turn = 0; hipOffset = new strm.touchnmotion.torso.ClearanceOffset(); }

    void apply(AbstractClientPlayer player, IKFrame space, Function<String, ModelPart> parts,
               float requestedTurn, float support, boolean enabled) {
        ModelPart[] legs = {parts.apply("right_leg"), parts.apply("left_leg")};
        if (space == null || legs[0] == null || legs[1] == null) return;
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > .0004;
        boolean ground = enabled && player.onGround() && !player.isPassenger();
        Vector3f[] feet = stride.feet;
        float counter = EMFState.getFrameCounter();
        double dt = clock.tick(counter, System.nanoTime());
        if (dt >= 0) {
            turn += ( (ground ? requestedTurn : 0) - turn) * Smoothing.follow(dt, .16);
            // Faded out to nothing worth a step: exactly none, so standing clear of walls asks nothing of the feet.
            if (requestedTurn == 0 && Math.abs(turn) < 1e-5f) turn = 0;
            if (!ground) stride.settle(dt, .12);
            else {
                Vector3f[] targets = new Vector3f[2];
                for (int i = 0; i < 2; i++) {
                    // A stable hip-width template, not the animated sole:
                    // breathing and the end of a stride must not request new
                    // alternating steps forever while the player stands still.
                    Vector3f relative = new Vector3f(i == 0 ? -2 : 2, 0, 0);
                    targets[i] = WallStanceMath.offset(relative, turn);
                    // A foot asked to go nowhere needs no ground looked for: 27 rays a foot, every frame, saved.
                    if (targets[i].lengthSquared() > 0 && !safe(player, space, legs[i], feet[i], targets[i])) targets[i].zero();
                }
                if (moving) {
                    stride.stepping = -1;
                    // Keep the pack's support foot. Only its swinging partner
                    // changes placement; no second independent walking cycle.
                    for (int i = 0; i < 2; i++) {
                        float swing = i == 0 ? 1 - support : support;
                        feet[i].lerp(targets[i].mul(swing), Smoothing.follow(dt, .10));
                    }
                } else {
                    if (stride.stepping < 0) for (int i = 0; i < 2; i++) {
                        if (targets[i].distanceSquared(feet[i]) > .09f
                                && safe(player, space, legs[i], feet[i], targets[i])) {
                            stride.begin(i, targets[i]);
                            break;
                        }
                    }
                    if (stride.stepping >= 0) {
                        if (!safe(player, space, legs[stride.stepping], stride.start, stride.end)) {
                            feet[stride.stepping].zero();
                            stride.stepping = -1;
                        } else stride.advance(dt, .28f);
                    }
                }
            }
        }
        Vector3f[] soles = stride.drawn(player.isCrouching() ? .18f : .3f);
        Vector3f r = soles[0], l = soles[1];
        float twist = turn * .45f;
        Vector3f hips = Stride.hips(legs);
        PelvisFollow.step(parts, r, l, twist, twist);
        Vector3f achieved = Stride.hips(legs).sub(hips);
        // Keep the solved soles, but don't transmit a reach-limit change straight
        // to the chest. Only this stance's additive hip displacement is filtered.
        Stride.carry(parts, hipOffset.sample(counter, System.nanoTime(), achieved).sub(achieved));
        if (strm.emfcompat.core.EMFCompatConfig.getBoolean(WallSqueeze.KEY_TRACE, false)
                && tracePace.due(100_000_000L)) {
            org.slf4j.LoggerFactory.getLogger("EMFCompatWallStance").info(
                    "[WallStance] moving={} turn={} step={} progress={} support={} right={} left={}",
                    moving, turn, stride.stepping, stride.progress, support, r, l);
        }
    }
    private static boolean safe(AbstractClientPlayer player, IKFrame frame, ModelPart leg, Vector3f from, Vector3f to) {
        return Stride.level(player, frame, leg, from, to, .09, .25, .14);
    }
}
