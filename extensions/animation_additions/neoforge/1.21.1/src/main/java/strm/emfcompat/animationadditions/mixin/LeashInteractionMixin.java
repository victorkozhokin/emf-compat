package strm.emfcompat.animationadditions.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.animationadditions.leash.LeashHold;

/** Remember the actual attaching hand before survival consumes the last lead. */
@Mixin(MultiPlayerGameMode.class)
public class LeashInteractionMixin {
    @Inject(method = "interact", at = @At("HEAD"))
    private void emfcompat$leadHand(Player player, Entity target, InteractionHand hand,
                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof AbstractClientPlayer client) LeashHold.attachHand(client, target, hand);
    }
}
