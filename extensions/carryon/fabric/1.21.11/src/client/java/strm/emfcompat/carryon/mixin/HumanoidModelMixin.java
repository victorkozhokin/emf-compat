package strm.emfcompat.carryon.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.CarryOnRenderState;
import strm.emfcompat.carryon.compat.CarryOnCompat;
import strm.emfcompat.core.BodyPartSync;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.UUID;

/**
 * Captures the arm poses set by Carry On at the end of {@link HumanoidModel#setupAnim}.
 * The Core mixins later restore these poses after EMF applies its resource-pack animation,
 * keeping the raised carry pose visible.
 */
@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {

    private static final String SOURCE = "carry_on";

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("RETURN")
    )
    private void emfcompat$captureCarryOnPose(HumanoidRenderState state, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerModel model)) return;
        if (!(state instanceof AvatarRenderState avatarState)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(avatarState.id);
        if (!(entity instanceof Player player)) return;

        UUID uuid = player.getUUID();
        if (!CarryOnCompat.isCarrying(player)) {
            PoseManager.clearPoses(uuid, SOURCE);
            CarryOnRenderState.clear(uuid);
            return;
        }

        PoseManager.savePoses(uuid, SOURCE, new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm));

        // Capture the base body pose so carried objects can follow torso animation.
        BodyPartSync.captureBase(uuid, "body", model.body);
    }
}
