package strm.touchnmotion.mixin.legacy.horsesync;

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
public class PlayerModelMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void horsesync$applyRidingPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                           float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        strm.touchnmotion.horsesync.RidingSeat.pose((net.minecraft.client.model.HumanoidModel<?>) (Object) this, player);
    }
}
