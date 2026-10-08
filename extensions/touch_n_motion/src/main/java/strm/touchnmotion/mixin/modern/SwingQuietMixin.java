package strm.touchnmotion.mixin.modern;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.touchnmotion.blockuse.BlockUse;

/**
 * From 1.21.11 on: the swing kept off a player whose hand is on a crank. A hand crank held down
 * starts a swing on every repeat of the click, and the pack plays each in full, twisting the torso
 * and the other arm. The renderer asks the entity for its swing more than once while it fills the
 * render state, a player's own part of it after the common one - so the swing is told as none to
 * whoever asks, for as long as the hand is on the crank. (Measured on 1.21.11 with only the
 * entity's two fields zeroed for the common part: the swing was in the state 176 frames of 230.)
 */
@Mixin(LivingEntity.class)
public class SwingQuietMixin {

    //? if >=26.3 {
    /*@Inject(method = "getSwingAnimation(F)F", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "getAttackAnim(F)F", at = @At("HEAD"), cancellable = true)
    //?}
    private void touchnmotion$quiet(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof AbstractClientPlayer player && BlockUse.quietsSwing(player.getUUID())) cir.setReturnValue(0f);
    }
}
