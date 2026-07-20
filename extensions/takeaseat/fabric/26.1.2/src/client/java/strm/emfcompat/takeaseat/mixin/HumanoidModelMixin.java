package strm.emfcompat.takeaseat.mixin;

import com.seatify.client.SeatifyClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {

    @Unique
    private static final String TAKEASEAT$POSE_SOURCE = "takeaseat";

    @Inject(
            method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD")
    )
    private void takeaseat$restoreArmPoseForHeldItem(
            HumanoidRenderState state,
            HumanoidArm arm,
            PoseStack poseStack,
            CallbackInfo ci
    ) {
        if (!((Object) this instanceof PlayerModel)) return;
        PlayerModel model = (PlayerModel) (Object) this;
        if (!(state instanceof AvatarRenderState avatarState)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(avatarState.id);
        if (!(entity instanceof AbstractClientPlayer player)) return;

        IAnimation layer;
        try {
            layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, SeatifyClient.SIT_LAYER);
        } catch (Exception e) {
            return;
        }
        if (layer == null || !layer.isActive()) return;

        SavedPoses saved = PoseManager.getSavedPoses(player.getUUID(), TAKEASEAT$POSE_SOURCE);
        if (saved == null || saved.parts() == null) return;

        PoseSnapshot snapshot = arm == HumanoidArm.LEFT ? saved.parts().get("left_arm") : saved.parts().get("right_arm");
        if (snapshot == null) return;

        snapshot.apply(arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm);
    }
}
