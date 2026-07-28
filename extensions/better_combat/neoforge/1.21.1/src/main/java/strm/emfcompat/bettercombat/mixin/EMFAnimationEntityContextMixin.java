package strm.emfcompat.bettercombat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import strm.emfcompat.bettercombat.EMFCompatBetterCombatMod;
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
@Mixin(EMFAnimationEntityContext.class)
public class EMFAnimationEntityContextMixin {

    private static final String SOURCE = "better_combat";

    @ModifyReturnValue(method = "isEntityAnimPaused()Z", at = @At("RETURN"))
    private static boolean emfcompat$unpauseDuringBetterCombatAttack(boolean original) {
        if (!original) {
            return false;
        }
        if (!EMFCompatBetterCombatMod.isEnabled()) {
            return original;
        }

        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) {
            return original;
        }

        Entity entity = (Entity) state.emfEntity();
        UUID uuid = entity.getUUID();
        if (uuid == null) {
            return original;
        }

        // Do not override an explicit per-entity pause from another mod (e.g. a debug/cutscene freeze).
        if (EMFAnimationEntityContext.entitiesPaused.contains(uuid)) {
            return original;
        }
        if (AttackPauseOverride.isUnpaused(uuid)) {
            return false;
        }
        // Unpause for a Better Combat attack pose OR a generic (Spell Engine / RPG Series) capture,
        // so the body/legs keep their EMF animations while the arms are restored afterwards.
        if (PoseManager.getSavedPoses(uuid, SOURCE) != null
                || PoseManager.getSavedPoses(uuid, EMFCompatBetterCombatMod.GENERIC_SOURCE) != null) {
            return false;
        }
        return original;
    }
}
