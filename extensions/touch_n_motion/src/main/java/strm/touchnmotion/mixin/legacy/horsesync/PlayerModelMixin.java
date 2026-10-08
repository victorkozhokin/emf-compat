package strm.touchnmotion.mixin.legacy.horsesync;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.touchnmotion.horsesync.HorseSync;
import strm.touchnmotion.horsesync.RidingPose;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies our horse-riding pose ({@link RidingPose}) at the end of {@link PlayerModel#setupAnim}
 * and captures it into the Core {@code PoseManager}, so the Core mixins restore it after EMF
 * re-animates the model (and onto the armor layer). Only active while the player is riding a
 * horse and the feature is enabled; otherwise the source is cleared so the pose does not stick.
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    private static final String SOURCE = HorseSync.RIDING_SOURCE;

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void horsesync$applyRidingPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                           float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        if (!HorseSync.isEnabled() || !HorseSync.isRidingAnimation()
                || !(player.getVehicle() instanceof AbstractHorse horse)) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        net.minecraft.client.model.HumanoidModel<?> model = (net.minecraft.client.model.HumanoidModel<?>) (Object) this;

        // If another (action) pose owns the arms — a gun aim, a melee swing — yield the whole upper
        // body to it and pose only the leg seat, so the rider aims/attacks naturally while still
        // straddling the horse. The seat itself stays a low-priority base (see HorseSync).
        boolean upperBody = !PoseManager.hasArmPoseExcept(player.getUUID(), SOURCE);

        // Drive the bob from the horse's gait (the passenger's own limb swing is ~0), so the rider
        // is still when the horse stands and bobs when it moves.
        RidingPose.apply(model, horse.walkAnimation.position(), horse.walkAnimation.speed(), upperBody);

        // Capture the posed parts absolutely. The Core restore reapplies these over EMF; sleeves/
        // pants follow their parent parts automatically. When yielding to an action, only the legs
        // are captured; the head is captured only while the crouch is active.
        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("left_leg", new PoseSnapshot(model.leftLeg));
        parts.put("right_leg", new PoseSnapshot(model.rightLeg));
        if (upperBody) {
            parts.put("body", new PoseSnapshot(model.body));
            parts.put("left_arm", new PoseSnapshot(model.leftArm));
            parts.put("right_arm", new PoseSnapshot(model.rightArm));
            if (RidingPose.capturesHead()) {
                parts.put("head", new PoseSnapshot(model.head));
            }
        }

        PoseManager.savePoses(player.getUUID(), SOURCE, null, null, parts);
    }
}
