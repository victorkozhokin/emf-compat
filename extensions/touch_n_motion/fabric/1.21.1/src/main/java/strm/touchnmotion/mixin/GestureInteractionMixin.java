package strm.touchnmotion.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.touchnmotion.gesture.AnimalCare;
import strm.touchnmotion.gesture.Gesture;
import strm.touchnmotion.gesture.HandTo;

/**
 * What the game accepted of this player's clicks, for the gestures that follow a real action:
 * feeding, milking and shearing an animal, dressing an armour stand, planting a seed. The item
 * is read before the click - a bucket is milk and a last seed is gone after it.
 */
@Mixin(MultiPlayerGameMode.class)
public class GestureInteractionMixin {
    @Unique
    private ItemStack emfcompat$used = ItemStack.EMPTY;

    /**
     * With the option on, a click that one of the gestures answers is held back: the hand goes to its
     * place first and the click is let through when it gets there (it comes this way again, marked).
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void emfcompat$holdCare(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (Gesture.replaying() || !Gesture.actsAfter() || !(player instanceof AbstractClientPlayer client)) return;
        int kind = AnimalCare.kindOf(player.getItemInHand(hand), target);
        MultiPlayerGameMode game = (MultiPlayerGameMode) (Object) this;
        if (kind >= 0 && AnimalCare.hold(client, target, kind, hand == InteractionHand.MAIN_HAND,
                () -> Gesture.replay(() -> game.interact(player, target, hand)))) cir.setReturnValue(InteractionResult.CONSUME);
    }

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void emfcompat$holdStand(Player player, Entity target, EntityHitResult ray, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (Gesture.replaying() || !Gesture.actsAfter() || !(target instanceof ArmorStand) || !(player instanceof AbstractClientPlayer client)) return;
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof ArmorItem) && !(held.isEmpty() && hand == InteractionHand.MAIN_HAND)) return;
        MultiPlayerGameMode game = (MultiPlayerGameMode) (Object) this;
        if (HandTo.hold(client, HandTo.STAND, ray.getLocation(), hand == InteractionHand.MAIN_HAND,
                () -> Gesture.replay(() -> game.interactAt(player, target, ray, hand)))) cir.setReturnValue(InteractionResult.CONSUME);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void emfcompat$holdSeed(LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        if (Gesture.replaying() || !Gesture.actsAfter() || result.getDirection() != Direction.UP) return;
        if (!(player.getItemInHand(hand).getItem() instanceof BlockItem seed) || !HandTo.plants(seed)) return;
        if (!seed.getBlock().defaultBlockState().canSurvive(player.level(), result.getBlockPos().above())
                || !player.level().getBlockState(result.getBlockPos().above()).isAir()) return;
        MultiPlayerGameMode game = (MultiPlayerGameMode) (Object) this;
        if (HandTo.hold(player, HandTo.SEED, result.getLocation(), hand == InteractionHand.MAIN_HAND,
                () -> Gesture.replay(() -> game.useItemOn(player, hand, result)))) cir.setReturnValue(InteractionResult.CONSUME);
    }

    /** A block's use the game accepted: the screen that follows is that block's. */
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void emfcompat$usedBlock(LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()) strm.touchnmotion.net.ClientHands.used(result.getBlockPos());
    }

    @Inject(method = "interact", at = @At("HEAD"))
    private void emfcompat$before(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        emfcompat$used = player.getItemInHand(hand).copy();
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void emfcompat$cared(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction() || !(player instanceof AbstractClientPlayer client)) return;
        ItemStack used = emfcompat$used;
        boolean main = hand == InteractionHand.MAIN_HAND;
        int kind = used.is(Items.SHEARS) && target instanceof Shearable ? AnimalCare.SHEAR
                : used.is(Items.BUCKET) && (target instanceof Cow || target instanceof Goat) ? AnimalCare.MILK
                : target instanceof Animal animal && !used.isEmpty() && animal.isFood(used) ? AnimalCare.FEED : -1;
        if (kind < 0) return;
        AnimalCare.done(client, target, kind, main);
        // The kinds are the packet's own numbers: FEED, MILK, SHEAR.
        strm.touchnmotion.net.ClientHands.act(kind, target, null, main);
    }

    @Inject(method = "interactAt", at = @At("RETURN"))
    private void emfcompat$dressed(Player player, Entity target, EntityHitResult ray, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && target instanceof ArmorStand && player instanceof AbstractClientPlayer client)
        {
            HandTo.done(client, HandTo.STAND, ray.getLocation(), hand == InteractionHand.MAIN_HAND);
            strm.touchnmotion.net.ClientHands.act(strm.touchnmotion.net.HandsAct.STAND, target, ray.getLocation(), hand == InteractionHand.MAIN_HAND);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void emfcompat$beforeBlock(LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        emfcompat$used = player.getItemInHand(hand).copy();
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void emfcompat$planted(LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction() || !(emfcompat$used.getItem() instanceof BlockItem seed)) return;
        if (HandTo.plants(seed))
        {
            HandTo.done(player, HandTo.SEED, result.getLocation(), hand == InteractionHand.MAIN_HAND);
            strm.touchnmotion.net.ClientHands.act(strm.touchnmotion.net.HandsAct.SEED, null, result.getLocation(), hand == InteractionHand.MAIN_HAND);
        }
    }
}
