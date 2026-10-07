package strm.touchnmotion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.ride.ChestInBow;

/** A boat's chest is drawn in the bow and its paddles further aft ({@link ChestInBow}): placed once the model is posed. */
@Mixin(BoatRenderer.class)
public abstract class BoatChestMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.Map<Boat.Type, com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.client.model.ListModel<Boat>>> boatResources;

    @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ListModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void emfcompat$chestInBow(Boat boat, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ChestInBow.place(boatResources.get(boat.getVariant()).getSecond(), boat);
    }
}
