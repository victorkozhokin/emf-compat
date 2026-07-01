package strm.neaemfcompat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Inject(
            method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD")
    )
    private void neaemfcompat$restoreArmPoseForItem(EntityRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatarState)) return;
        if (Minecraft.getInstance().level == null) return;
        Entity entity = Minecraft.getInstance().level.getEntity(avatarState.id);
        if (!(entity instanceof AbstractClientPlayer player)) return;

        var saved = PoseManager.entitySavedPoses.get(player.getUUID());
        if (saved == null) return;

        PlayerModel model = (PlayerModel) (Object) this;
        PoseSnapshot snapshot = arm == HumanoidArm.LEFT ? saved.leftArm() : saved.rightArm();
        if (snapshot != null) {
            snapshot.applyRotation(arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm);
        }
    }
}
