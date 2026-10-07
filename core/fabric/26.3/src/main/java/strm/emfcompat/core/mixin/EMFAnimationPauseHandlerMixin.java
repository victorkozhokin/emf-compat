package strm.emfcompat.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.core.PauseOverride;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.utils.EMFAnimationPauseHandler;

import java.util.UUID;

/**
 * Lifts EMF's animation pause for any player an addon has captured a pose for.
 *
 * <p>This is the single place the pause is overridden. It used to be duplicated in the Better
 * Combat and Iron's Spells addons, each lifting the pause only for its own pose source, which
 * left every other addon — Take a Seat and Carry On in particular, both of which coexist with a
 * Player Animation Library animation — frozen with a captured pose they could never restore.</p>
 *
 * <p>Injected at RETURN rather than HEAD on purpose: EMF's own decision has to be made first, so
 * an explicit per-entity pause from another mod still wins (see the {@code entitiesPaused} check
 * below). See {@link PauseOverride} for why a saved pose is the right signal and why the pause
 * cannot simply be lifted whenever PAL is active.</p>
 *
 * <p>{@code remap = false} on both annotations: the target is EMF's own class, not Minecraft's.
 * Without it MixinGradle's annotation processor tries to map {@code shouldAnimationsPause} through
 * the searge mappings on Forge and fails the build.</p>
 */
@Mixin(value = EMFAnimationPauseHandler.class, remap = false)
public class EMFAnimationPauseHandlerMixin {

    @Inject(method = "shouldAnimationsPause", at = @At("RETURN"), cancellable = true, remap = false)
    private static void emfcompat$liftPauseWhileAnAddonHoldsAPose(
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
        // An explicit per-entity pause from another mod (a debug or cutscene freeze) is deliberate
        // and outranks anything an addon wants.
        if (EMFAnimationPauseHandler.entitiesPaused.contains(uuid)) {
            return;
        }
        if (PauseOverride.shouldLiftPause(uuid)) {
            cir.setReturnValue(false);
        }
    }
}
