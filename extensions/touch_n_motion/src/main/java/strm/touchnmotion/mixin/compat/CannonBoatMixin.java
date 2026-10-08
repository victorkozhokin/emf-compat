package strm.touchnmotion.mixin.compat;

import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.touchnmotion.ride.CannonInBow;

/** Supplementaries' boat with a cannon, when it is there: where its cannon stands ({@link CannonInBow}); on both sides. */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.entities.CannonBoatEntity", remap = false)
public abstract class CannonBoatMixin {

    @Inject(method = "getCannonOffset", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void touchnmotion$inBow(CallbackInfoReturnable<Vec3> cir) {
        Vec3 own = cir.getReturnValue(), ours = CannonInBow.offset((Boat) (Object) this, own);
        if (ours != own) cir.setReturnValue(ours);
    }
}
