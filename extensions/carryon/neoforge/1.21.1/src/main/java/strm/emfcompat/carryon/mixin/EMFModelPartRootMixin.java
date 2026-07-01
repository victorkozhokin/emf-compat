package strm.emfcompat.carryon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.BodyPartSync;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import net.minecraft.world.entity.Entity;

/**
 * Captures the EMF-animated torso pose after EMF animation has run.
 * The body delta is used by {@link CarryRenderHelperMixin} to move carried
 * blocks and entities together with the player's torso.
 */
@Mixin(value = EMFModelPartRoot.class, priority = 900)
public class EMFModelPartRootMixin {

    @Inject(method = "animate", at = @At("RETURN"))
    private void carryonemfcompat$captureCurrentBodyPose(CallbackInfo ci) {
        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) return;

        Entity entity = (Entity) state.emfEntity();
        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;

        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            if ("[vanilla part body]".equals(part.toStringShort())) {
                BodyPartSync.captureCurrent(entity, "body", part);
                break;
            }
        }
    }
}
