package strm.touchnmotion.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.net.ClientHands;

/** Capture both sides of predicted menu clicks, including a crafter's slot toggle. */
@Mixin(MultiPlayerGameMode.class)
public class MenuInteractionMixin {
    @Inject(method = {"handleInventoryMouseClick", "handleInventoryButtonClick"}, at = @At("HEAD"))
    private void touchnmotion$beforeMenuClick(CallbackInfo ci) { ClientHands.menuChanged(); }

    @Inject(method = {"handleInventoryMouseClick", "handleInventoryButtonClick"}, at = @At("RETURN"))
    private void touchnmotion$afterMenuClick(CallbackInfo ci) { ClientHands.menuChanged(); }
}
