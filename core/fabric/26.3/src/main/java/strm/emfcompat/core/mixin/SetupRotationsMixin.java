package strm.emfcompat.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.CrouchNormalizer;

/**
 * Corrects how far the render of a crouching player is moved as a whole. The correction itself
 * lives in {@link CrouchNormalizer}.
 *
 * <p>Animation libraries do not only pose the model's parts. They also translate, rotate and scale
 * the entire {@link PoseStack} from their {@code body} bone — Player Animation Library in its
 * {@code LivingEntityRendererMixin.doTranslations}, injected into {@code submit} right before the
 * call to {@code scale}. Hackers 'n Slashers' sneak poses move that bone by -4, which drops the
 * whole player a quarter of a block on top of vanilla's own crouch offset. None of it shows up on
 * the model's parts, which is why a part-level probe sees nothing move.</p>
 *
 * <p>Measured from just before {@code setupRotations} to just after {@code scale}, so that anything
 * injected inside {@code setupRotations} is counted as well, whichever library put it there. For a
 * crouching player vanilla adds no translation anywhere in that stretch — a yaw rotation, the
 * model flip and a scale, all about the origin — so every vertical unit the delta carries came from
 * a mod. The delta is {@code before⁻¹ · after}: the translation that remains is what was added, in
 * the frame the entity was placed in, whatever rotation the camera had already put on the
 * stack.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SetupRotationsMixin {

    @Unique
    private static final Matrix4f EMFCOMPAT$BEFORE = new Matrix4f();

    @Unique
    private static CrouchNormalizer.Mode emfcompat$normalise = CrouchNormalizer.Mode.NONE;

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V"
            )
    )
    private void emfcompat$beforeSetupRotations(LivingEntityRenderState state, PoseStack poseStack,
                                                SubmitNodeCollector collector, CameraRenderState camera,
                                                CallbackInfo ci) {
        emfcompat$normalise = CrouchNormalizer.appliesTo(state);
        if (emfcompat$normalise != CrouchNormalizer.Mode.NONE) {
            EMFCOMPAT$BEFORE.set(poseStack.last().pose());
        }
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void emfcompat$afterScale(LivingEntityRenderState state, PoseStack poseStack,
                                      SubmitNodeCollector collector, CameraRenderState camera,
                                      CallbackInfo ci) {
        if (emfcompat$normalise == CrouchNormalizer.Mode.NONE) {
            return;
        }
        CrouchNormalizer.Mode mode = emfcompat$normalise;
        emfcompat$normalise = CrouchNormalizer.Mode.NONE;
        CrouchNormalizer.normalise(EMFCOMPAT$BEFORE, poseStack.last().pose(), mode);
    }
}
