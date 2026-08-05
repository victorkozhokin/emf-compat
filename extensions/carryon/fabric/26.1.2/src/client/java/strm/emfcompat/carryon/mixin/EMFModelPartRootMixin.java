package strm.emfcompat.carryon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.BodyPartSync;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.UUID;
import strm.emfcompat.carryon.EMFCarryOnClient;

/**
 * Captures the EMF-animated torso pose after EMF animation has run.
 * The body delta is used by {@link CarryRenderHelperMixin} to move carried
 * blocks and entities together with the player's torso.
 *
 * <p>EMF drives the animation from two entry points, and which one runs depends on the model:
 * {@code animate()} for nested model states and {@code triggerManualAnimation(PoseStack)} for
 * whole entity renderers — the player goes through the latter (EMF's {@code MixinLivingEntityRenderer}
 * calls it right after {@code EntityModel#setupAnim}). Both are hooked, mirroring the core's
 * restore mixin; without the manual one the legacy body sync never captured anything here.</p>
 */
@Mixin(value = EMFModelPartRoot.class, priority = 900)
public class EMFModelPartRootMixin {

    @Inject(method = "animate", at = @At("RETURN"))
    private void carryonemfcompat$captureCurrentBodyPose(CallbackInfo ci) {
        carryonemfcompat$doCaptureBody();
    }

    @Inject(method = "triggerManualAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"))
    private void carryonemfcompat$captureCurrentBodyPoseManual(PoseStack pose, CallbackInfo ci) {
        carryonemfcompat$doCaptureBody();
    }

    @Unique
    private void carryonemfcompat$doCaptureBody() {
        // Only the legacy arm-sync mode consumes this; body-follow gets everything it needs from
        // the core (root pose + body-follow delta).
        if (!EMFCarryOnClient.isEnabled() || EMFCarryOnClient.isBodyFollow()) {
            return;
        }

        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) return;

        UUID uuid = state.emfEntity().etf$getUuid();
        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;

        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            if ("[vanilla part body]".equals(part.toStringShort())) {
                BodyPartSync.captureCurrent(uuid, "body", part);
                break;
            }
        }
    }
}
