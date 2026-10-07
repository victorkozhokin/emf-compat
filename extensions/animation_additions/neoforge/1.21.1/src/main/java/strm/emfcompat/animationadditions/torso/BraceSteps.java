package strm.emfcompat.animationadditions.torso;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import org.slf4j.Logger;
import strm.emfcompat.animationadditions.DebugLog;
import strm.emfcompat.core.ik.IKFrame;

import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.FrameClock;

/** Sequential short brace steps on broad level support; walking keeps the pack's stride. */
public final class BraceSteps {
    public static class State {
        final Stride stride = new Stride();
        final FrameClock clock = new FrameClock();
        final DebugLog.Pace tracePace = new DebugLog.Pace();

        /** Where the two soles are between them, model pixels from where the pack has them: what the body has to go with. */
        public Vector3f mean() {
            return new Vector3f(stride.feet[0]).add(stride.feet[1]).mul(.5f);
        }

        /** Puts both soles down where they are given at once - landed there from a hop - with no step under way. */
        public void place(Vector3f right, Vector3f left) {
            stride.stepping = -1;
            stride.feet[0].set(right);
            stride.feet[1].set(left);
        }

        /** Both feet back under the pack's pose and no step under way: nothing to draw. */
        public boolean resting() {
            return stride.stepping < 0 && stride.feet[0].lengthSquared() < 1e-4f && stride.feet[1].lengthSquared() < 1e-4f;
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
        boolean still = Body.planted(player);
        Stride stride = s.stride;
        double dt = s.clock.tick();
        if (dt >= 0) {
            if (!still || effort < .05f) stride.settle(dt, .12);
            else {
                // A teleport or changed support must not reuse the stance from a
                // previous broad floor on a fence or over empty space.
                for (int i = 0; i < 2; i++) if (stride.feet[i].lengthSquared() > .001f
                        && !Stride.level(player, frame, legs[i], new Vector3f(), stride.feet[i])) {
                    stride.feet[i].zero();
                    if (stride.stepping == i) stride.stepping = -1;
                }
                if (stride.stepping < 0) for (int i = 0; i < 2; i++) {
                    Vector3f target = i == 0 ? right : left;
                    if (target.distanceSquared(stride.feet[i]) > .09f
                            && Stride.level(player, frame, legs[i], stride.feet[i], target)) {
                        stride.begin(i, target);
                        break;
                    }
                }
                if (stride.stepping >= 0) {
                    if (!Stride.level(player, frame, legs[stride.stepping], stride.start, stride.end)) stride.stepping = -1;
                    else stride.advance(dt, .32f);
                }
            }
        }
        Vector3f[] soles = stride.drawn(.45f);
        Vector3f r = soles[0], l = soles[1];
        if (!player.onGround() || player.isPassenger()) twist = 0;
        PelvisFollow.step(parts, r, l, -twist, twist);
        if (DebugLog.trace() && s.tracePace.due(100_000_000L)) {
            trace.info("[{}] still={} effort={} step={} progress={} right={} left={}",
                label, still, effort, stride.stepping, stride.progress, r, l);
        }
    }


    private BraceSteps() {
    }
}
