package strm.touchnmotion.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import strm.touchnmotion.cape.CapeFollow;
import java.lang.reflect.Method;
import java.util.*;

/** Anchor the virtual neck in the current FPM draw, after additive animation, with no previous-frame offset. */
public final class FirstPersonHead {
    private FirstPersonHead() {}
    private static final class Api {
        static final Method RENDERING = resolve();
        static Method resolve() {
            try { return Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI").getMethod("isRenderingPlayer"); }
            catch (ReflectiveOperationException | LinkageError absent) { return null; }
        }
    }
    public static boolean active(UUID uuid) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.getUUID().equals(uuid) || Api.RENDERING == null
                || strm.touchnmotion.compat.ShadowPass.drawing()) return false;
        try { return Boolean.TRUE.equals(Api.RENDERING.invoke(null)); }
        catch (ReflectiveOperationException failure) { return false; }
    }
    /** The torso as the pack left it, and the neck on it in pixels. */
    public record Neck(Matrix4f torso, Vector3f pixels) {
        public static Neck of(ModelPart torso, ModelPart head) {
            if (torso == null) return null;
            Matrix4f pose = CapeFollow.pose(torso);
            Vector3f neck = pose.getTranslation(new Vector3f()).mul(16f);
            // FPM hides the head far outside the model; keep the torso's pivot in that case.
            if (head != null && Math.abs(head.x) + Math.abs(head.y) + Math.abs(head.z) < 64) neck.set(head.x, head.y, head.z);
            return new Neck(pose, neck);
        }
    }
    /** The pack's neck anchor is carried through the full torso delta. */
    public static Vector3f correction(Matrix4f pack, Matrix4f drawn) {
        return correction(pack, drawn, pack.getTranslation(new Vector3f()).mul(16f));
    }
    public static Vector3f correction(Matrix4f pack, Matrix4f drawn, Vector3f neckPixels) {
        Vector3f anchor = new Vector3f(neckPixels).div(16f);
        Vector3f local = new Matrix4f(pack).invert().transformPosition(new Vector3f(anchor));
        return drawn.transformPosition(local).sub(anchor).mul(16f).negate();
    }
    /** Shift only top-level parts: children inherit the shift, including modern hats and sleeves. */
    public static void apply(Neck neck, ModelPart torso, Collection<? extends ModelPart> parts) {
        if (neck == null || torso == null) return;
        shift(parts, correction(neck.torso(), CapeFollow.pose(torso), neck.pixels()));
    }
    private static void shift(Collection<? extends ModelPart> parts, Vector3f by) {
        Set<ModelPart> children = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart part : parts) {
            //? if >=1.21.11 {
            /*part.getAllParts().stream().filter(child -> child != part).forEach(children::add);
            *///?} else {
            part.getAllParts().filter(child -> child != part).forEach(children::add);
            //?}
        }
        Set<ModelPart> moved = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart part : parts) if (!children.contains(part) && moved.add(part)) {
            part.x += by.x; part.y += by.y; part.z += by.z;
        }
    }
}
