package strm.emfcompat.create.flight;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Detects Create Stuff 'N Additions state (jetpack flight, hooked grappling whisk, carried
 * block-picker) for the animated player.
 *
 * <p>Fully data-driven: it reads item tags, item ids and synced {@code CUSTOM_DATA} components,
 * so it references no create_sa classes and is a safe no-op when the mod is absent (the tag is
 * empty and the item ids resolve to nothing).</p>
 */
public final class CreateSaCompat {

    private static final TagKey<Item> JETPACK_TAG =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("create_sa", "jetpack"));
    private static final ResourceLocation WHISK_ID =
            ResourceLocation.fromNamespaceAndPath("create_sa", "grapplin_whisk");
    private static final ResourceLocation PICKER_ID =
            ResourceLocation.fromNamespaceAndPath("create_sa", "block_picker");

    private CreateSaCompat() {
    }

    /**
     * Returns the hand holding a Grappling Whisk that is currently hooked, or {@code null}.
     * Main hand only — SA items do not function off-hand, and capturing there froze the other
     * arm in a half-vanilla pose.
     */
    public static InteractionHand getHookedWhiskHand(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (emfcompat$isItem(stack, WHISK_ID) && emfcompat$getBool(stack, "tagHooked")) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    /** Returns {@code true} if the player holds a Block Picker carrying a block (main hand only). */
    public static boolean isCarryingBlock(Player player) {
        ItemStack stack = player.getMainHandItem();
        return emfcompat$isItem(stack, PICKER_ID) && emfcompat$getBool(stack, "tagAction");
    }

    /**
     * Returns {@code true} if the player wears a Stuff 'N Additions jetpack whose
     * {@code isFlying} flag is set, and the player is airborne.
     */
    public static boolean isJetpackFlying(Player player) {
        if (player.onGround()) return false;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(JETPACK_TAG)) return false;
        CustomData data = chest.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("isFlying");
    }

    private static boolean emfcompat$isItem(ItemStack stack, ResourceLocation id) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    private static boolean emfcompat$getBool(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(key);
    }
}
