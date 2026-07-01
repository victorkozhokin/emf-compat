package strm.emfcompat.bettercombat.mixin;

import net.bettercombat.api.AttackHand;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.bettercombat.compat.BetterCombatCompat;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures the arms and torso jacket during an active Better Combat attack.
 * The body, head and legs remain under EMF's control so that resource-pack animations there keep running.
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "better_combat";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureBetterCombatArmPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                      float ageInTicks, float netHeadYaw, float headPitch,
                                                      CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        AttackHand attackHand = BetterCombatCompat.getAttackHand(player);
        if (attackHand == null) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("jacket", new PoseSnapshot(model.jacket));

        PoseManager.savePoses(
                player.getUUID(), SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                parts
        );
    }
}
