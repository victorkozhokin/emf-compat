package strm.emfcompat.animationadditions.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.animationadditions.fishing.Fishing;

/**
 * The line's start, for a player seen from outside: the rod's tip as the fishing pose has it,
 * when that is asked for ({@link Fishing#lineStart}). The game's own place is a fixed one by the
 * hand; Enchanted Fishing Line draws its line from the same.
 */
@Mixin(value = FishingHookRenderer.class, priority = 900)
public abstract class FishingLineMixin {

    @Inject(method = "getPlayerHandPos", at = @At("RETURN"), cancellable = true)
    private void emfcompat$fromRodTip(Player player, float swing, float partial, CallbackInfoReturnable<Vec3> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson() && player == mc.player) return;
        Vec3 tip = Fishing.lineStart(player, partial);
        if (tip != null) cir.setReturnValue(tip);
    }
}
