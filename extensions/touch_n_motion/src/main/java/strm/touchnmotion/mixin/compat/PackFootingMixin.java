package strm.touchnmotion.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;

/**
 * EMF's {@code is_on_ground}, as a resource pack reads it: a player walking down a heeled Sable
 * sub-level drops off its deck for a tick under each step, and the pack lands them each time.
 * Such a drop is standing on the deck ({@link strm.touchnmotion.interaction.Anchor#footing}).
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.math.EMFMath", remap = false)
public abstract class PackFootingMixin {
    @Shadow(remap = false)
    private static EMFEntityRenderState emfState() {
        throw new AssertionError();
    }

    @Inject(method = "isOnGround", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void touchnmotion$footingOnDeck(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        try {
            EMFEntityRenderState state = emfState();
            if (state != null && state.emfEntity() instanceof net.minecraft.world.entity.player.Player player
                    && strm.touchnmotion.interaction.Anchor.packFooting(player)) cir.setReturnValue(true);
        } catch (RuntimeException e) {
            strm.touchnmotion.interaction.Anchor.failed(e);
        }
    }
}
