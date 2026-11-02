package strm.touchnmotion.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import strm.touchnmotion.interaction.Anchor;

/**
 * The walk animation's step on a Sable sub-level - in a jump off its deck too - counted across the
 * deck, not at the craft's speed ({@link Anchor#walkDistance}).
 */
@Mixin(LivingEntity.class)
public abstract class SubLevelWalkMixin {
    @ModifyVariable(method = "updateWalkAnimation", at = @At("HEAD"), argsOnly = true, require = 0)
    private float touchnmotion$walkOnDeck(float distance) {
        if (!((Object) this instanceof Player player) || !player.level().isClientSide()) return distance;
        try {
            return Anchor.walkDistance(player, distance);
        } catch (RuntimeException e) {
            Anchor.failed(e);
            return distance;
        }
    }
}
