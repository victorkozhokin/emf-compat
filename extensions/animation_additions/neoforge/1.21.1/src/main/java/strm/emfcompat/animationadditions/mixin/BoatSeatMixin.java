package strm.emfcompat.animationadditions.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.animationadditions.ride.BoatSeats;

/** The second of two players in a boat sits in the bow and faces the rower, and the rider of a boat with a chest sits aft ({@link BoatSeats}); on both sides. */
@Mixin(Boat.class)
public abstract class BoatSeatMixin {

    @Inject(method = "getPassengerAttachmentPoint", at = @At("RETURN"), cancellable = true)
    private void emfcompat$bowSeat(Entity entity, EntityDimensions dimensions, float scale, CallbackInfoReturnable<Vec3> cir) {
        Boat boat = (Boat) (Object) this;
        if (BoatSeats.chestInBow(boat)) {
            cir.setReturnValue(new Vec3(0.0, cir.getReturnValue().y, -BoatSeats.AFT).yRot(-boat.getYRot() * Mth.DEG_TO_RAD));
            return;
        }
        if (BoatSeats.rowsWithBow(boat, entity)) {
            cir.setReturnValue(new Vec3(0.0, cir.getReturnValue().y, 0.0));
            return;
        }
        if (!BoatSeats.inBow(boat, entity)) return;
        cir.setReturnValue(new Vec3(0.0, cir.getReturnValue().y + BoatSeats.RAISED, BoatSeats.BOW).yRot(-boat.getYRot() * Mth.DEG_TO_RAD));
    }

    /** As the game's own, about the way to the stern instead of the way to the bow. */
    @Inject(method = "clampRotation", at = @At("HEAD"), cancellable = true)
    private void emfcompat$faceRower(Entity entity, CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (!BoatSeats.inBow(boat, entity)) return;
        float aft = boat.getYRot() + 180f;
        entity.setYBodyRot(aft);
        float off = Mth.wrapDegrees(entity.getYRot() - aft);
        float kept = Mth.clamp(off, -BoatSeats.TURN, BoatSeats.TURN);
        entity.yRotO += kept - off;
        entity.setYRot(entity.getYRot() + kept - off);
        entity.setYHeadRot(entity.getYRot());
        ci.cancel();
    }
}
