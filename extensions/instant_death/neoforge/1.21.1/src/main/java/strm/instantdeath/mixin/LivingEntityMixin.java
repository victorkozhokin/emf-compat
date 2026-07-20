package strm.instantdeath.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.instantdeath.Config;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "die", at = @At("TAIL"))
    private void instantdeath$onDie(DamageSource source, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (!Config.ENABLED.get()) {
            return;
        }
        if (Config.EXCLUDE_PLAYERS.get() && self instanceof Player) {
            return;
        }

        self.level().broadcastEntityEvent(self, (byte) 60);
        self.remove(Entity.RemovalReason.KILLED);
    }

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void instantdeath$onTickDeath(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide()) {
            return;
        }
        if (!Config.ENABLED.get()) {
            return;
        }
        if (Config.EXCLUDE_PLAYERS.get() && self instanceof Player) {
            return;
        }

        ci.cancel();
    }
}
