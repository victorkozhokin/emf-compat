package strm.emfcompat.ironspells.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.ironspells.compat.IronSpellsCompat;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.utils.EMFAnimationPauseHandler;

import java.util.UUID;

/**
 * Keeps EMF animating the player while an Iron's Spells cast is active.
 *
 * <p>EMF pauses its own animations whenever Player Animator has something playing, and Iron's
 * Spells casts through Player Animator, so without this the whole player freezes for the cast.
 * Worse, a paused entity is skipped before EMF's animation hooks run, so the core never gets the
 * chance to restore the captured casting pose either.</p>
 *
 * <p>EMF 3.3 moved this decision out of {@code EMFAnimationEntityContext.isEntityAnimPaused},
 * where this used to hook it, into {@code EMFAnimationPauseHandler} — with the Player Animator
 * check now sitting above the old override point.</p>
 */
@Mixin(EMFAnimationPauseHandler.class)
public class EMFAnimationPauseHandlerMixin {

    @Inject(method = "shouldAnimationsPause", at = @At("RETURN"), cancellable = true)
    private static void emfcompat$unpauseDuringIronSpellsCast(
            EMFEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (state == null) {
            return;
        }

        UUID uuid = state.uuid();
        if (uuid == null) {
            return;
        }

        // Do not override an explicit per-entity pause from another mod (e.g. a debug/cutscene freeze).
        if (EMFAnimationPauseHandler.entitiesPaused.contains(uuid)) {
            return;
        }

        if (!(state.emfEntity() instanceof Entity entity)
                || !(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        if (IronSpellsCompat.isCasting(player)) {
            cir.setReturnValue(false);
        }
    }
}
