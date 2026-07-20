package strm.emfcompat.bettercombat.mixin;

import net.bettercombat.api.AttackHand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;

import java.util.UUID;
import strm.emfcompat.bettercombat.compat.BetterCombatCompat;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

/**
 * Captures the arms during an active Better Combat attack.
 * The body, head, legs and jacket remain under EMF's control so that resource-pack animations there keep running.
 *
 * <p>Player Animation Library applies its animation at {@code PlayerModel.setupAnim} RETURN with priority 2001,
 * so this mixin runs at priority 2500 to capture the pose <em>after</em> Better Combat has modified the model.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "better_combat";

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("RETURN")
    )
    private void emfcompat$captureBetterCombatArmPose(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof AbstractClientPlayer player)) return;

        UUID uuid = player.getUUID();
        AttackHand attackHand = BetterCombatCompat.getAttackHand(player);
        if (attackHand == null) {
            PoseManager.clearPoses(uuid, SOURCE);
            AttackPauseOverride.tickCooldown(uuid);
            return;
        }

        AttackPauseOverride.markAttackActive(uuid);
        PoseManager.savePoses(
                player.getUUID(), SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm)
        );
    }
}
