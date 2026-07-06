package strm.emfcompat.bettercombat.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.core.PoseManager;

import java.util.UUID;

/**
 * Prevents EMF from pausing its player animations while a Better Combat attack is active.
 *
 * <p>EMF registers a global pause condition for Player Animator: whenever Player Animator has an
 * active animation, EMF pauses its own model animations. Better Combat uses Player Animator, so during an
 * attack EMF would normally freeze the body and legs even though this extension only needs to
 * override the arms. This mixin overrides that pause for the duration of a BC attack,
 * letting the body/legs keep their EMF animations while the core mixins restore the arms afterwards.</p>
 */
@SuppressWarnings("deprecation")
@Mixin(value = EMFAnimationEntityContext.class, remap = false)
public class EMFAnimationEntityContextMixin {

    private static final String SOURCE = "better_combat";

    @Inject(method = "isEntityAnimPaused()Z", at = @At("RETURN"), cancellable = true)
    private static void emfcompat$unpauseDuringBetterCombatAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            cir.setReturnValue(false);
            return;
        }

        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) {
            return;
        }

        Entity entity = (Entity) state.emfEntity();
        UUID uuid = entity.getUUID();
        if (uuid == null) {
            return;
        }

        // Do not override an explicit per-entity pause from another mod (e.g. a debug/cutscene freeze).
        if (EMFAnimationEntityContext.entitiesPaused.contains(uuid)) {
            return;
        }
        if (AttackPauseOverride.isUnpaused(uuid)) {
            cir.setReturnValue(false);
            return;
        }
        if (PoseManager.getSavedPoses(uuid, SOURCE) != null) {
            cir.setReturnValue(false);
        }
    }
}
