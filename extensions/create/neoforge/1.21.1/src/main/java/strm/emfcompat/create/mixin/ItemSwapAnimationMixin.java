package strm.emfcompat.create.mixin;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.animations.hands.ItemSwapAnimation;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.create.CreateCompat;

@Mixin(value = ItemSwapAnimation.class, remap = false)
public class ItemSwapAnimationMixin {

    @Inject(method = "isValid", at = @At("RETURN"), cancellable = true, remap = false)
    private void emfcompatCreate$disableSwapWhileActive(AbstractClientPlayer player, PlayerData data, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && CreateCompat.shouldDisableItemSwap(player)) {
            cir.setReturnValue(false);
        }
    }
}
