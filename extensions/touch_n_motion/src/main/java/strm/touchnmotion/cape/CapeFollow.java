package strm.touchnmotion.cape;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.PartMath;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A cape hangs off the torso, but a pack does not hang it off the torso's part: Fresh Animations
 * places it by the same variables it places the torso by, and the game's own cape does not follow
 * the part at all. So when the torso is leant or shifted here, after the pack, the cape stayed
 * where the pack's torso was and came off the back.
 *
 * <p>What was done to the torso is kept as one transform - from the pack's pose of it to the one
 * it is drawn with - and the cape is carried by the same.</p>
 */
public final class CapeFollow {
    private CapeFollow() {
    }

    private static final Map<UUID, Matrix4f> CARRIED = new HashMap<>();

    /** The torso's pose, as a transform, without its scale. */
    public static Matrix4f pose(ModelPart part) {
        return PartMath.transform(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, 1f, 1f, 1f);
    }

    /** The main model's torso: {@code pack} is its pose as the pack left it, the part is as it will be drawn. */
    public static void keep(UUID uuid, Matrix4f pack, ModelPart drawn) {
        Matrix4f by = pose(drawn).mul(pack.invert());
        // Nothing done to the torso is the usual case, and then nothing is done to the cape either.
        if ((by.properties() & Matrix4f.PROPERTY_IDENTITY) != 0 || by.equals(new Matrix4f(), 1e-5f)) CARRIED.remove(uuid);
        else CARRIED.put(uuid, by);
    }

    /**
     * Carries the cape of this model, if it has one. Where the cape hangs off this model's own torso
     * (1.21.11 on) it is that torso that is carried, from {@code packTorso}, its pose before anything
     * here touched it; where the cape is a part beside the torso, the cape itself.
     */
    public static void carry(UUID uuid, Function<String, ModelPart> parts, Matrix4f packTorso) {
        Matrix4f by = CARRIED.get(uuid);
        if (by == null) return;
        ModelPart torso = parts.apply("body");
        if (torso != null && packTorso != null && (torso.hasChild("cape") || torso.hasChild("cloak"))) {
            set(torso, new Matrix4f(by).mul(packTorso));
            return;
        }
        ModelPart cape = parts.apply("cloak");
        if (cape == null) cape = parts.apply("cape");
        if (cape != null) set(cape, new Matrix4f(by).mul(pose(cape)));
    }

    private static void set(ModelPart part, Matrix4f pose) {
        Vector3f at = pose.getTranslation(new Vector3f());
        Vector3f turn = pose.getEulerAnglesZYX(new Vector3f());
        part.x = at.x * 16f;
        part.y = at.y * 16f;
        part.z = at.z * 16f;
        part.xRot = turn.x;
        part.yRot = turn.y;
        part.zRot = turn.z;
    }

    /** Leaving a world. */
    public static void clear() {
        CARRIED.clear();
    }
}
