package strm.neaemfcompat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void neaemfcompat$restoreArmPoseBeforeFirstRender(PoseStack stack, MultiBufferSource buffer, int i, AbstractClientPlayer player, ModelPart armPart, ModelPart sleevePart, CallbackInfo ci) {
        restorePose(armPart, sleevePart, player);
    }

    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void neaemfcompat$restoreArmPoseBeforeSecondRender(PoseStack stack, MultiBufferSource buffer, int i, AbstractClientPlayer player, ModelPart armPart, ModelPart sleevePart, CallbackInfo ci) {
        restorePose(armPart, sleevePart, player);
    }

    private void restorePose(ModelPart armPart, ModelPart sleevePart, AbstractClientPlayer player) {
        SavedPoses saved = PoseManager.entitySavedPoses.get(player.getUUID());
        if (saved == null) return;

        PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
        PoseSnapshot snapshot;
        if (armPart == model.leftArm) {
            snapshot = saved.leftArm();
        } else if (armPart == model.rightArm) {
            snapshot = saved.rightArm();
        } else {
            return;
        }

        if (snapshot != null) {
            snapshot.applyRotation(armPart);
            snapshot.applyRotation(sleevePart);
        }
    }
}
