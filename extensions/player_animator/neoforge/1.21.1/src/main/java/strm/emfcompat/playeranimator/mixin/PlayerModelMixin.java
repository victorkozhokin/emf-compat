package strm.emfcompat.playeranimator.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.playeranimator.compat.PlayerAnimatorCompat;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures the full-body pose after Player Animator has applied its animation in
 * {@link PlayerModel#setupAnim}. EMF Compat core then restores these poses after EMF runs.
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "player_animator";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$capturePlayerAnimatorPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                     float ageInTicks, float netHeadYaw, float headPitch,
                                                     CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        if (!PlayerAnimatorCompat.isAnimationActive(player)) {
            PoseManager.clearPoses(player, SOURCE);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("head", new PoseSnapshot(model.head));
        parts.put("body", new PoseSnapshot(model.body));
        parts.put("left_arm", new PoseSnapshot(model.leftArm));
        parts.put("right_arm", new PoseSnapshot(model.rightArm));
        parts.put("left_leg", new PoseSnapshot(model.leftLeg));
        parts.put("right_leg", new PoseSnapshot(model.rightLeg));

        PoseManager.savePoses(
                player, SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                parts
        );
    }
}
