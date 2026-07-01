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

/**
 * Syncs the carried block/entity in third-person view with the EMF-animated torso pose
 * and registers carried entities for EMF's vanilla-model condition.
 */
@Mixin(CarryRenderHelper.class)
public class CarryRenderHelperMixin {

    private static final float BODY_PART_SCALE = 0.6f / 16.0f;

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
    }

    private static void applyBodyDelta(PoseStack poseStack, Player player) {
        if (!BodyPartSync.hasDelta(player.getUUID(), "body")) return;

        Vector3f translation = BodyPartSync.getTranslationDelta(player.getUUID(), "body");
        Vector3f rotation = BodyPartSync.getRotationDelta(player.getUUID(), "body");

        // Model part positions are in pixels (1/16 block). CarryOn scales the matrix by 0.6.
        // Y and Z are inverted because model-space and PoseStack space differ:
        // model Y+ is down, PoseStack Y+ is up; model Z+ is back, PoseStack Z+ is forward.
        poseStack.translate(
                translation.x * BODY_PART_SCALE,
                -translation.y * BODY_PART_SCALE,
                -translation.z * BODY_PART_SCALE
        );

        // Y rotation is inverted so the carried object tilts in the same direction as the torso.
        poseStack.mulPose(new Quaternionf().rotationZYX(rotation.z, -rotation.y, rotation.x));
    }
}
