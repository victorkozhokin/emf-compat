package strm.emfcompat.animationadditions.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.animationadditions.gesture.AnimalCare;
import strm.emfcompat.animationadditions.gesture.HandTo;

/**
 * What the game accepted of this player's clicks, for the gestures that follow a real action:
 * feeding, milking and shearing an animal, dressing an armour stand, planting a seed. The item
 * is read before the click - a bucket is milk and a last seed is gone after it.
 */
@Mixin(MultiPlayerGameMode.class)
public class GestureInteractionMixin {
    @Unique
    private ItemStack emfcompat$used = ItemStack.EMPTY;

    @Inject(method = "interact", at = @At("HEAD"))
    private void emfcompat$before(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        emfcompat$used = player.getItemInHand(hand).copy();
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void emfcompat$cared(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction() || !(player instanceof AbstractClientPlayer client)) return;
        ItemStack used = emfcompat$used;
        boolean main = hand == InteractionHand.MAIN_HAND;
        if (used.is(Items.SHEARS) && target instanceof Shearable) AnimalCare.done(client, target, AnimalCare.SHEAR, main);
        else if (used.is(Items.BUCKET) && (target instanceof Cow || target instanceof Goat)) AnimalCare.done(client, target, AnimalCare.MILK, main);
        else if (target instanceof Animal animal && !used.isEmpty() && animal.isFood(used)) AnimalCare.done(client, target, AnimalCare.FEED, main);
    }

    @Inject(method = "interactAt", at = @At("RETURN"))
    private void emfcompat$dressed(Player player, Entity target, EntityHitResult ray, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && target instanceof ArmorStand && player instanceof AbstractClientPlayer client)
            HandTo.done(client, HandTo.STAND, ray.getLocation(), hand == InteractionHand.MAIN_HAND);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void emfcompat$beforeBlock(LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        emfcompat$used = player.getItemInHand(hand).copy();
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void emfcompat$planted(LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction() || !(emfcompat$used.getItem() instanceof BlockItem seed)) return;
        if (HandTo.plants(seed))
            HandTo.done(player, HandTo.SEED, result.getLocation(), hand == InteractionHand.MAIN_HAND);
    }
}
