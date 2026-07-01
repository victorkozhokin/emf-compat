package strm.emfcompat.quark.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.violetmoon.quark.content.tweaks.client.emote.EmoteBase;
import org.violetmoon.quark.content.tweaks.client.emote.ModelAccessor;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures the Quark emote pose after {@link EmoteBase#update} has run.
 * <p>
 * Fresh Animations / FA PE animate the player's arms and head, so while a Quark emote is
 * active we always pause EMF on those parts. Body and legs are left to EMF/FA PE, which
 * keeps the player walking / idling naturally while the emote controls the gesture.
 * </p>
 */
@Mixin(EmoteBase.class)
public class EmoteBaseMixin {

    @Unique
    private static final String SOURCE = "quark";

    @Inject(method = "update", at = @At("RETURN"), remap = false)
    private void emfcompat$captureQuarkEmotePose(CallbackInfo ci) {
        EmoteBase self = (EmoteBase) (Object) this;
        HumanoidModel<?> model = ((EmoteBaseAccessor) self).emfcompat$getModel();
        Player player = ((EmoteBaseAccessor) self).emfcompat$getPlayer();

        if (!(player instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }

        if (!(model instanceof PlayerModel<?> playerModel)) {
            return;
        }

        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> typedModel = (PlayerModel<AbstractClientPlayer>) playerModel;

        // FA PE animates arms and head, so those are always paused while a Quark emote is playing.
        // Quark only rotates these parts, so we keep EMF's position changes (body-follow idle/walk
        // translation) to stop the head and arms from visually detaching from the torso.
        PoseSnapshot headPose = new PoseSnapshot(typedModel.head, true);
        PoseSnapshot leftArmPose = new PoseSnapshot(typedModel.leftArm);
        PoseSnapshot rightArmPose = new PoseSnapshot(typedModel.rightArm);

        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("head", headPose);

        // In 1.21.1 the hat/headwear layer is a separate vanilla part named "hat".
        // Save it explicitly so EMF does not leave it behind while restoring the head pose.
        PoseSnapshot hatPose = new PoseSnapshot(typedModel.hat, true);
        parts.put("hat", hatPose);
        parts.put("headwear", hatPose);

        // Quark does not normally animate body/legs, but keep the check as a safety net.
        if (self.usesBodyPart(ModelAccessor.BODY)) {
            parts.put("body", new PoseSnapshot(typedModel.body));
        }
        if (self.usesBodyPart(ModelAccessor.LEFT_LEG)) {
            parts.put("left_leg", new PoseSnapshot(typedModel.leftLeg));
        }
        if (self.usesBodyPart(ModelAccessor.RIGHT_LEG)) {
            parts.put("right_leg", new PoseSnapshot(typedModel.rightLeg));
        }

        PoseManager.savePoses(
                clientPlayer.getUUID(),
                SOURCE,
                leftArmPose,
                rightArmPose,
                parts
        );
    }
}
