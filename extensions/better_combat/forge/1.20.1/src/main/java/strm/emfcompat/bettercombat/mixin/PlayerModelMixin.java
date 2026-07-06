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
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.bettercombat.compat.BetterCombatCompat;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.UUID;

/**
 * Captures the arms during an active Better Combat attack.
 * The body, head, legs and jacket remain under EMF's control so that resource-pack animations there keep running.
 *
 * <p>Player Animator applies its animation at {@code PlayerModel.setupAnim} RETURN with priority 2000,
 * so this mixin runs at priority 2500 to capture the pose <em>after</em> Better Combat has modified the model.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
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
        UUID uuid = player.getUUID();
        if (attackHand == null) {
            AttackPauseOverride.tickCooldown(uuid);
            // Keep the captured arm pose for a few frames after the attack ends so that
            // first-person hands and EMF unpausing don't snap back instantly.
            if (!AttackPauseOverride.isUnpaused(uuid)) {
                PoseManager.clearPoses(uuid, SOURCE);
            }
            return;
        }

        AttackPauseOverride.markAttackActive(uuid);

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        PoseManager.savePoses(
                player.getUUID(), SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm)
        );
    }
}
