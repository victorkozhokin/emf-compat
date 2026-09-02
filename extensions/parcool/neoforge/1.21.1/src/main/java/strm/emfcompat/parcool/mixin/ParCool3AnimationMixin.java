package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.attachment.client.Animation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.parcool.compat.ParCoolPose;

/**
 * Capture path for ParCool 3.4.x, which poses the model from its own {@code PlayerModel}
 * mixin: it builds the pose at HEAD and, for animations that own the whole body, cancels
 * {@code setupAnim} outright. A RETURN injection on {@code setupAnim} would therefore miss
 * exactly the poses that matter most, so the capture hangs off ParCool's own two entry
 * points instead, where both paths are visible.
 *
 * <p>ParCool 3 does not report which parts an animation touched, so each entry point passes
 * the set its mode implies: a cancelled {@code setupAnim} means ParCool built the pose from a
 * reset model and owns all of it, while the post pass only adjusts a vanilla pose, so the
 * torso is left to the resource pack.</p>
 */
@Mixin(Animation.class)
public class ParCool3AnimationMixin {

    @Inject(method = "animatePre", at = @At("RETURN"))
    private void emfcompat$captureOwnedPose(Player player, PlayerModelTransformer transformer,
                                            CallbackInfoReturnable<Boolean> cir) {
        // true means ParCool is about to cancel setupAnim: the pose it just built is final,
        // and its own post pass will not run for this frame either.
        if (!cir.getReturnValueZ()) return;
        ParCoolPose.capture(player, transformer.getRawModel(), ParCoolPose.WHOLE_MODEL);
    }

    @Inject(method = "animatePost", at = @At("RETURN"))
    private void emfcompat$captureAdjustedPose(Player player, PlayerModelTransformer transformer,
                                               CallbackInfo ci) {
        Animation animation = (Animation) (Object) this;

        // No animator means the passive animation ran - a subtle idle adjustment that is not
        // worth taking the pack's own idle away for, so nothing is captured.
        if (!animation.hasAnimator() || animation.shouldCancelAnimation(player)) {
            ParCoolPose.clear(player);
            return;
        }
        ParCoolPose.capture(player, transformer.getRawModel(), ParCoolPose.LIMBS_AND_HEAD);
    }
}
