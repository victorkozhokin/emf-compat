package strm.emfcompat.ironspells.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import strm.emfcompat.ironspells.compat.IronSpellsCompat;

import java.util.UUID;

/**
 * Prevents EMF from pausing its player animations while an Iron's Spells cast is active.
 *
 * <p>EMF registers a global pause condition for Player Animator: whenever Player Animator has an
 * active animation, EMF pauses its own model animations. Iron's Spells uses Player Animator, so during a
 * cast EMF would normally freeze the whole player. This mixin overrides that pause so that the head
 * and legs can keep their EMF animations while the captured casting pose is restored afterwards.</p>
 */
@SuppressWarnings("deprecation")
@Mixin(EMFAnimationEntityContext.class)
public class EMFAnimationEntityContextMixin {

    @ModifyReturnValue(method = "isEntityAnimPaused()Z", at = @At("RETURN"))
    private static boolean emfcompat$unpauseDuringIronSpellsCast(boolean original) {
        if (!original) {
            return false;
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

        if (!(entity instanceof AbstractClientPlayer player)) {
            return original;
        }

        return !IronSpellsCompat.isCasting(player);
    }
}
