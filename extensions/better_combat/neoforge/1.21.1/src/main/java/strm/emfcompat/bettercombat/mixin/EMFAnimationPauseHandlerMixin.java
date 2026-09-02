package strm.emfcompat.bettercombat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.utils.EMFAnimationPauseHandler;
import strm.emfcompat.bettercombat.EMFCompatBetterCombatMod;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.core.PoseManager;

import java.util.UUID;

/**
 * Keeps EMF animating the player while a Better Combat attack is in flight.
 *
 * <p>EMF pauses its own animations whenever Player Animation Library has something playing, and
 * Better Combat 3.x drives its attacks through PAL. Without this the body and legs freeze for the
 * whole swing, even though this addon only needs the arms — and a paused entity is skipped before
 * EMF's animation hooks run, so the core never gets a chance to restore anything either.</p>
 *
 * <p>EMF 3.3 moved this decision out of {@code EMFAnimationEntityContext.isEntityAnimPaused},
 * where the addon used to override it, into {@code EMFAnimationPauseHandler}. The PAL check now
 * sits above the old override point, which is why the pause could no longer be lifted from there.</p>
 */
@Mixin(value = EMFAnimationPauseHandler.class)
public class EMFAnimationPauseHandlerMixin {

    private static final String SOURCE = "better_combat";

    @Inject(method = "shouldAnimationsPause", at = @At("RETURN"), cancellable = true)
    private static void emfcompat$unpauseDuringBetterCombatAttack(
            EMFEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (!EMFCompatBetterCombatMod.isEnabled()) {
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
        if (AttackPauseOverride.isUnpaused(uuid)) {
            cir.setReturnValue(false);
            return;
        }
        // Weapon stances go through their own source and keep PAL active for as long as the weapon
        // is held, so they have to lift the pause too - otherwise EMF stays frozen the whole time.
        if (PoseManager.getSavedPoses(uuid, SOURCE) != null
                || PoseManager.getSavedPoses(uuid, EMFCompatBetterCombatMod.GENERIC_SOURCE) != null) {
            cir.setReturnValue(false);
        }
    }
}
