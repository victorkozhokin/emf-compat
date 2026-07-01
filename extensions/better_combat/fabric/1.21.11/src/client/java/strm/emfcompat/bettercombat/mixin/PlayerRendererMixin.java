package strm.emfcompat.bettercombat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

/**
 * Applies the saved Better Combat attack arm pose to the first-person hand model.
 */
@Mixin(AvatarRenderer.class)
public class PlayerRendererMixin {

    @Unique
    private static final String SOURCE = "better_combat";

    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I",
                    shift = At.Shift.BEFORE
            )
    )
    private void emfcompat$restoreArmPoseBeforeHandSubmit(
            PoseStack stack,
            SubmitNodeCollector collector,
            int packedLight,
            Identifier texture,
            ModelPart armPart,
            boolean sleeveVisible,
            CallbackInfo ci
    ) {
        emfcompat$restoreArmPose(armPart);
    }

    private void emfcompat$restoreArmPose(ModelPart armPart) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        SavedPoses saved = PoseManager.getSavedPoses(mc.player.getUUID(), SOURCE);
        if (saved == null) return;

        PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
        PoseSnapshot snapshot;
        if (armPart == model.leftArm) {
            snapshot = saved.leftArm();
        } else if (armPart == model.rightArm) {
            snapshot = saved.rightArm();
        } else {
            return;
        }

        if (snapshot != null) {
            snapshot.applyRotation(armPart);
        }
    }
}
