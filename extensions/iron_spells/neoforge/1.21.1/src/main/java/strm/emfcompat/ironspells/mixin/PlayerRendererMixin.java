package strm.emfcompat.ironspells.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

/**
 * Applies the saved Iron's Spells arm pose to the first-person hand model.
 * Core mixins skip pose restoration for the local player in first person, so without
 * this mixin the held item/hand would use the vanilla/EMF pose instead of the casting one.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Unique
    private static final String SOURCE = "iron_spells";

    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void emfcompat$restoreArmPoseBeforeFirstPersonArmRender(
            PoseStack stack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            ModelPart armPart,
            ModelPart sleevePart,
            CallbackInfo ci
    ) {
        emfcompat$restoreArmPose(armPart, sleevePart, player);
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
    private void emfcompat$restoreArmPoseBeforeFirstPersonSleeveRender(
            PoseStack stack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            ModelPart armPart,
            ModelPart sleevePart,
            CallbackInfo ci
    ) {
        emfcompat$restoreArmPose(armPart, sleevePart, player);
    }

    private void emfcompat$restoreArmPose(ModelPart armPart, ModelPart sleevePart, AbstractClientPlayer player) {
        SavedPoses saved = PoseManager.getSavedPoses(player.getUUID(), SOURCE);
        if (saved == null) {
            return;
        }

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
