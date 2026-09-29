package strm.emfcompat.animationadditions.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.animationadditions.blockuse.BlockUse;

/** While a crouching player is drawn standing up ({@link StandUpMixin}), they are not crouching. */
@Mixin(Entity.class)
public class CrouchingMixin {

    @Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
    private void emfcompat$drawnStanding(CallbackInfoReturnable<Boolean> cir) {
        if (BlockUse.drawnStanding == (Object) this) cir.setReturnValue(false);
    }
}
