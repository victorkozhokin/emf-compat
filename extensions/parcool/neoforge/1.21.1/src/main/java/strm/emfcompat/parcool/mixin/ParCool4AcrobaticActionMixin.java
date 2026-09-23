package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.action.impl.TrickJump;
import com.alrex.parcool.common.action.impl.Vault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.parcool.EMFCompatParCoolMod;
import strm.emfcompat.parcool.compat.ParCoolHandsFull;

/**
 * Prevents dodges/dashes, trick-jump flips and vaults while both hands carry an external load.
 *
 * <p>A vault is a hand plant on the obstacle: with a block or a mob in both arms there is nothing
 * to plant, and the carried object would pass through the obstacle it is vaulted over.</p>
 */
@Mixin({Dodge.class, TrickJump.class, Vault.class})
public abstract class ParCool4AcrobaticActionMixin {

    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void emfcompat$requireFreeHands(CallbackInfoReturnable<Boolean> cir) {
        ParCool4ActionAccessor action = (ParCool4ActionAccessor) this;
        if (EMFCompatParCoolMod.isEnabled()
                && ParCoolHandsFull.handsFull(action.emfcompat$getParkourability().player())) {
            cir.setReturnValue(false);
        }
    }
}
