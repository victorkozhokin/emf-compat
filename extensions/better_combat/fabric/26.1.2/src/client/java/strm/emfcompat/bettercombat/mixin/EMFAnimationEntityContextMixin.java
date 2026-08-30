package strm.emfcompat.bettercombat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import strm.emfcompat.bettercombat.EMFCompatBetterCombatClient;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.core.EMFStateAccess;
import strm.emfcompat.core.PoseManager;

import java.util.UUID;

/**
 * Prevents EMF from pausing its player animations while a Better Combat attack is active.
 *
 * <p>EMF registers a global pause condition for Player Animation Library (PAL): whenever PAL has an
 * active animation, EMF pauses its own model animations. Better Combat 3.x uses PAL, so during an
 * attack EMF would normally freeze the body and legs even though this extension only needs to
 * override the arms and jacket. This mixin overrides that pause for the duration of a BC attack,
 * letting the body/legs keep their EMF animations while the core mixins restore the arms/jacket to
 * the BC pose afterwards.</p>
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
        if (!EMFCompatBetterCombatClient.isEnabled()) {
            return original;
        }

        var state = EMFStateAccess.current();
        if (state == null) {
            return original;
        }

        UUID uuid = state.uuid();
        if (uuid == null) {
            return original;
        }

        // Do not override an explicit per-entity pause from another mod (e.g. a debug/cutscene freeze).
        if (EMFStateAccess.isPausedExplicitly(uuid)) {
            return original;
        }
        if (AttackPauseOverride.isUnpaused(uuid)) {
            return false;
        }
        // Weapon stances go through their own source and keep PAL active for as long as the weapon
        // is held, so they have to lift the pause too — otherwise EMF stays frozen the whole time.
        return PoseManager.getSavedPoses(uuid, SOURCE) == null
                && PoseManager.getSavedPoses(uuid, EMFCompatBetterCombatClient.POSE_SOURCE) == null;
    }
}
