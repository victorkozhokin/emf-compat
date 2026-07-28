package strm.emfcompat.gliders.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.gliders.GlidersEMFCompat;
import strm.emfcompat.gliders.compat.GlidingState;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures the gliding arm pose at the end of {@link PlayerModel#setupAnim}, so the core can
 * restore it after EMF re-animates the model — keeping the gliding arms visible while the body,
 * head and legs stay under EMF (packs with a flight animation, see {@code NBTMethodMixin}).
 *
 * <p>Exception: while a glider is still opening, the whole body is captured instead, so the
 * mod's deploy animation plays out in full before the flight animation takes over.</p>
 *
 * <p>Priority 2500 runs this after every arm-posing source: Paragliders and Reliable Gliders pose
 * the arms in {@code HumanoidModel.setupAnim} (the {@code super} call), while VC Gliders animates
 * them through player-animation-lib, applied at {@code PlayerModel.setupAnim} RETURN (priority
 * 2000). Capturing here catches all three — which is why VC Gliders (whose arms a
 * {@code HumanoidModel} mixin runs too early to see) now works.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void gliders$captureGliderArmPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch,
                                              CallbackInfo ci) {
        // The first-person pass calls setupAnim with ageInTicks == 0; skip it.
        if (ageInTicks == 0) return;
        if (!(entity instanceof Player player)) return;
        if (player.level() == null) return;

        if (!GlidingState.isGliding(player)) {
            PoseManager.clearPoses(player.getUUID(), GlidingState.SOURCE);
            return;
        }

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;

        // While the glider is still opening, keep the mod's own deploy animation whole: the body
        // and legs swing under the glider, which only reads right if every part is restored, not
        // just the arms. The flight animation is held back for the same span (see GlidingState),
        // so the pack takes over only once the swinging has settled.
        if (GlidingState.isDeploying(player)) {
            Map<String, PoseSnapshot> parts = new HashMap<>();
            parts.put("head", new PoseSnapshot(model.head));
            parts.put("body", new PoseSnapshot(model.body));
            parts.put("left_arm", new PoseSnapshot(model.leftArm));
            parts.put("right_arm", new PoseSnapshot(model.rightArm));
            parts.put("left_leg", new PoseSnapshot(model.leftLeg));
            parts.put("right_leg", new PoseSnapshot(model.rightLeg));
            PoseManager.savePoses(player.getUUID(), GlidingState.SOURCE, null, null, parts);
            return;
        }

        Vector3f bodyBase = GlidersEMFCompat.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;
        PoseManager.savePoses(player.getUUID(), GlidingState.SOURCE,
                new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm), null, bodyBase);
    }
}
