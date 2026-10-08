package strm.touchnmotion.mixin.modern;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.touchnmotion.blockuse.BlockUse;

/**
 * From 26.3 on: the swing kept off a player whose hand is on a crank. A hand crank held down
 * starts a swing on every repeat of the click, and the pack plays each in full, twisting the torso
 * and the other arm. Up to 26.2 the swing is two fields of the entity, zeroed while its state is
 * read ({@link StateEntityMixin}); from 26.3 it is kept out of reach and only told, so here it is
 * told as none - to whoever asks, for as long as the hand is on the crank.
 */
@Mixin(LivingEntity.class)
public class SwingQuietMixin {

    @Inject(method = "getSwingAnimation(F)F", at = @At("HEAD"), cancellable = true)
    private void touchnmotion$quiet(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof AbstractClientPlayer player && BlockUse.quietsSwing(player.getUUID())) cir.setReturnValue(0f);
    }
}
