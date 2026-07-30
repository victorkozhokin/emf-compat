package strm.emfcompat.takeaseat.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.takeaseat.TakeASeatEMFCompat;
import strm.emfcompat.takeaseat.compat.SittingPlusCompat;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures the Sitting+ pose at the end of {@link PlayerModel#setupAnim}, so the core can restore
 * it after EMF re-animates the model.
 *
 * <p>Sitting is a whole-body pose, not just arms: the legs fold and the torso leans, so head,
 * body, arms and legs are all captured. Player Animator applies its layers at {@code setupAnim}
 * RETURN with priority 2000, hence 2500 here.</p>
 *
 * <p>Unlike on 1.21.11+, nothing pauses EMF for this: its built-in pause covers zigythebird's
 * Player Animation Library, while Sitting+ drives kosmx Player Animator. Capture-restore is the
 * only thing keeping the pose visible.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "takeaseat";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void takeaseat$captureSittingPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch,
                                              CallbackInfo ci) {
        // The first-person pass calls setupAnim with ageInTicks == 0; skip it.
        if (ageInTicks == 0) return;
        if (!(entity instanceof AbstractClientPlayer player)) return;

        if (!TakeASeatEMFCompat.isEnabled() || !SittingPlusCompat.isSitting(player)) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("head", new PoseSnapshot(model.head, true));
        parts.put("body", new PoseSnapshot(model.body));
        parts.put("left_leg", new PoseSnapshot(model.leftLeg));
        parts.put("right_leg", new PoseSnapshot(model.rightLeg));

        Vector3f bodyBase = TakeASeatEMFCompat.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;
        PoseManager.savePoses(player.getUUID(), SOURCE,
                new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm), parts, bodyBase);
    }
}
