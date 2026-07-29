package strm.emfcompat.gliders.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.gliders.compat.FlightAnimationSupport;
import traben.entity_model_features.models.animation.EMFAnimationHandler;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * Detects whether the loaded pack has a flight animation for the player model.
 * {@code receiveAnimationHandler} fires for every model variant on every
 * resource reload, and the handler keeps the raw expression strings, so a
 * simple scan is enough. See {@link FlightAnimationSupport}.
 */
@Mixin(EMFModelPartRoot.class)
public class AnimationDetectionMixin {

    @Inject(method = "receiveAnimationHandler", at = @At("HEAD"), remap = false)
    private void gliders$detectFlightAnimation(int variant, EMFAnimationHandler handler, CallbackInfo ci) {
        if (FlightAnimationSupport.isPackFlightAware()) return;

        var modelId = ((EMFModelPartRoot) (Object) this).modelName;
        String fileName = modelId == null ? null : modelId.getfileName();
        // player.jem / player_slim.jem and player-derived custom models.
        if (fileName == null || !fileName.startsWith("player")) return;

        for (EMFAnimationHandler.AnimLineData line : handler.lines()) {
            FlightAnimationSupport.inspectLine(line.animKey, line.expression, line.isVar);
        }
    }
}
