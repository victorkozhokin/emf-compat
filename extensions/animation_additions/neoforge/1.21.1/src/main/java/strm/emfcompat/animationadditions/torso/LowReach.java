package strm.emfcompat.animationadditions.torso;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import traben.entity_model_features.models.animation.state.EMFState;
import java.util.function.Function;

/** Hand contact with a grounded pelvis, applied after the ordinary torso layers. */
public final class LowReach {
    private LowReach() {}
    public static final class State {
        final Quaternionf turn = new Quaternionf();
        final Vector3f shift = new Vector3f();
        float frame = -1;
        long updatedAt;

        /** Keep both soles planted while the grounded contact fades away. */
        public boolean active() {
            return shift.lengthSquared() > 1e-6f || Math.abs(turn.x) + Math.abs(turn.y) + Math.abs(turn.z) > 1e-3f;
        }
    }

    public static void apply(Function<String, ModelPart> parts, boolean right, Vector3f target,
                             float weight, State state) {
        ModelPart body = parts.apply("body"), arm = parts.apply(right ? "right_arm" : "left_arm");
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (body == null || arm == null || r == null || l == null) return;
        Vector3f hips = new Vector3f((r.x + l.x) * .5f, (r.y + l.y) * .5f, (r.z + l.z) * .5f);
        float frame = EMFState.getFrameCounter();
        long now = System.nanoTime();
        if (frame != state.frame) {
            double dt = state.updatedAt == 0 ? 0 : Math.min(.1, (now - state.updatedAt) * 1e-9);
            state.updatedAt = now; state.frame = frame;
            Vector3f shoulder = new Vector3f(arm.x, arm.y, arm.z).sub(hips);
            Vector3f point = new Vector3f(target).sub(hips);
            Quaternionf wanted = LowReachMath.turn(shoulder, point, 11, (float)Math.toRadians(40));
            Vector3f fitted = new Quaternionf(wanted).transform(new Vector3f(shoulder)).add(hips);
            Vector3f move = LowReachMath.shift(fitted, target, 11, 3.5f);
            float k = Smoothing.follow(dt, .14);
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
