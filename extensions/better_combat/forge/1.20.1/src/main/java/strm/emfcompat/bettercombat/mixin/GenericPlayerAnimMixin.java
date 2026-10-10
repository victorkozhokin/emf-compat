package strm.emfcompat.bettercombat.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.bettercombat.EMFCompatBetterCombatMod;
import strm.emfcompat.bettercombat.compat.PlayerAnimBridge;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic Player Animator baseline (temporary home in the Better Combat addon — will move to core).
 * When Spell Engine is installed and any Player Animator animation is active — a Spell Engine / RPG
 * Series cast, or any other player animation — capture the arm pose (already applied by the library)
 * into the low-priority {@code playeranim_base} source so it survives EMF. Better Combat's own attack
 * capture ({@code better_combat}, priority 0) overrides this during melee via the pose merge.
 *
 * <p>Gated on Spell Engine being present so it never runs idle; arms only for now.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2600)
public class GenericPlayerAnimMixin {

    /** Below this limb-swing amount the player counts as stationary (legs get the cast pose). */
    @Unique
    private static final float LEG_MOVE_THRESHOLD = 0.15f;

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureGenericPlayerAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                    float ageInTicks, float netHeadYaw, float headPitch,
                                                    CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;

        String source = EMFCompatBetterCombatMod.GENERIC_SOURCE;
        if (!EMFCompatBetterCombatMod.isEnabled()
                || !EMFCompatBetterCombatMod.isGenericPlayerAnim()
                || !EMFCompatBetterCombatMod.isSpellEnginePresent()
                || EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())
                || !PlayerAnimBridge.hasActiveAnimation(player)) {
            PoseManager.clearPoses(player.getUUID(), source);
            return;
        }

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;

        // Legs by analogy with the attack-legs feature: rotation-only (stays attached at the hip)
        // and only while roughly stationary, so moving/casting keeps EMF's walk cycle. Shares the
        // "Attack legs" toggle.
        Map<String, PoseSnapshot> parts = null;
        if (EMFCompatBetterCombatMod.isAttackLegs() && limbSwingAmount < LEG_MOVE_THRESHOLD) {
            parts = new HashMap<>();
            parts.put("left_leg", new PoseSnapshot(model.leftLeg, true));
            parts.put("right_leg", new PoseSnapshot(model.rightLeg, true));
        }

        PoseManager.savePoses(player.getUUID(), source,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                parts);
    }
}
