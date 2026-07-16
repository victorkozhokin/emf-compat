package strm.emfcompat.create.mixin;

import dev.leo.sableplayerragdoll.neoforge.client.RagdollGrabState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;



@Mixin(value = HumanoidModel.class, priority = 1100)
public class HumanoidModelRagdollMixin {

    private static final String SOURCE = "create_ragdoll";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompatCreate$onSetupAnimRagdoll(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (player.level() == null) return;
        if (Minecraft.getInstance().isPaused()) return;

        if (RagdollGrabState.isGrabbing(player.getUUID())) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            PoseManager.savePoses(
                    player.getUUID(),
                    SOURCE,
                    new PoseSnapshot(model.leftArm),
                    new PoseSnapshot(model.rightArm)
            );
        } else {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
        }
    }
}
