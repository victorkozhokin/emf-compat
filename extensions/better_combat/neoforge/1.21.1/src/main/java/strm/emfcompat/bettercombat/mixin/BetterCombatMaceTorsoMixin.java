package strm.emfcompat.bettercombat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.bettercombat.logic.AnimatedHand;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Reduces the intensity of the mace slam's torso animation.
 *
 * <p>The mace ({@code bettercombat:one_handed_slam}) rotates and displaces the torso very
 * aggressively (forward/backward lean of up to ~60°). This mixin scales those transforms down
 * only when the player is actually holding a mace, leaving other weapons untouched.</p>
 */
@Mixin(value = AbstractClientPlayer.class, priority = 1500)
public class BetterCombatMaceTorsoMixin {

    /**
     * Scale factor for torso rotation/translation during a mace slam.
     * {@code 1.0} = original animation, {@code 0.0} = completely disabled.
     */
    private static final float MACE_TORSO_SCALE = 0.35F;

    @Inject(
            method = "playAttackAnimation(Ljava/lang/String;Lnet/bettercombat/logic/AnimatedHand;FF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/kosmx/playerAnim/core/data/KeyframeAnimation$AnimationBuilder;build()Ldev/kosmx/playerAnim/core/data/KeyframeAnimation;",
                    shift = At.Shift.BEFORE,
                    remap = false
            ),
            remap = false
    )
    private void emfcompat$reduceMaceTorsoTilt(
            String name,
            AnimatedHand animatedHand,
            float length,
            float upswing,
            CallbackInfo ci,
            @Local(name = "copy") KeyframeAnimation.AnimationBuilder copy
    ) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (!isMaceAttack(player, animatedHand)) {
            return;
        }

        scaleTorso(copy.torso, MACE_TORSO_SCALE);
        scaleTorso(copy.body, MACE_TORSO_SCALE);
    }

    private static boolean isMaceAttack(AbstractClientPlayer player, AnimatedHand animatedHand) {
        ItemStack stack = animatedHand.isOffHand()
                ? player.getOffhandItem()
                : player.getMainHandItem();
        return stack.is(Items.MACE);
    }

    private static void scaleTorso(KeyframeAnimation.StateCollection part, float scale) {
        if (part == null) {
            return;
        }
        scaleState(part.pitch, scale);
        scaleState(part.yaw, scale);
        scaleState(part.roll, scale);
        scaleState(part.x, scale);
        scaleState(part.y, scale);
        scaleState(part.z, scale);
    }

    private static void scaleState(KeyframeAnimation.StateCollection.State state, float scale) {
        if (state == null) {
            return;
        }
        List<KeyframeAnimation.KeyFrame> frames = state.getKeyFrames();
        for (int i = 0; i < frames.size(); i++) {
            KeyframeAnimation.KeyFrame frame = frames.get(i);
            frames.set(i, new KeyframeAnimation.KeyFrame(
                    frame.tick,
                    frame.value * scale,
                    frame.ease,
                    frame.easingArg
            ));
        }
    }
}
