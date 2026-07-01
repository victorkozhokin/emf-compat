package strm.emfcompat.supplementaries.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.supplementaries.compat.SupplementariesCompat;

import java.util.UUID;

/**
 * Captures the arm poses set by Supplementaries (Flute, Slingshot, Bubble Blower)
 * at the end of {@link HumanoidModel#setupAnim}.
 * The Core mixins later restore these poses after EMF applies its resource-pack animation,
 * keeping the custom item-hold poses visible.
 */
@Mixin(value = HumanoidModel.class, priority = 900)
public class HumanoidModelMixin {

    private static final String SOURCE = "supplementaries";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureSupplementariesPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                       float ageInTicks, float netHeadYaw, float headPitch,
                                                       CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;

        UUID uuid = player.getUUID();
        if (!SupplementariesCompat.isUsingSupplementariesItem(player)) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        if (EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        PoseManager.savePoses(
                uuid,
                SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm)
        );
    }
}
