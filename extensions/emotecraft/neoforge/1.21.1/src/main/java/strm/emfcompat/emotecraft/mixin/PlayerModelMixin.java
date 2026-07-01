package strm.emfcompat.emotecraft.mixin;

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
import strm.emfcompat.emotecraft.compat.EmotecraftCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Captures only the body parts that Emotecraft is currently animating.
 * Parts not used by the emote are left untouched so EMF resource-pack animations keep running.
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "emotecraft";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureEmotecraftPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                 float ageInTicks, float netHeadYaw, float headPitch,
                                                 CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        Set<String> animatedParts = EmotecraftCompat.getAnimatedParts(player);
        if (animatedParts.isEmpty()) {
            PoseManager.clearPoses(player, SOURCE);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;
        Map<String, PoseSnapshot> parts = new HashMap<>();

        if (animatedParts.contains("head")) {
            parts.put("head", new PoseSnapshot(model.head));
        }
        if (animatedParts.contains("body")) {
            parts.put("body", new PoseSnapshot(model.body));
        }
        if (animatedParts.contains("left_leg")) {
            parts.put("left_leg", new PoseSnapshot(model.leftLeg));
        }
        if (animatedParts.contains("right_leg")) {
            parts.put("right_leg", new PoseSnapshot(model.rightLeg));
        }

        PoseSnapshot leftArm = animatedParts.contains("left_arm") ? new PoseSnapshot(model.leftArm) : null;
        PoseSnapshot rightArm = animatedParts.contains("right_arm") ? new PoseSnapshot(model.rightArm) : null;

        PoseManager.savePoses(
                player, SOURCE,
                leftArm,
                rightArm,
                parts
        );
    }
}
