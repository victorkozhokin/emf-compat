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
import strm.emfcompat.carryon.EMFCarryOnMod;
import strm.emfcompat.core.BodyPartSync;
import strm.emfcompat.core.PoseManager;
import tschipp.carryon.client.render.CarryRenderHelper;

/**
 * Keeps the carried block/entity attached to the hands and registers carried entities for
 * EMF's vanilla-model condition.
 *
 * <p>In body-follow mode the carried object is shifted by the exact translation the core
 * applies to the player's arms this frame ({@link PoseManager#getBodyFollowDelta}). Because
 * the object and the arms move by the same amount, they stay in sync. Only the translation is
 * applied — no rotation delta — so the object no longer swings independently of the raised arm
 * pose.</p>
 *
 * <p>Carry On's own fixed sneak offset ({@code doSneakCheck} → {@code translate(0, -0.4, 0)})
 * is left intact: it compensates the object for the renderer lowering the whole model on
 * crouch, which the body-follow delta (a model-space body-part movement) does not cover — the
 * two are complementary, so the object needs both to stay in the hands while crouching.</p>
 */
@Mixin(value = CarryRenderHelper.class, remap = false)
public class CarryRenderHelperMixin {

    // Body-follow: our translate runs inside Carry On's matrix, already scaled by 0.6, so we
    // divide by 0.6 to make the object's world movement match the arms' (raw model pixels).
    private static final float BODY_FOLLOW_SCALE = 1.0f / 16.0f / 0.6f;

    // Legacy: original BodyPartSync scale (0.6/16), kept as-is to reproduce the old look.
    private static final float LEGACY_SCALE = 0.6f / 16.0f;

    @Inject(
            method = "applyBlockTransformations(Lnet/minecraft/world/entity/player/Player;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/Block;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ltschipp/carryon/client/render/CarryRenderHelper;applyGeneralTransformations(Lnet/minecraft/world/entity/player/Player;FLcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void emfcompat$syncCarriedBlockWithBody(
            Player player,
            float partialTicks,
            PoseStack poseStack,
            Block block,
            CallbackInfo ci
    ) {
        applyBodyDelta(poseStack, player);
    }

    @Inject(
            method = "applyEntityTransformations(Lnet/minecraft/world/entity/player/Player;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ltschipp/carryon/client/render/CarryRenderHelper;applyGeneralTransformations(Lnet/minecraft/world/entity/player/Player;FLcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void emfcompat$syncCarriedEntityWithBody(
            Player player,
            float partialTicks,
            PoseStack poseStack,
            Entity entity,
            CallbackInfo ci
    ) {
        applyBodyDelta(poseStack, player);
    }

    @Inject(
            method = "applyEntityTransformations(Lnet/minecraft/world/entity/player/Player;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD")
    )
    private static void emfcompat$markCarriedEntity(
            Player player,
            float partialTicks,
            PoseStack poseStack,
            Entity entity,
            CallbackInfo ci
    ) {
        if (!EMFCarryOnMod.isEnabled()) return;
        CarryOnRenderState.markCarried(entity);
    }

    private static void applyBodyDelta(PoseStack poseStack, Player player) {
        if (!EMFCarryOnMod.isEnabled()) return;

        // Model part positions are in pixels (1/16 block). Y and Z are inverted because model
        // space and PoseStack space differ: model Y+ is down / Z+ is back, PoseStack Y+ is up /
        // Z+ is forward.
        if (EMFCarryOnMod.isBodyFollow()) {
            // Body-follow: shift by the same delta the core applied to the arms (translation only).
            Vector3f delta = PoseManager.getBodyFollowDelta(player.getUUID());
            if (delta == null) return;
            poseStack.translate(
                    delta.x * BODY_FOLLOW_SCALE,
                    -delta.y * BODY_FOLLOW_SCALE,
                    -delta.z * BODY_FOLLOW_SCALE
            );
        } else {
            // Legacy: BodyPartSync translation + rotation of the torso.
            if (!BodyPartSync.hasDelta(player.getUUID(), "body")) return;
            Vector3f translation = BodyPartSync.getTranslationDelta(player.getUUID(), "body");
            Vector3f rotation = BodyPartSync.getRotationDelta(player.getUUID(), "body");
            poseStack.translate(
                    translation.x * LEGACY_SCALE,
                    -translation.y * LEGACY_SCALE,
                    -translation.z * LEGACY_SCALE
            );
            // Y rotation is inverted so the carried object tilts in the same direction as the
            // torso. The X sign differs from the NeoForge module on purpose — this is the value
            // the Forge build was tuned with, and the legacy path exists to reproduce it.
            poseStack.mulPose(new Quaternionf().rotationZYX(-rotation.z, -rotation.y, -rotation.x));
        }
    }
}
