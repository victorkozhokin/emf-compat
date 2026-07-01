package strm.emfcompat.bettercombat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranimcore.animation.Animation;
import net.bettercombat.logic.AnimatedHand;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import strm.emfcompat.bettercombat.compat.MaceAnimationScaler;

/**
 * Reduces the intensity of the mace slam's torso animation.
 *
 * <p>The mace ({@code bettercombat:one_handed_slam}) rotates and displaces the torso very
 * aggressively (forward/backward lean of up to ~60°). This mixin scales those transforms down
 * only when the player is actually holding a mace, leaving other weapons untouched.</p>
 *
 * <p>Player Animation Library uses immutable {@link Animation} records, so the scaling is done
 * when Better Combat loads the animation in {@code playAttackAnimation}.</p>
 */
@Mixin(value = AbstractClientPlayer.class, priority = 1500)
public class BetterCombatMaceTorsoMixin {

    /**
     * Scale factor for torso rotation/translation during a mace slam.
     * {@code 1.0} = original animation, {@code 0.0} = completely disabled.
     */
    private static final float MACE_TORSO_SCALE = 0.35F;

    @WrapOperation(
            method = "playAttackAnimation(Ljava/lang/String;Lnet/bettercombat/logic/AnimatedHand;FF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/zigythebird/playeranim/animation/PlayerAnimResources;getAnimation(Lnet/minecraft/resources/Identifier;)Lcom/zigythebird/playeranimcore/animation/Animation;",
                    remap = false
            ),
            remap = false
    )
    private Animation emfcompat$scaleMaceTorsoAnimation(
            Identifier id,
            Operation<Animation> original,
            String name,
            AnimatedHand animatedHand,
            float length,
            float upswing
    ) {
        Animation animation = original.call(id);
        if (animation == null) {
            return null;
        }

        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (!isMaceAttack(player, animatedHand)) {
            return animation;
        }

        return MaceAnimationScaler.scaleTorsoAndBody(animation, MACE_TORSO_SCALE);
    }

    private static boolean isMaceAttack(AbstractClientPlayer player, AnimatedHand animatedHand) {
        ItemStack stack = animatedHand.isOffHand()
                ? player.getOffhandItem()
                : player.getMainHandItem();
        return stack.is(Items.MACE);
    }
}
