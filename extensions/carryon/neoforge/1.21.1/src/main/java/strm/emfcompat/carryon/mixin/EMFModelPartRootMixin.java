package strm.emfcompat.carryon.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.EMFCarryOnMod;
import strm.emfcompat.core.BodyPartSync;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

/**
 * Legacy object-sync support: captures the EMF-animated torso pose after animation so
 * {@link CarryRenderHelperMixin} can move the carried block/entity with the torso via
 * {@link BodyPartSync} (translation + rotation).
 *
 * <p>Only runs when the Carry On "Arm sync" option is set to legacy (rotation-only); in
 * body-follow mode the carried object follows the core's published body-follow delta instead,
 * so this capture is skipped.</p>
 */
@Mixin(value = EMFModelPartRoot.class, priority = 900)
public class EMFModelPartRootMixin {

    @Inject(method = "animate", at = @At("RETURN"))
    private void carryonemfcompat$captureCurrentBodyPose(CallbackInfo ci) {
        if (!EMFCarryOnMod.isEnabled() || EMFCarryOnMod.isBodyFollow()) {
            return;
        }

        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || !(state.emfEntity() instanceof Entity entity)) return;

        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;
        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            if ("[vanilla part body]".equals(part.toStringShort())) {
                BodyPartSync.captureCurrent(entity, "body", part);
                break;
            }
        }
    }
}
