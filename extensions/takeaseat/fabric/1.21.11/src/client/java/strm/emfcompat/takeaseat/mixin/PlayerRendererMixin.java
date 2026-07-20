package strm.emfcompat.takeaseat.mixin;

import com.takeaseat.client.TakeASeatClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

@Mixin(PlayerEntityRenderer.class)
public class PlayerRendererMixin {

    @Unique
    private static final String TAKEASEAT$POSE_SOURCE = "takeaseat";

    @Inject(
            method = "renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Lnet/minecraft/client/model/ModelPart;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ModelPart;resetTransform()V",
                    shift = At.Shift.AFTER
            )
    )
    private void takeaseat$restoreArmPoseForFirstPersonHand(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            Identifier texture,
            ModelPart part,
            boolean sleeveVisible,
            CallbackInfo ci
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayerEntity player = mc.player;
        IAnimation layer;
        try {
            layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, TakeASeatClient.SIT_LAYER);
        } catch (Exception e) {
            return;
        }
        if (layer == null || !layer.isActive()) return;

        SavedPoses saved = PoseManager.getSavedPoses(player.getUuid(), TAKEASEAT$POSE_SOURCE);
        if (saved == null || saved.parts() == null) return;

        PlayerEntityModel model = (PlayerEntityModel) ((PlayerEntityRenderer) (Object) this).getModel();
        PoseSnapshot snapshot;
        if (part == model.leftArm) {
            snapshot = saved.parts().get("left_arm");
        } else if (part == model.rightArm) {
            snapshot = saved.parts().get("right_arm");
        } else {
            return;
        }

        if (snapshot == null) return;
        snapshot.applyRotation(part);
    }
}
