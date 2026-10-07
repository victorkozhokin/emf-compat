package strm.emfcompat.animationadditions.interaction;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;

/** The questions every feature asks of a player and of the model's parts. */
public final class Body {

    private Body() {
    }

    /** Standing on the ground on their own feet and not walking. */
    public static boolean planted(Player player) {
        return player.onGround() && !player.isPassenger()
                && player.getDeltaMovement().horizontalDistanceSqr() < .0004;
    }

    /** Whether the main hand ({@code main}) or the other one is the right arm. */
    public static boolean right(Player player, boolean main) {
        return (player.getMainArm() == HumanoidArm.RIGHT) == main;
    }

    public static boolean right(Player player, InteractionHand hand) {
        return right(player, hand == InteractionHand.MAIN_HAND);
    }

    /** How far the head is turned from the body, radians. */
    public static float headYaw(LivingEntity entity) {
        return (float) Math.toRadians(Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot));
    }

    /** The point {@code length} pixels down a limb as it is posed now: a palm, a sole. */
    public static Vector3f tip(ModelPart limb, float length) {
        return new Quaternionf().rotationZYX(limb.zRot, limb.yRot, limb.xRot)
                .transform(new Vector3f(0, length * limb.yScale, 0)).add(limb.x, limb.y, limb.z);
    }

    /** A world point in model pixels this frame. */
    public static Vector3f model(IKFrame frame, Vec3 world) {
        return frame.relativeToJoint(world, new Vector3f());
    }
}
