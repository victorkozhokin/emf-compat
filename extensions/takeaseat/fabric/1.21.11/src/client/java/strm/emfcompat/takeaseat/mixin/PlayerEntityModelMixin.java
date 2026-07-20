package strm.emfcompat.takeaseat.mixin;

import com.takeaseat.client.TakeASeatClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {

    @Unique
    private static final String TAKEASEAT$POSE_SOURCE = "takeaseat";

    @Inject(
            method = "setArmAngle(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD")
    )
    private void takeaseat$restoreArmPoseForHeldItem(
            PlayerEntityRenderState state,
            Arm arm,
            MatrixStack matrices,
            CallbackInfo ci
    ) {
        if (MinecraftClient.getInstance().world == null) return;

        Entity entity = MinecraftClient.getInstance().world.getEntityById(state.id);
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        IAnimation layer;
        try {
            layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, TakeASeatClient.SIT_LAYER);
        } catch (Exception e) {
            return;
        }
        if (layer == null || !layer.isActive()) return;

        SavedPoses saved = PoseManager.getSavedPoses(player.getUuid(), TAKEASEAT$POSE_SOURCE);
        if (saved == null || saved.parts() == null) return;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        PoseSnapshot snapshot = arm == Arm.LEFT ? saved.parts().get("left_arm") : saved.parts().get("right_arm");
        if (snapshot == null) return;

        snapshot.apply(arm == Arm.LEFT ? model.leftArm : model.rightArm);
    }
}
