package strm.emfcompat.carryon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.CarryOnRenderState;
import strm.emfcompat.core.BodyPartSync;
import tschipp.carryon.client.render.CarryRenderHelper;
import strm.emfcompat.carryon.EMFCarryOnClient;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

/**
 * Keeps the carried block/entity attached to the hands and registers carried entities for
 * EMF's vanilla-model condition.
 *
 * <p>In body-follow mode the carried object is shifted by the exact translation the core applies
 * to the player's arms this frame ({@link PoseManager#getBodyFollowDelta}) and then turned by the
 * torso's rotation delta, so it tracks the arms instead of swinging independently of the raised
 * pose. Legacy mode drives both from {@link BodyPartSync} alone.</p>
 *
 * <p>Note that Carry On 2.9.0 renamed the entry points to {@code setup*Transformations(…, boolean
 * firstPerson)}; in third person those still call the {@code apply*Transformations} methods hooked
 * here, so the injection points below are the live ones.</p>
 */
@Mixin(CarryRenderHelper.class)
public class CarryRenderHelperMixin {

    // Legacy: original BodyPartSync scale (0.6/16), kept as-is to reproduce the old look.
    private static final float LEGACY_SCALE = 0.6f / 16.0f;

    @Inject(
            method = "applyBlockTransformations(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/Block;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ltschipp/carryon/client/render/CarryRenderHelper;applyGeneralTransformations(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void emfcompat$syncCarriedBlockWithBody(
            Player player,
            PoseStack poseStack,
            Block block,
            CallbackInfo ci
    ) {
        applyBodyDelta(poseStack, player);
    }

    @Inject(
            method = "applyEntityTransformations(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ltschipp/carryon/client/render/CarryRenderHelper;applyGeneralTransformations(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void emfcompat$syncCarriedEntityWithBody(
            Player player,
            PoseStack poseStack,
            Entity entity,
            CallbackInfo ci
    ) {
        applyBodyDelta(poseStack, player);
    }

    @Inject(
            method = "applyEntityTransformations(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD")
    )
    private static void emfcompat$markCarriedEntity(
            Player player,
            PoseStack poseStack,
            Entity entity,
            CallbackInfo ci
    ) {
        CarryOnRenderState.markCarried(entity);
        emfcompat$applyRootTransform(poseStack, player);
    }

    @Inject(
            method = "applyBlockTransformations(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/Block;)V",
            at = @At("HEAD")
    )
    private static void emfcompat$applyRootTransformForBlock(
            Player player,
            PoseStack poseStack,
            Block block,
            CallbackInfo ci
    ) {
        emfcompat$applyRootTransform(poseStack, player);
    }

    /**
     * Reproduces, for the carried object, the transforms the arms receive from the model.
     *
     * <p>Must run at HEAD, before Carry On's own transforms: at that point the matrix is still the
     * model's own frame, with the origin at the root pivot, so both steps below can be applied
     * verbatim in model units — no axis flips, no sign conversion, no undoing Carry On's 0.6
     * scale. Both are laid down in the same order the model applies them, so the object ends up
     * rigidly attached to the arms:</p>
     *
     * <ol>
     *   <li><b>Root pose</b>, exactly the way a {@link net.minecraft.client.model.geom.ModelPart}
     *       applies its own transform. Packs animate the root to lean and sway the whole player;
     *       the arms inherit that as children, the object (drawn from the entity matrix) would
     *       otherwise miss it entirely. Root yaw reaches ~13°, which at the object's reach
     *       dominates everything else.</li>
     *   <li><b>Body-follow delta</b>, the offset the core adds to the arms' own position inside
     *       that root frame ({@code part.x = snap.x + delta}). Applying it here in model pixels is
     *       what actually keeps the object with the arms as the torso shifts back and forth.</li>
     * </ol>
     */
    private static void emfcompat$applyRootTransform(PoseStack poseStack, Player player) {
        if (!EMFCarryOnClient.isEnabled()) return;

        PoseSnapshot root = PoseManager.getRootPose(player.getUUID());
        if (root != null) {
            poseStack.translate(root.x / 16.0f, root.y / 16.0f, root.z / 16.0f);
            if (root.xRot != 0.0f || root.yRot != 0.0f || root.zRot != 0.0f) {
                poseStack.mulPose(new Quaternionf().rotationZYX(root.zRot, root.yRot, root.xRot));
            }
            if (root.xScale != 1.0f || root.yScale != 1.0f || root.zScale != 1.0f) {
                poseStack.scale(root.xScale, root.yScale, root.zScale);
            }
        }

        if (EMFCarryOnClient.isBodyFollow()) {
            Vector3f delta = PoseManager.getBodyFollowDelta(player.getUUID());
            if (delta != null) {
                poseStack.translate(delta.x / 16.0f, delta.y / 16.0f, delta.z / 16.0f);
            }
        }
    }

    private static void applyBodyDelta(PoseStack poseStack, Player player) {
        if (!EMFCarryOnClient.isEnabled()) return;

        // Body-follow does all of its work at HEAD, in model space — see
        // emfcompat$applyRootTransform. Only the legacy path runs here, where the matrix has
        // already been scaled by 0.6 and re-oriented, hence its own scale and inverted axes.
        if (EMFCarryOnClient.isBodyFollow()) {
            return;
        }

        {
            // Legacy: BodyPartSync translation + rotation of the torso. Model part positions are
            // in pixels (1/16 block); Y and Z are inverted because model space and PoseStack space
            // differ — model Y+ is down / Z+ is back, PoseStack Y+ is up / Z+ is forward.
            if (!BodyPartSync.hasDelta(player.getUUID(), "body")) return;
            Vector3f translation = BodyPartSync.getTranslationDelta(player.getUUID(), "body");
            Vector3f rotation = BodyPartSync.getRotationDelta(player.getUUID(), "body");
            poseStack.translate(
                    translation.x * LEGACY_SCALE,
                    -translation.y * LEGACY_SCALE,
                    -translation.z * LEGACY_SCALE
            );
            // Y rotation is inverted so the carried object tilts in the same direction as the
            // torso. The X sign matches what this Fabric build was tuned with.
            poseStack.mulPose(new Quaternionf().rotationZYX(-rotation.z, -rotation.y, -rotation.x));
        }
    }
}
