package strm.touchnmotion.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.touchnmotion.lead.LeadHold;

@Mixin(Player.class)
public class LeadPlayerMixin {
    @Inject(method = "getRopeHoldPosition", at = @At("RETURN"), cancellable = true)
    private void emfcompat$leadPalm(float partial, CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this instanceof AbstractClientPlayer player)
            cir.setReturnValue(LeadHold.rope(player, partial, cir.getReturnValue()));
    }
}
