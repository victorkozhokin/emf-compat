package strm.emfcompat.carryon.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.EMFCarryOnMod;
import strm.emfcompat.carryon.compat.CarryOnCompat;
import strm.emfcompat.core.BodyPartSync;
import strm.emfcompat.core.FirstPersonModelCompat;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;



/**
 * Captures the arm poses set by Carry On at the end of {@link HumanoidModel#setupAnim}.
 * The Core mixins later restore these poses after EMF applies its resource-pack animation,
 * keeping the raised carry pose visible without relying on EMF's part-pause API,
 * which does not behave correctly when EMF's ASM is enabled.
 */
@Mixin(value = HumanoidModel.class, priority = 1100)
public class HumanoidModelMixin {

    private static final String SOURCE = "carry_on";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureCarryOnPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (player.level() == null) return;

        if (!EMFCarryOnMod.isEnabled() || !CarryOnCompat.isCarrying(player)) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            BodyPartSync.clear(player.getUUID());
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        PoseSnapshot leftArm = new PoseSnapshot(model.leftArm);
        PoseSnapshot rightArm = new PoseSnapshot(model.rightArm);

        if (EMFCarryOnMod.isBodyFollow()) {
            // Body-follow: arms keep their exact pose and track the torso; the carried object
            // follows via the core's published body-follow delta (translation only).
            PoseManager.savePoses(player.getUUID(), SOURCE, leftArm, rightArm, null,
                    new Vector3f(model.body.x, model.body.y, model.body.z));
        } else {
            // Legacy: arms restored rotation-only, and the carried object synced to the torso
            // the old way via BodyPartSync (translation + rotation). Capture the base body here;
            // the current body is captured after EMF animate (EMFModelPartRootMixin).
            PoseManager.savePoses(player.getUUID(), SOURCE, leftArm, rightArm);
            BodyPartSync.captureBase(player.getUUID(), "body", model.body);
        }

        // FirstPersonModel hides empty hands in first person. While carrying, force the arms
        // visible again so the raised Carry On pose is actually rendered.
        if (FirstPersonModelCompat.isActive() && !player.isInvisible()) {
            model.leftArm.visible = true;
            model.rightArm.visible = true;
        }
    }
}
