package strm.emfcompat.animationadditions.torso;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import strm.emfcompat.animationadditions.DebugLog;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.core.ik.IKFrame;
import traben.entity_model_features.models.animation.state.EMFState;

import java.util.function.Function;

/** Sequential short brace steps on broad level support; walking keeps the pack's stride. */
public final class BraceSteps {
    public static class State {
        final Vector3f[] feet = {new Vector3f(), new Vector3f()};
        final Vector3f start = new Vector3f(), end = new Vector3f();
        int stepping = -1;
        float progress, frame = -1;
        long at, traceAt;

        /** Both feet back under the pack's pose and no step under way: nothing to draw. */
        public boolean resting() {
            return stepping < 0 && feet[0].lengthSquared() < 1e-4f && feet[1].lengthSquared() < 1e-4f;
        }
    }

    /**
     * {@code right} and {@code left} are where the soles should stand, model pixels from where the
     * pack has them; below an {@code effort} of 0.05, or once the player moves, they go back.
     */
    public static void apply(State s, AbstractClientPlayer player, IKFrame frame, Function<String, ModelPart> parts,
                             Vector3f right, Vector3f left, float effort, float twist, Logger trace, String label) {
        if (frame == null) return;
        ModelPart[] legs = {parts.apply("right_leg"), parts.apply("left_leg")};
        if (legs[0] == null || legs[1] == null) return;
        boolean still = player.onGround() && !player.isPassenger()
                && player.getDeltaMovement().horizontalDistanceSqr() < .0004;
        float counter = EMFState.getFrameCounter();
        if (counter != s.frame) {
            long now = System.nanoTime();
            double dt = s.at == 0 ? 0 : Math.min(.1, (now - s.at) * 1e-9);
            s.at = now;
            s.frame = counter;
            if (!still || effort < .05f) {
                s.stepping = -1;
                for (Vector3f foot : s.feet) foot.mul(1 - Smoothing.follow(dt, .12));
            } else {
                // A teleport or changed support must not reuse the stance from a
                // previous broad floor on a fence or over empty space.
                for (int i = 0; i < 2; i++) if (s.feet[i].lengthSquared() > .001f
                        && !safe(player, frame, legs[i], new Vector3f(), s.feet[i])) {
                    s.feet[i].zero();
                    if (s.stepping == i) s.stepping = -1;
                }
                if (s.stepping < 0) for (int i = 0; i < 2; i++) {
                    Vector3f target = i == 0 ? right : left;
                    if (target.distanceSquared(s.feet[i]) > .09f && safe(player, frame, legs[i], s.feet[i], target)) {
                        s.stepping = i;
                        s.start.set(s.feet[i]);
                        s.end.set(target);
                        s.progress = 0;
                        break;
                    }
                }
                if (s.stepping >= 0) {
                    if (!safe(player, frame, legs[s.stepping], s.start, s.end)) s.stepping = -1;
                    else {
                        s.progress = Math.min(1, s.progress + (float) dt / .32f);
                        s.feet[s.stepping].set(s.start).lerp(s.end, smooth(s.progress));
                        if (s.progress >= 1) s.stepping = -1;
                    }
                }
            }
        }
        Vector3f r = new Vector3f(s.feet[0]), l = new Vector3f(s.feet[1]);
        if (s.stepping >= 0)(s.stepping == 0 ? r : l).y -= (float) Math.sin(Math.PI * s.progress) * .45f;
        if (!player.onGround() || player.isPassenger()) twist = 0;
        PelvisFollow.step(parts, r, l, -twist, twist);
        if (DebugLog.trace() && System.nanoTime() - s.traceAt > 100_000_000L) {
            s.traceAt = System.nanoTime();
            trace.info("[{}] still={} effort={} step={} progress={} right={} left={}",
                label, still, effort, s.stepping, s.progress, r, l);
        }
    }

    private static float smooth(float v) { v = Math.max(0, Math.min(1, v)); return v * v * (3 - 2 * v); }

    private static boolean safe(AbstractClientPlayer player, IKFrame frame, ModelPart leg, Vector3f from, Vector3f to) {
        Vector3f sole = new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot)
            .transform(new Vector3f(0, 12 * leg.yScale, 0)).add(leg.x, leg.y, leg.z);
        double height = Double.NaN;
        for (float t : new float[]{0, .5f, 1}) {
            Vec3 point = frame.jointWorld(new Vector3f(sole).add(new Vector3f(from).lerp(to, t)));
            for (double x : new double[]{-.1, 0, .1}) for (double z : new double[]{-.1, 0, .1}) {
                Vec3 p = point.add(x, 0, z);
                var hit = player.level().clip(new ClipContext(p.add(0, .3, 0), p.add(0, -.3, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.BLOCK || Math.abs(hit.getLocation().y - point.y) > .16) return false;
                if (Double.isNaN(height)) height = hit.getLocation().y;
                else if (Math.abs(hit.getLocation().y - height) > .04) return false;
            }
        }
        return true;
    }

    private BraceSteps() {
    }
}
