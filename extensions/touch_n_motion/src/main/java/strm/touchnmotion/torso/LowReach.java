package strm.touchnmotion.torso;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.Smoothing;
import java.util.function.Function;
import strm.touchnmotion.interaction.FrameClock;

/** Hand contact with a grounded pelvis, applied after the ordinary torso layers. */
public final class LowReach {
    private LowReach() {}
    public static final class State {
        final Quaternionf turn = new Quaternionf();
        final Vector3f shift = new Vector3f();
        public double followSeconds = .14;
        public float angleLimit = (float) Math.toRadians(40);
        public float weightShift;
        public float weightForward;
        final FrameClock clock = new FrameClock();

        /** Keep both soles planted while the grounded contact fades away. */
        public boolean active() {
            return shift.lengthSquared() > 1e-6f || Math.abs(turn.x) + Math.abs(turn.y) + Math.abs(turn.z) > 1e-3f;
        }
    }

    public static void apply(Function<String, ModelPart> parts, boolean right, Vector3f target,
                             float weight, State state) {
        apply(parts, right, target, null, weight, state);
    }

    /** Balance the torso fit between both contacts without changing the single-hand solver. */
    public static void apply(Function<String, ModelPart> parts, boolean right, Vector3f target,
                             Vector3f otherTarget, float weight, State state) {
        ModelPart body = parts.apply("body"), arm = parts.apply(right ? "right_arm" : "left_arm");
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (body == null || arm == null || r == null || l == null) return;
        Vector3f hips = new Vector3f((r.x + l.x) * .5f, (r.y + l.y) * .5f, (r.z + l.z) * .5f);
        double dt = state.clock.tick();
        if (dt >= 0) {
            Vector3f shoulder = new Vector3f(arm.x, arm.y, arm.z).sub(hips);
            Vector3f point = new Vector3f(target).sub(hips);
            Quaternionf wanted = LowReachMath.turn(shoulder, point, 11, state.angleLimit);
            Vector3f fitted = new Quaternionf(wanted).transform(new Vector3f(shoulder)).add(hips);
            Vector3f move = LowReachMath.shift(fitted, target, 11, 3.5f);
            ModelPart other = otherTarget == null ? null : parts.apply(right ? "left_arm" : "right_arm");
            if (other != null) {
                Vector3f otherShoulder = new Vector3f(other.x, other.y, other.z).sub(hips);
                var fit = TwoHandReachMath.fit(shoulder, point, otherShoulder,
                        new Vector3f(otherTarget).sub(hips), 11, state.angleLimit);
                wanted.set(fit.turn());
                move.set(fit.shift());
            }
            move.x += state.weightShift;
            move.z += state.weightForward;
            float k = Smoothing.follow(dt, state.followSeconds);
            state.turn.slerp(new Quaternionf().slerp(wanted, weight), k).normalize();
            state.shift.lerp(move.mul(weight), k);
        }
        if (state.shift.lengthSquared() < 1e-8f && Math.abs(state.turn.x) + Math.abs(state.turn.y) + Math.abs(state.turn.z) < 1e-5f) return;
        Vector3f delta = state.shift;
        for (ModelPart leg : new ModelPart[]{r, l}) {
            var moved = PelvisFollow.translate(new Vector3f(leg.x, leg.y, leg.z),
                    leg.xRot, leg.yRot, leg.zRot, 12 * leg.yScale, delta.x, delta.z);
            leg.setPos(moved.pivot().x, moved.pivot().y, moved.pivot().z);
            leg.setRotation(moved.pitch(), moved.yaw(), moved.roll());
        }
        Vector3f movedHips = new Vector3f((r.x + l.x) * .5f, (r.y + l.y) * .5f, (r.z + l.z) * .5f);
        Vector3f achieved = new Vector3f(movedHips).sub(hips);
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
            ModelPart part = parts.apply(name);
            if (part == null) continue;
            var moved = PelvisFollow.carry(new Vector3f(part.x, part.y, part.z),
                    part.xRot, part.yRot, part.zRot, state.turn, hips, 0);
            part.setPos(moved.pivot().x + achieved.x, moved.pivot().y + achieved.y, moved.pivot().z + achieved.z);
            if (!name.equals("head") && !name.equals("hat"))
                part.setRotation(moved.pitch(), moved.yaw(), moved.roll());
        }
    }
}
