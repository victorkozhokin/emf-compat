package strm.emfcompat.animationadditions.buttonpress;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.torso.PelvisFollow;
import strm.emfcompat.core.ik.IKFrame;
import traben.entity_model_features.models.animation.state.EMFState;
import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Ease;

/** One short support step on the click; the other sole stays planted. */
final class LeverStep {
    AbstractClientPlayer player;
    IKFrame space;
    boolean eligible, holding;
    long press, consumed, at;
    float frame = -1, progress = 1;
    int foot;
    float stride = 1, duration = .24f, height = .35f;
    final Vector3f grip = new Vector3f(), offset = new Vector3f();
    final Vector3f start = new Vector3f(), end = new Vector3f();

    void observe(AbstractClientPlayer player, IKFrame space, boolean holding, Vector3f grip, long press) {
        this.player = player;
        this.space = space;
        this.holding = holding;
        this.eligible = player.onGround() && !player.isPassenger()
                && player.getDeltaMovement().horizontalDistanceSqr() < .0004;
        this.grip.set(grip);
        this.press = press;
    }

    void apply(Function<String, ModelPart> parts, float owned) {
        if (player == null || space == null) return;
        ModelPart[] legs = {parts.apply("right_leg"), parts.apply("left_leg")};
        if (legs[0] == null || legs[1] == null) return;
        float current = EMFState.getFrameCounter();
        if (current != frame) {
            long now = System.nanoTime();
            float dt = at == 0 ? 0 : Math.min(.1f, (now - at) * 1e-9f);
            at = now;
            frame = current;
            if (!eligible) { offset.zero(); start.zero(); end.zero(); progress = 1; consumed = press; }
            else {
                if (holding && owned > .5f && press > 0 && press != consumed) {
                    consumed = press;
                    if (offset.lengthSquared() < 1e-5f && progress >= 1) {
                        foot = grip.x <= 0 ? 0 : 1;
                        Vector3f wanted = LeverEffort.step(grip).mul(stride);
                        if (safe(legs[foot], offset, wanted)) begin(wanted);
                    }
                }
                if ((!holding || owned < .01f) && progress >= 1 && offset.lengthSquared() > 1e-5f
                        && safe(legs[foot], offset, new Vector3f())) begin(new Vector3f());
                if (progress < 1) {
                    if (!safe(legs[foot], start, end)) { offset.set(start); progress = 1; }
                    else {
                        progress = Math.min(1, progress + dt / duration);
                        offset.set(start).lerp(end, Ease.smooth(progress));
                    }
                }
            }
        }
        Vector3f stepping = new Vector3f(offset);
        if (progress < 1) stepping.y -= LeverEffort.lift(progress) * (player.isCrouching() ? height * .57f : height);
        PelvisFollow.step(parts, foot == 0 ? stepping : new Vector3f(), foot == 1 ? stepping : new Vector3f(), 0, 0);
    }

    private void begin(Vector3f wanted) { start.set(offset); end.set(wanted); progress = 0; }

    private boolean safe(ModelPart leg, Vector3f from, Vector3f to) {
        Vector3f sole = new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot)
                .transform(new Vector3f(0, 12 * leg.yScale, 0)).add(leg.x, leg.y, leg.z);
        double height = Double.NaN;
        for (float t : new float[]{0, .5f, 1}) {
            Vec3 point = space.jointWorld(new Vector3f(sole).add(new Vector3f(from).lerp(to, t)));
            for (double x : new double[]{-.10, 0, .10}) for (double z : new double[]{-.10, 0, .10}) {
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
}
