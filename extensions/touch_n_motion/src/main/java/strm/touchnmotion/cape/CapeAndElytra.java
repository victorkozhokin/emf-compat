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
 * Everything done here for a cape and an elytra's wings, in one place. They hang off the torso but are
 * drawn apart from it - by layers of their own, and with a pack, by models of their own - so what is
 * done to the torso has to reach them separately:
 * <ul>
 *   <li><b>Carried with the torso.</b> A pack does not hang the cape off the torso's part: Fresh
 *   Animations places it by the variables it places the torso by, and the game's own cape does not
 *   follow the part at all. When the torso is leant or shifted here, after the pack, the cape stayed
 *   where the pack's torso was and came off the back - on a galloping horse, the rider folded over
 *   the neck, it stood off high behind the head. What was done to the torso is kept as one transform
 *   ({@link #follow}), from the pack's pose of it to the one it is drawn with, and the cape and the
 *   wings are carried by the same:
 *   <ul>
 *     <li>up to 1.21.10 their layers are drawn on the model's stack with it put on first
 *     ({@link #beginLayer}, {@code CapeTurnMixin}, {@code ElytraFollowMixin}) - whoever draws the
 *     cape: the game's layer swinging it out, or EMF drawing a pack's cape model in its place, which
 *     the hook never sees; the wings whether a pack animates them or not;</li>
 *     <li>from 1.21.11 the cape hangs off the model's torso, which is carried; the wings' parts are.</li>
 *   </ul></li>
 *   <li><b>Swinging from the body drawn</b> ({@link #turned}): a model turned round from the body
 *   the game turns it by (a craft's wheel, a train's controls, a minecart) has the cape swing as from
 *   the body drawn ({@code CapeTurnMixin}).</li>
 *   <li><b>No wind</b> ({@link #noWind}): carried along inside something (a train's cab), the cape
 *   hangs instead of flying out with the speed.</li>
 * </ul>
 */
public final class CapeAndElytra {
    private CapeAndElytra() {
    }

    private static final Map<UUID, Matrix4f> CARRIED = new HashMap<>();
    /** How far round the model is drawn from the body the game turns it by, degrees: set by a seat that turns it to its controls. */
    private static final Map<UUID, Float> TURNED = new HashMap<>();
    /** How far behind the player's model the game draws the wings ({@code WingsLayer}): the model's units. */
    private static final float WINGS_BEHIND = 0.125f;

    /** The torso's pose, as a transform, without its scale. */
    public static Matrix4f pose(ModelPart part) {
        return PartMath.transform(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, 1f, 1f, 1f);
    }

    /** Before anything here touches this model: its torso as the pack left it, {@code null} without one. */
    public static Matrix4f packTorso(Function<String, ModelPart> parts) {
        ModelPart torso = parts.apply("body");
        return torso == null ? null : pose(torso);
    }

    /**
     * After everything here has been done to this model: the main model's torso is kept as what the
     * cape and the wings are carried by, and from 1.21.11 this model's cape or wings, if it has them,
     * are carried.
     *
     * @param packTorso {@link #packTorso} of this model
     */
    public static void follow(UUID uuid, Function<String, ModelPart> parts, boolean main, Matrix4f packTorso) {
        ModelPart torso = parts.apply("body");
        if (main && torso != null && packTorso != null) keep(uuid, new Matrix4f(packTorso), torso);
        //? if >=1.21.11 {
        /*carry(uuid, parts, packTorso);
        *///?}
    }

    /**
     * Before a cape's or the wings' layer draws for this player (up to 1.21.10): what was done to the
     * torso is put on the stack, under everything the layer and EMF do. {@code true} when it was, and
     * {@link #endLayer} has to take it off after.
     */
    public static boolean beginLayer(com.mojang.blaze3d.vertex.PoseStack stack, UUID uuid) {
        Matrix4f by = CARRIED.get(uuid);
        if (by == null) return false;
        stack.pushPose();
        strm.touchnmotion.platform.Platform.apply(stack, by);
        return true;
    }

    public static void endLayer(com.mojang.blaze3d.vertex.PoseStack stack, boolean begun) {
        if (begun) stack.popPose();
    }

    /** The main model's torso: {@code pack} is its pose as the pack left it, the part is as it will be drawn. */
    static void keep(UUID uuid, Matrix4f pack, ModelPart drawn) {
        Matrix4f by = pose(drawn).mul(pack.invert());
        // Nothing done to the torso is the usual case, and then nothing is done to the cape either.
        if ((by.properties() & Matrix4f.PROPERTY_IDENTITY) != 0 || by.equals(new Matrix4f(), 1e-5f)) CARRIED.remove(uuid);
        else CARRIED.put(uuid, by);
    }

    /**
     * From 1.21.11: carries the cape of this model, which hangs off its own torso - that torso, from
     * {@code packTorso}, its pose before anything here touched it - and an elytra's wings, a model of
     * their own drawn a little behind the player's.
     */
    static void carry(UUID uuid, Function<String, ModelPart> parts, Matrix4f packTorso) {
        Matrix4f by = CARRIED.get(uuid);
        if (by == null) return;
        ModelPart torso = parts.apply("body");
        if (torso != null && packTorso != null && (torso.hasChild("cape") || torso.hasChild("cloak"))) {
            set(torso, new Matrix4f(by).mul(packTorso));
            return;
        }
        ModelPart left = parts.apply("left_wing"), right = parts.apply("right_wing");
        if (left == null && right == null) return;
        Matrix4f behind = new Matrix4f().translation(0f, 0f, -WINGS_BEHIND).mul(by).translate(0f, 0f, WINGS_BEHIND);
        if (left != null) set(left, new Matrix4f(behind).mul(pose(left)));
        if (right != null) set(right, new Matrix4f(behind).mul(pose(right)));
    }

    /** How far round from the game's body this player's model is drawn now, degrees; 0 when it is not turned. */
    public static float turned(UUID uuid) {
        Float degrees = TURNED.get(uuid);
        return degrees == null ? 0f : degrees;
    }

    /** This draw of the model is turned this far round from the game's body, degrees. */
    public static void turned(UUID uuid, float degrees) {
        if (Math.abs(degrees) < .01f) TURNED.remove(uuid);
        else TURNED.put(uuid, degrees);
    }

    /** The draw turned from facing {@code before} to facing {@code after} (the model's forward, in the stack's space). */
    public static void turned(UUID uuid, Vector3f before, Vector3f after) {
        turned(uuid, net.minecraft.util.Mth.wrapDegrees(
                (float) Math.toDegrees(Math.atan2(-after.x, after.z) - Math.atan2(-before.x, before.z))));
    }

    /**
     * Carried along inside something, the cape hangs: where it trails from is put on the player, so
     * the speed is no wind. Up to 1.21.10, where the game keeps it on the player.
     */
    public static void noWind(net.minecraft.world.entity.player.Player player) {
        //? if <1.21.11 {
        player.xCloakO = player.xo;
        player.yCloakO = player.yo;
        player.zCloakO = player.zo;
        player.xCloak = player.getX();
        player.yCloak = player.getY();
        player.zCloak = player.getZ();
        //?}
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
        TURNED.clear();
    }
}
