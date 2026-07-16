package strm.neaemfcompat.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {

    @Inject(
            method = "setArmAngle(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD")
    )
    private void neaemfcompat$restoreArmPoseForItem(PlayerEntityRenderState state, Arm arm, MatrixStack matrices, CallbackInfo ci) {
        if (MinecraftClient.getInstance().world == null) return;
        Entity entity = MinecraftClient.getInstance().world.getEntityById(state.id);
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        var saved = PoseManager.getSavedPoses(player.getUuid());
        if (saved == null) return;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        PoseSnapshot snapshot = arm == Arm.LEFT ? saved.leftArm() : saved.rightArm();
        if (snapshot != null) {
            snapshot.applyRotation(arm == Arm.LEFT ? model.leftArm : model.rightArm);
        }
    }
}
