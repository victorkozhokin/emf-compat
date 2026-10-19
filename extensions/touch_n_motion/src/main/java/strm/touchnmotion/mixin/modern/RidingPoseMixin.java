package strm.touchnmotion.mixin.modern;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The horse-riding pose at the end of {@link PlayerModel#setupAnim}: {@link strm.touchnmotion.horsesync.RidingSeat}
 * writes it and captures it into the core's {@code PoseManager}, which restores it after EMF
 * re-animates the model (and onto the armour layer).
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class RidingPoseMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void horsesync$applyRidingPose(net.minecraft.client.renderer.entity.state.AvatarRenderState state, CallbackInfo ci) {
        LivingEntity entity = strm.touchnmotion.interaction.DrawnEntities.of(state);
        if (!(entity instanceof AbstractClientPlayer player)) return;
        strm.touchnmotion.horsesync.RidingSeat.pose((net.minecraft.client.model.HumanoidModel<?>) (Object) this, player);
    }
}
