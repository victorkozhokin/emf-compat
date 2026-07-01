package strm.neaemfcompat.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {

    @Unique
    private PlayerEntityRenderState neaemfcompat$currentState;

    @Inject(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("HEAD")
    )
    private void neaemfcompat$captureState(PlayerEntityRenderState state, CallbackInfo ci) {
        this.neaemfcompat$currentState = state;
    }

    @Inject(
            method = "setArmAngle(Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD")
    )
    private void neaemfcompat$restoreArmPoseForItem(Arm arm, MatrixStack matrices, CallbackInfo ci) {
        PlayerEntityRenderState state = this.neaemfcompat$currentState;
        if (state == null) return;
        if (MinecraftClient.getInstance().world == null) return;

        Entity entity = MinecraftClient.getInstance().world.getEntityById(state.id);
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        var saved = PoseManager.entitySavedPoses.get(player.getUuid());
        if (saved == null) return;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        PoseSnapshot snapshot = arm == Arm.LEFT ? saved.leftArm() : saved.rightArm();
        if (snapshot != null) {
            snapshot.applyRotation(arm == Arm.LEFT ? model.leftArm : model.rightArm);
        }
    }
}
