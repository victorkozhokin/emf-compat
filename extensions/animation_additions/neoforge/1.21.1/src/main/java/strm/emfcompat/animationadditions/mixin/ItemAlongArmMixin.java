package strm.emfcompat.animationadditions.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.animationadditions.blockuse.BlockUse;

/**
 * Turns the held item to lie along the arm when {@link BlockUse} asks for it - a key going into a
 * keyhole, the arm stretched out to it. Vanilla holds an item square to the arm, sticking forwards
 * out of the fist; here it is turned about the fist, in the arm's own axes, right after the pose
 * stack has been carried to the hand.
 */
@Mixin(ItemInHandLayer.class)
public class ItemAlongArmMixin {

    /** The fist in the arm's axes, blocks: where vanilla puts the middle of a held item, as {@code ItemInHandLayer} translates it. */
    private static final float FIST_Y = 10f / 16f;
    private static final float FIST_Z = -2f / 16f;
    /** Turned about the fist, the item's middle is at the fist and half of it in the arm: it goes this far on down the arm, blocks. */
    private static final float OUT_OF_FIST = 4.5f / 16f;

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void emfcompat$itemAlongArm(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                        PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        float w = BlockUse.itemAlongArm(player.getUUID(), arm == HumanoidArm.RIGHT);
        if (w < 1e-3f) return;
        // The arm's axes: y down the arm, -z forwards. The far end of a flat item held by vanilla
        // points back (+z); a turn of -90 degrees about x lays it down the arm (+y), and a quarter
        // turn about the arm stands its flat side up, seen from the side when the arm points ahead.
        Quaternionf turn = new Quaternionf().rotationY((float) (Math.PI / 2)).rotateX((float) (-Math.PI / 2));
        Quaternionf blended = new Quaternionf().slerp(turn, w);
        pose.translate(0f, FIST_Y + OUT_OF_FIST * w, FIST_Z);
        pose.mulPose(blended);
        pose.translate(0f, -FIST_Y, -FIST_Z);
    }
}
