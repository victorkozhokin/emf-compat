package strm.touchnmotion.mixin.compat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.compat.ShoulderCamera;

/** Mouse turning is a separate route from crosshair snapping; the camera remains free at controls. */
@Pseudo
@Mixin(targets="com.github.exopandora.shouldersurfing.client.ShoulderSurfingCamera", remap=false)
public abstract class ShoulderControlCameraMixin {
    @Inject(method="turnPlayerWithCamera", at=@At("HEAD"), cancellable=true, require = 0, remap=false)
    private void touchnmotion$keepSeatedHeading(CallbackInfo ci) {
        if (ShoulderCamera.atControls(net.minecraft.client.Minecraft.getInstance().player)) ci.cancel();
    }
    @Inject(method="isCameraTurningWithPlayer", at=@At("HEAD"), cancellable=true, require = 0, remap=false)
    private void touchnmotion$keepIdleCamera(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (ShoulderCamera.cockpitCamera()) cir.setReturnValue(false);
    }
    @Inject(method="shouldResetCameraTurningWithPlayerDelay", at=@At("HEAD"), cancellable=true, require = 0, remap=false)
    private void touchnmotion$pauseIdleReturn(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (ShoulderCamera.cockpitCamera()) cir.setReturnValue(true);
    }

}
