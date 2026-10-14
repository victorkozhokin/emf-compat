package strm.touchnmotion.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.touchnmotion.compat.ShoulderCamera;

/** Shoulder Surfing, when it is there: it does not turn the player to the camera while they hold a craft's controls ({@link ShoulderCamera}). */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.ShoulderSurfing", remap = false)
public abstract class ShoulderTurnMixin {
    @Inject(method = {"shouldTurnWhenInteracting", "shouldTurnWhenUsingItem", "shouldTurnWhenAttacking", "shouldTurnWhenPicking"},
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void touchnmotion$handsOnControls(net.minecraft.world.entity.LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ShoulderCamera.atControls(entity)) cir.setReturnValue(false);
    }
    @Inject(method = "lookAtCrosshairTargetInternal", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void touchnmotion$keepControlFacing(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (ShoulderCamera.atControls(net.minecraft.client.Minecraft.getInstance().player)) ci.cancel();
    }

    @Inject(method="computeIsCameraDecoupled", at=@At("HEAD"), cancellable=true, require = 0, remap=false)
    private static void touchnmotion$freeCameraAtControls(net.minecraft.world.entity.Entity entity,
            boolean shoulder, boolean aiming, CallbackInfoReturnable<Boolean> cir) {
        if (shoulder && entity instanceof net.minecraft.world.entity.LivingEntity living && ShoulderCamera.atControls(living))
            cir.setReturnValue(true);
    }

}
