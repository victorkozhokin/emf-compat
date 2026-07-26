package strm.emfcompat.gliders.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.gliders.compat.GlidingState;

/**
 * Captures the paragliding arm pose at the end of {@link HumanoidModel#setupAnim}.
 * Paragliders applies its arm pose from its own PlayerModel mixin, so the
 * priority 1100 makes this capture run after it. The Core mixins later restore
 * these poses after EMF applies its resource-pack animation, keeping the
 * gliding arm pose visible without relying on EMF's part-pause API, which does
 * not behave correctly when EMF's ASM is enabled.
 *
 * Only the arms are captured and restored (they hold the paraglider above the
 * head); the head, body and legs stay under EMF control so packs with a
 * creative-flight animation (see {@code NBTMethodMixin}) can animate them
 * while gliding.
 */
@Mixin(value = HumanoidModel.class, priority = 1100)
public class HumanoidModelMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void gliders$captureParaglidingPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (player.level() == null) return;

        // Arm poses are only captured for Tictim's Paragliders: it poses the
        // arms inside setupAnim. VC Gliders animates the arms through
        // player-animation-lib keyframes at render time, so there is nothing
        // to capture here for it.
        if (!GlidingState.isParagliding(player)) {
            PoseManager.clearPoses(player.getUUID(), GlidingState.SOURCE);
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;

        PoseManager.savePoses(player.getUUID(), GlidingState.SOURCE,
                new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm));
    }
}
