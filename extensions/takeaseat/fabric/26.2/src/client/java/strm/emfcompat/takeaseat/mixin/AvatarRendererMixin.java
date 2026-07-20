package strm.emfcompat.takeaseat.mixin;

import com.takeaseat.client.TakeASeatClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Unique
    private static final String TAKEASEAT$POSE_SOURCE = "takeaseat";

    @Inject(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;resetPose()V",
                    shift = At.Shift.AFTER
            )
    )
    private void takeaseat$restoreArmPoseForFirstPersonHand(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            Identifier texture,
            ModelPart part,
            boolean sleeveVisible,
            CallbackInfo ci
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayer player = mc.player;
        IAnimation layer;
        try {
            layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, TakeASeatClient.SIT_LAYER);
        } catch (Exception e) {
            return;
        }
        if (layer == null || !layer.isActive()) return;

        SavedPoses saved = PoseManager.getSavedPoses(player.getUUID(), TAKEASEAT$POSE_SOURCE);
        if (saved == null || saved.parts() == null) return;

        PoseSnapshot snapshot;
        if (part == ((AvatarRenderer<?>)(Object)this).getModel().leftArm) {
            snapshot = saved.parts().get("left_arm");
        } else if (part == ((AvatarRenderer<?>)(Object)this).getModel().rightArm) {
            snapshot = saved.parts().get("right_arm");
        } else {
            return;
        }

        if (snapshot == null) return;
        snapshot.applyRotation(part);
    }
}
