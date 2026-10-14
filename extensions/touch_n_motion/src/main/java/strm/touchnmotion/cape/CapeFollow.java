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
 * it is drawn with - and the cape is carried by the same; an elytra's wings too.</p>
 */
public final class CapeFollow {
    private CapeFollow() {
    }

    private static final Map<UUID, Matrix4f> CARRIED = new HashMap<>();
    /** How far behind the player's model the game draws the wings' ({@code ElytraLayer}, {@code WingsLayer}): the model's units. */
    private static final float WINGS_BEHIND = 0.125f;

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
    public static void carry(UUID uuid, Function<String, ModelPart> parts, Matrix4f packTorso, Matrix4f capeFrame) {
        Matrix4f by = CARRIED.get(uuid);
        if (by == null) return;
        ModelPart torso = parts.apply("body");
        if (torso != null && packTorso != null && (torso.hasChild("cape") || torso.hasChild("cloak"))) {
            set(torso, new Matrix4f(by).mul(packTorso));
            return;
        }
        ModelPart cape = parts.apply("cloak");
        if (cape == null) cape = parts.apply("cape");
        // The game draws a cape beside the torso in a frame of its own (capeFrame): what was done to
        // the torso is carried into it, or the cape goes the other way - turned about, it is mirrored.
        if (cape != null) {
            Matrix4f in = capeFrame == null ? new Matrix4f(by)
                    : new Matrix4f(capeFrame).invert().mul(by).mul(capeFrame);
            set(cape, in.mul(pose(cape)));
        }
        // An elytra's wings are a model of their own, drawn a little behind the player's: the same there.
        ModelPart left = parts.apply("left_wing"), right = parts.apply("right_wing");
        if (left == null && right == null) return;
        Matrix4f behind = new Matrix4f().translation(0f, 0f, -WINGS_BEHIND).mul(by).translate(0f, 0f, WINGS_BEHIND);
        if (left != null) set(left, new Matrix4f(behind).mul(pose(left)));
        if (right != null) set(right, new Matrix4f(behind).mul(pose(right)));
    }

    /**
     * The frame the game's {@code CapeLayer} draws a cape beside the torso in, in the model's space:
     * a little behind it, swung about X, tilted about Z, and turned about to face back. Degrees, as the layer has them.
     */
    public static Matrix4f capeFrame(float swing, float tilt, float turn) {
        return new Matrix4f().translation(0f, 0f, WINGS_BEHIND)
                .rotateX((float) Math.toRadians(swing))
                .rotateZ((float) Math.toRadians(tilt))
                .rotateY((float) Math.toRadians(turn));
    }

    //? if <1.21.11 {
    /** This player's cape frame this frame, worked out as {@code CapeLayer} does it. */
    public static Matrix4f capeFrame(net.minecraft.world.entity.player.Player player, float partialTick) {
        double dx = net.minecraft.util.Mth.lerp(partialTick, player.xCloakO, player.xCloak) - net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX());
        double dy = net.minecraft.util.Mth.lerp(partialTick, player.yCloakO, player.yCloak) - net.minecraft.util.Mth.lerp(partialTick, player.yo, player.getY());
        double dz = net.minecraft.util.Mth.lerp(partialTick, player.zCloakO, player.zCloak) - net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ());
        float body = net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double sin = net.minecraft.util.Mth.sin(body * ((float) Math.PI / 180f)), cos = -net.minecraft.util.Mth.cos(body * ((float) Math.PI / 180f));
        float lift = net.minecraft.util.Mth.clamp((float) dy * 10f, -6f, 32f);
        float back = net.minecraft.util.Mth.clamp((float) (dx * sin + dz * cos) * 100f, 0f, 150f);
        float side = net.minecraft.util.Mth.clamp((float) (dx * cos - dz * sin) * 100f, -20f, 20f);
        float bob = net.minecraft.util.Mth.lerp(partialTick, player.oBob, player.bob);
        lift += net.minecraft.util.Mth.sin(net.minecraft.util.Mth.lerp(partialTick, player.walkDistO, player.walkDist) * 6f) * 32f * bob;
        if (player.isCrouching()) lift += 25f;
        return capeFrame(6f + back / 2f + lift, side / 2f, 180f - side / 2f);
    }
    //?}

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
