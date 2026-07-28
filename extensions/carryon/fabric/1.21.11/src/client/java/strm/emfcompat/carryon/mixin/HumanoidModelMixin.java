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
import org.joml.Vector3f;
import strm.emfcompat.carryon.EMFCarryOnClient;

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
        if (!EMFCarryOnClient.isEnabled() || !CarryOnCompat.isCarrying(player)) {
            PoseManager.clearPoses(uuid, SOURCE);
            BodyPartSync.clear(uuid);
            return;
        }

        if (EMFCarryOnClient.isBodyFollow()) {
            // Body-follow: arms keep their exact pose and track the torso; the carried object
            // follows via the core's published body-follow delta (translation only).
            PoseManager.savePoses(uuid, SOURCE,
                    new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm), null,
                    new Vector3f(model.body.x, model.body.y, model.body.z));
        } else {
            // Legacy: arms restored rotation-only, and the carried object synced to the torso
            // the old way via BodyPartSync (translation + rotation). Capture the base body here;
            // the current body is captured after EMF animate (EMFModelPartRootMixin).
            PoseManager.savePoses(uuid, SOURCE, new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm));
            BodyPartSync.captureBase(uuid, "body", model.body);
        }
    }
}
