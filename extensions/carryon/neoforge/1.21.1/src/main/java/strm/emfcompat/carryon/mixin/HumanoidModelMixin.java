package strm.emfcompat.carryon.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.CarryOnRenderState;
import strm.emfcompat.carryon.compat.CarryOnCompat;
import strm.emfcompat.carryon.compat.CorpseCompatGuard;
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
        if (CorpseCompatGuard.isCorpseDummyPlayer(player)) return;

        if (!CarryOnCompat.isCarrying(player)) {
            PoseManager.clearPoses(player, SOURCE);
            CarryOnRenderState.clear(player);
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        PoseSnapshot leftArm = new PoseSnapshot(model.leftArm);
        PoseSnapshot rightArm = new PoseSnapshot(model.rightArm);

        PoseManager.savePoses(player, SOURCE, leftArm, rightArm);

        // FirstPersonModel hides empty hands in first person. While carrying, force the arms
        // visible again so the raised Carry On pose is actually rendered.
        if (FirstPersonModelCompat.isActive() && !player.isInvisible()) {
            model.leftArm.visible = true;
            model.rightArm.visible = true;
        }

        // Capture the base body pose so carried objects can follow torso animation.
        BodyPartSync.captureBase(player, "body", model.body);
    }
}
