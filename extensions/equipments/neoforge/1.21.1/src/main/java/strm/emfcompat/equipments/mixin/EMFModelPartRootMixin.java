package strm.emfcompat.equipments.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.equipments.ArmorFallback;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/** EMF bakes a jem's {@code textureSize} into the boxes and keeps no copy; remember it per root. */
@Mixin(value = EMFModelPartRoot.class, remap = false)
public abstract class EMFModelPartRootMixin {

    @Inject(method = "addAndSetVariantOfJem", at = @At("HEAD"))
    private void emfCompat$recordTextureSize(EMFJemData jem, int variant, CallbackInfo ci) {
        boolean ownTexture = jem.texture != null && !jem.texture.isBlank();
        ArmorFallback.recordJem((EMFModelPartRoot) (Object) this, variant, jem.textureSize, ownTexture);
    }
}
