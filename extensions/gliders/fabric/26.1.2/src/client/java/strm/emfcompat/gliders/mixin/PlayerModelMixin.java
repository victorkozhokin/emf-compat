package strm.emfcompat.gliders.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.gliders.GlidersEMFCompat;
import strm.emfcompat.gliders.compat.GlidingState;

import java.util.UUID;

/**
 * Captures the gliding arm pose at the end of {@link PlayerModel#setupAnim}, so the core can
 * restore it after EMF re-animates the model — keeping the gliding arms visible while the body,
 * head and legs stay under EMF.
 *
 * <p>Priority 2500 runs this after the arm-posing source: Reliable Gliders poses the arms in
 * {@code HumanoidModel.setupAnim} (the {@code super} call), so capturing at
 * {@code PlayerModel.setupAnim} RETURN sees the final pose.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("RETURN")
    )
    private void gliders$captureGliderArmPose(AvatarRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player player)) return;

        UUID uuid = player.getUUID();
        if (!GlidingState.isGliding(player)) {
            PoseManager.clearPoses(uuid, GlidingState.SOURCE);
            return;
        }

        PlayerModel model = (PlayerModel) (Object) this;

        Vector3f bodyBase = GlidersEMFCompat.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;
        PoseManager.savePoses(uuid, GlidingState.SOURCE,
                new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm), null, bodyBase);
    }
}
