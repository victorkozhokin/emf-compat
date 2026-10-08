package strm.touchnmotion.mixin.modern;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.boat.AbstractBoatModel;
import net.minecraft.client.model.object.boat.RaftModel;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.ride.BoatSeats;
import strm.touchnmotion.ride.ChestInBow;

import java.util.List;

/**
 * A boat's chest is drawn in the bow and its paddles further aft ({@link ChestInBow}), from
 * 1.21.11 on: placed each time the model is posed. A model with the chest's parts is a chest
 * boat's, whichever boat is being drawn, so the render state need not say; the same call puts the
 * parts back when the option is off.
 */
@Mixin(AbstractBoatModel.class)
public abstract class BoatChestModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/BoatRenderState;)V", at = @At("RETURN"))
    private void touchnmotion$chestInBow(BoatRenderState state, CallbackInfo ci) {
        ModelPart root = ((Model<?>) (Object) this).root();
        if (!root.hasChild("chest_bottom") || !root.hasChild("chest_lid") || !root.hasChild("chest_lock")
                || !root.hasChild("left_paddle") || !root.hasChild("right_paddle")) return;
        ChestInBow.place(List.of(root.getChild("left_paddle"), root.getChild("right_paddle")),
                List.of(root.getChild("chest_bottom"), root.getChild("chest_lid"), root.getChild("chest_lock")),
                BoatSeats.chestClient.getAsBoolean(), (Object) this instanceof RaftModel);
    }
}
