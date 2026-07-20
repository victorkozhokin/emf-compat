package strm.emfcompat.bettercombat.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.bettercombat.compat.BetterCombatCompat;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

/**
 * Overrides the arm pose (rotation + translation) used for first-person held-item positioning while a Better Combat attack is active.
 *
 * <p>NEA injects into {@code translateToHand} (Yarn: {@code setArmAngle}) at HEAD to restore its saved arm pose
 * for items. We inject after it (higher priority) so that during a BC attack the item follows the BC attack pose
 * instead of the NEA pose. Without this the visible arm uses BC while the held item stays at the NEA position,
 * causing the item to levitate.</p>
 */
@Mixin(value = HumanoidModel.class, priority = 1500)
public class HumanoidModelMixin {

    @Unique
    private static final String SOURCE = "better_combat";

    @Inject(
            method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD")
    )
    private void emfcompat$restoreArmPoseForHeldItem(
            HumanoidRenderState state,
            HumanoidArm arm,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            CallbackInfo ci
    ) {
        if (!((Object) this instanceof PlayerModel)) {
            return;
        }
        PlayerModel model = (PlayerModel) (Object) this;
        if (!(state instanceof AvatarRenderState avatarState)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Entity entity = mc.level.getEntity(avatarState.id);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        // Only override if Better Combat is actually controlling the arm right now.
        if (!BetterCombatCompat.isAttackActive(player)) {
            return;
        }

        SavedPoses saved = PoseManager.getSavedPoses(player.getUUID(), SOURCE);
        if (saved == null) {
            return;
        }

        PoseSnapshot snapshot = arm == HumanoidArm.LEFT ? saved.leftArm() : saved.rightArm();
        if (snapshot == null) {
            return;
        }

        snapshot.apply(arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm);
    }
}
