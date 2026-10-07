package strm.emfcompat.animationadditions.torso;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.Ease;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.core.ik.IKFrame;

import java.util.function.Function;

/**
 * The two soles of a standing player moved one at a time: where each stands, model pixels from
 * where the pack has it, and the one step under way. What decides where a foot should go, and
 * when, stays with each stance; this is only the taking of the step.
 */
public final class Stride {

    private static final float[] ALONG = {0, .5f, 1};

    public final Vector3f[] feet = {new Vector3f(), new Vector3f()};
    public final Vector3f start = new Vector3f(), end = new Vector3f();
    /** The foot in the air, 0 right, 1 left; -1 with both down. */
    public int stepping = -1;
    public float progress = 1;

    public void begin(int foot, Vector3f target) {
        stepping = foot;
        start.set(feet[foot]);
        end.set(target);
        progress = 0;
    }

    /** Takes the step under way {@code dt} seconds further; it lasts {@code seconds}. */
    public void advance(double dt, float seconds) {
        progress = Math.min(1, progress + (float) dt / seconds);
        feet[stepping].set(start).lerp(end, Ease.smooth(progress));
        if (progress >= 1) stepping = -1;
    }

    /** No step, and both feet easing back under the pack's pose. */
    public void settle(double dt, double seconds) {
        stepping = -1;
        for (Vector3f foot : feet) foot.mul(1 - Smoothing.follow(dt, seconds));
    }

    public void clear() {
        stepping = -1;
        for (Vector3f foot : feet) foot.zero();
    }

    /** Where to draw the soles: the right and the left, the one in the air raised over its arc. */
    public Vector3f[] drawn(float height) {
        Vector3f[] soles = {new Vector3f(feet[0]), new Vector3f(feet[1])};
        if (stepping >= 0) soles[stepping].y -= (float) Math.sin(Math.PI * Ease.unit(progress)) * height;
        return soles;
    }

    /** {@link #level(AbstractClientPlayer, IKFrame, ModelPart, Vector3f, Vector3f, double, double, double)} with the usual sole. */
    public static boolean level(AbstractClientPlayer player, IKFrame frame, ModelPart leg, Vector3f from, Vector3f to) {
        return level(player, frame, leg, from, to, .10, .3, .16);
    }

    /**
     * Whether a sole can be taken from {@code from} to {@code to}: the whole short way and a sole's
     * footprint of {@code spread} blocks each side must have nearly level ground within
     * {@code tolerance} blocks of the sole, looked for {@code probe} blocks up and down.
     */
    public static boolean level(AbstractClientPlayer player, IKFrame frame, ModelPart leg, Vector3f from, Vector3f to,
                                double spread, double probe, double tolerance) {
        Vector3f sole = Body.tip(leg, 12);
        double height = Double.NaN;
        for (float t : ALONG) {
            Vec3 point = frame.jointWorld(new Vector3f(sole).add(new Vector3f(from).lerp(to, t)));
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                Vec3 p = point.add(x * spread, 0, z * spread);
                var hit = player.level().clip(new ClipContext(p.add(0, probe, 0), p.add(0, -probe, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.BLOCK || Math.abs(hit.getLocation().y - point.y) > tolerance) return false;
                if (Double.isNaN(height)) height = hit.getLocation().y;
                else if (Math.abs(hit.getLocation().y - height) > .04) return false;
            }
        }
        return true;
    }

    /** The point midway between the hips as the legs are posed now. */
    public static Vector3f hips(ModelPart[] legs) {
        return new Vector3f((legs[0].x + legs[1].x) * .5f, (legs[0].y + legs[1].y) * .5f, (legs[0].z + legs[1].z) * .5f);
    }

    /** Moves everything above the legs by {@code by}. */
    public static void carry(Function<String, ModelPart> parts, Vector3f by) {
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
            ModelPart part = parts.apply(name);
            if (part != null) part.setPos(part.x + by.x, part.y + by.y, part.z + by.z);
        }
    }
}
