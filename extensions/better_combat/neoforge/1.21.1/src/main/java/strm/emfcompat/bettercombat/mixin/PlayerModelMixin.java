package strm.emfcompat.bettercombat.mixin;

import net.bettercombat.api.AttackHand;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.bettercombat.EMFCompatBetterCombatMod;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.bettercombat.compat.BetterCombatCompat;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.HashMap;
import java.util.Map;

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

    /** Below this limb-swing amount the player counts as stationary (legs get the attack step). */
    @Unique
    private static final float LEG_MOVE_THRESHOLD = 0.15f;

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureBetterCombatArmPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                      float ageInTicks, float netHeadYaw, float headPitch,
                                                      CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        if (!EMFCompatBetterCombatMod.isEnabled()) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        AttackHand attackHand = BetterCombatCompat.getAttackHand(player);
        if (attackHand == null) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            AttackPauseOverride.tickCooldown(player.getUUID());
            return;
        }

        AttackPauseOverride.markAttackActive(player.getUUID());

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        // Body-follow: attack arm poses keep their shape and follow the torso (bodyBase = the
        // body's position at capture). Rotation-only (legacy): no bodyBase, arms keep only rotation.
        Vector3f bodyBase = EMFCompatBetterCombatMod.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;

        // Optionally restore the legs so an attack's step survives EMF. Two safeguards:
        //  - rotation-only: keeps the legs pivoted at the hip (absolute position detached them);
        //  - only while roughly stationary: when walking/running, leave the legs to EMF so they
        //    keep the walk cycle instead of freezing on the attack step.
        Map<String, PoseSnapshot> parts = null;
        if (EMFCompatBetterCombatMod.isAttackLegs() && limbSwingAmount < LEG_MOVE_THRESHOLD) {
            parts = new HashMap<>();
            parts.put("left_leg", new PoseSnapshot(model.leftLeg, true));
            parts.put("right_leg", new PoseSnapshot(model.rightLeg, true));
        }

        PoseManager.savePoses(
                player.getUUID(), SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                parts,
                bodyBase
        );
    }
}
