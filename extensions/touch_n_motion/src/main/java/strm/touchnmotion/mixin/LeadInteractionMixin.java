package strm.touchnmotion.mixin;

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
import strm.touchnmotion.lead.LeadHold;

/** Remember the actual attaching hand before survival consumes the last lead. */
@Mixin(MultiPlayerGameMode.class)
public class LeadInteractionMixin {
    @Inject(method = "interact", at = @At("HEAD"))
    //? if <26.1 {
    private void emfcompat$leadHand(Player player, Entity target, InteractionHand hand,
                                   CallbackInfoReturnable<InteractionResult> cir) {
    //?} else {
    /*private void emfcompat$leadHand(Player player, Entity target, net.minecraft.world.phys.EntityHitResult ray, InteractionHand hand,
                                   CallbackInfoReturnable<InteractionResult> cir) {
    *///?}
        if (player instanceof AbstractClientPlayer client) LeadHold.attachHand(client, target, hand);
    }
}
