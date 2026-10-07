package strm.mctest.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.mctest.Driver;

/** Hide the overlay while keeping the real menu and container-open state alive. Test driver only. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
    private void mctest$hideScreen(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (Driver.enabled() && Driver.hideScreen()) ci.cancel();
    }
}
