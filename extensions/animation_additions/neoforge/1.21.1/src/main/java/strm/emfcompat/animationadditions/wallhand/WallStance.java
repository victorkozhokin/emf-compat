package strm.emfcompat.animationadditions.wallhand;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.torso.PelvisFollow;
import strm.emfcompat.core.ik.IKFrame;
import traben.entity_model_features.models.animation.state.EMFState;
import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Ease;

/** Short alternating stance adjustments; moving feet follow the existing walking phase. */
final class WallStance {
    final Vector3f[] feet = {new Vector3f(), new Vector3f()};
    final Vector3f start = new Vector3f(), end = new Vector3f();
    int stepping = -1;
    float progress = 1, draw = -1, turn;
    long at, traceAt;
    private strm.emfcompat.animationadditions.torso.ClearanceOffset hipOffset = new strm.emfcompat.animationadditions.torso.ClearanceOffset();

    void reset() { for (var f : feet) f.zero(); stepping = -1; turn = 0; hipOffset = new strm.emfcompat.animationadditions.torso.ClearanceOffset(); }

    void apply(AbstractClientPlayer player, IKFrame space, Function<String, ModelPart> parts,
               float requestedTurn, float support, boolean enabled) {
        ModelPart[] legs = {parts.apply("right_leg"), parts.apply("left_leg")};
        if (space == null || legs[0] == null || legs[1] == null) return;
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > .0004;
        boolean ground = enabled && player.onGround() && !player.isPassenger();
        float counter = EMFState.getFrameCounter();
        if (counter != draw) {
            long now = System.nanoTime();
            double dt = at == 0 ? 0 : Math.min(.1, (now - at) * 1e-9);
            at = now;
            draw = counter;
            turn += ( (ground ? requestedTurn : 0) - turn) * Smoothing.follow(dt, .16);
            // Faded out to nothing worth a step: exactly none, so standing clear of walls asks nothing of the feet.
            if (requestedTurn == 0 && Math.abs(turn) < 1e-5f) turn = 0;
            if (!ground) { stepping = -1; for (var f : feet) f.mul(1 - Smoothing.follow(dt, .12)); }
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
                    stepping = -1;
                    // Keep the pack's support foot. Only its swinging partner
                    // changes placement; no second independent walking cycle.
                    for (int i = 0; i < 2; i++) {
                        float swing = i == 0 ? 1 - support : support;
                        feet[i].lerp(targets[i].mul(swing), Smoothing.follow(dt, .10));
                    }
                } else {
                    if (stepping < 0) for (int i = 0; i < 2; i++) {
                        if (targets[i].distanceSquared(feet[i]) > .09f
                                && safe(player, space, legs[i], feet[i], targets[i])) {
                            stepping = i;
                            start.set(feet[i]);
                            end.set(targets[i]);
                            progress = 0;
                            break;
                        }
                    }
                    if (stepping >= 0) {
                        if (!safe(player, space, legs[stepping], start, end)) { feet[stepping].zero(); stepping = -1; }
                        else {
                            progress = Math.min(1, progress + (float) dt / .28f);
                            feet[stepping].set(start).lerp(end, Ease.smooth(progress));
                            if (progress >= 1) stepping = -1;
                        }
                    }
                }
            }
        }
        Vector3f r = new Vector3f(feet[0]), l = new Vector3f(feet[1]);
        if (stepping >= 0)(stepping == 0 ? r : l).y -= (float) Math.sin(Math.PI * progress) * (player.isCrouching() ? .18f : .3f);
        float twist = turn * .45f;
        Vector3f hips = new Vector3f((legs[0].x + legs[1].x) * .5f,
                (legs[0].y + legs[1].y) * .5f, (legs[0].z + legs[1].z) * .5f);
        PelvisFollow.step(parts, r, l, twist, twist);
        Vector3f achieved = new Vector3f((legs[0].x + legs[1].x) * .5f,
                (legs[0].y + legs[1].y) * .5f, (legs[0].z + legs[1].z) * .5f).sub(hips);
        Vector3f correction = hipOffset.sample(counter, System.nanoTime(), achieved).sub(achieved);
        // Keep the solved soles, but don't transmit a reach-limit change straight
        // to the chest. Only this stance's additive hip displacement is filtered.
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
            ModelPart part = parts.apply(name);
            if (part != null) part.setPos(part.x + correction.x, part.y + correction.y, part.z + correction.z);
        }
        if (strm.emfcompat.core.EMFCompatConfig.getBoolean(WallSqueeze.KEY_TRACE, false)
                && System.nanoTime() - traceAt > 100_000_000L) {
            traceAt = System.nanoTime();
            org.slf4j.LoggerFactory.getLogger("EMFCompatWallStance").info(
                    "[WallStance] moving={} turn={} step={} progress={} support={} right={} left={}",
                    moving, turn, stepping, progress, support, r, l);
        }
    }
    private static Vector3f sole(ModelPart leg) {
        return new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot)
                .transform(new Vector3f(0, 12 * leg.yScale, 0)).add(leg.x, leg.y, leg.z);
    }
    private static boolean safe(AbstractClientPlayer player, IKFrame frame, ModelPart leg, Vector3f from, Vector3f to) {
        Vector3f sole = sole(leg);
        double height = Double.NaN;
        for (float t : new float[]{0, .5f, 1}) {
            var point = frame.jointWorld(new Vector3f(sole).add(new Vector3f(from).lerp(to, t)));
            for (double x : new double[]{-.09, 0, .09}) for (double z : new double[]{-.09, 0, .09}) {
                var p = point.add(x, 0, z);
                var hit = player.level().clip(new ClipContext(p.add(0, .25, 0), p.add(0, -.25, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.BLOCK || Math.abs(hit.getLocation().y - point.y) > .14) return false;
                if (Double.isNaN(height)) height = hit.getLocation().y;
                else if (Math.abs(hit.getLocation().y - height) > .04) return false;
            }
        }
        return true;
    }
}
