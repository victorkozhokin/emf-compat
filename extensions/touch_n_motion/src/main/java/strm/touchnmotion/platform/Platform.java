package strm.touchnmotion.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import strm.touchnmotion.blockuse.Held;

/**
 * What the mod asks of its loader and of its version of the game, each answering in its own way.
 * The one class of the shared code that is written several times over: everything else that is a
 * loader's own sits in the {@code neoforge} and {@code fabric} packages, of which a build takes
 * one; what is a span of versions' own, in the mixins' {@code legacy} (up to 1.21.10) and
 * {@code modern} packages; and names the game merely changed are replaced by the build
 * ({@code renames.gradle}).
 */
public final class Platform {
    private Platform() {
    }

    public static boolean isModLoaded(String id) {
        //? if fabric {
        /*return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
        *///?} elif forge {
        /*return net.minecraftforge.fml.ModList.get().isLoaded(id);
        *///?} else {
        return net.neoforged.fml.ModList.get().isLoaded(id);
        //?}
    }

    /** The stacks a mod's inventory holds; empty for what is not one. */
    public static ItemStack[] stacks(Object inventory) {
        //? if neoforge {
        if (inventory instanceof net.neoforged.neoforge.items.IItemHandler handler) {
            ItemStack[] out = new ItemStack[handler.getSlots()];
            for (int i = 0; i < out.length; i++) out[i] = handler.getStackInSlot(i);
            return out;
        }
        return new ItemStack[0];
        //?} elif forge {
        /*if (inventory instanceof net.minecraftforge.items.IItemHandler handler) {
            ItemStack[] out = new ItemStack[handler.getSlots()];
            for (int i = 0; i < out.length; i++) out[i] = handler.getStackInSlot(i);
            return out;
        }
        return new ItemStack[0];
        *///?} else {
        /*return Held.stacks(inventory);
        *///?}
    }

    /** How much fluid a mod's tank holds; zero for what is not one. */
    public static long fluid(Object tank) {
        //? if neoforge {
        return tank instanceof net.neoforged.neoforge.fluids.capability.templates.FluidTank handler ? handler.getFluidAmount() : 0;
        //?} elif forge {
        /*return tank instanceof net.minecraftforge.fluids.capability.templates.FluidTank handler ? handler.getFluidAmount() : 0;
        *///?} else {
        /*return Held.fluid(tank);
        *///?}
    }

    /** Whether a furnace would burn it. Client only from 1.21.11 on: what burns is the world's to say. */
    public static boolean isFuel(ItemStack stack) {
        //? if neoforge {
        return stack.getBurnTime(null) > 0;
        //?} elif forge {
        /*return net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null) > 0;
        */        //?} elif >=26.3 {
        /*return stack.has(net.minecraft.core.component.DataComponents.COOKING_FUEL);
        *///?} elif >=1.21.11 {
        /*net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null && level.fuelValues().isFuel(stack);
        *///?} else {
        /*return net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(stack);
        *///?}
    }

    /** Whether a composter takes it. */
    public static boolean compostable(ItemStack stack) {
        //? if >=26.3 {
        /*return stack.has(net.minecraft.core.component.DataComponents.COMPOSTABLE);
        *///?} else {
        //? if neoforge {
        if (stack.getItemHolder().getData(net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.COMPOSTABLES) != null) return true;
        //?}
        return net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.containsKey(stack.getItem());
        //?}
    }

    private static final java.util.Set<Block> POTTED = new java.util.HashSet<>();

    /** Whether a flower pot takes this plant. */
    public static boolean potted(Block plant) {
        //? if neoforge || forge {
        return ((FlowerPotBlock) Blocks.FLOWER_POT).getFullPotsView()
                .getOrDefault(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(plant), () -> Blocks.AIR).get() != Blocks.AIR;
        //?} else {
        /*// Every pot with something in it knows what: the plants that go in a pot are the ones some pot holds.
        if (POTTED.isEmpty()) {
            for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (block instanceof FlowerPotBlock pot && pottedIn(pot) != Blocks.AIR) POTTED.add(pottedIn(pot));
            }
        }
        return POTTED.contains(plant);
        *///?}
    }

    /** Where the camera is. Client only. */
    public static net.minecraft.world.phys.Vec3 cameraPosition() {
        //? if >=26.2 {
        /*return net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
        *///?} elif >=1.21.11 {
        /*return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().position();
        *///?} else {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        //?}
    }

    /** The screen that is open; {@code null} for none. Client only. */
    public static net.minecraft.client.gui.screens.Screen screen() {
        //? if >=26.2 {
        /*return net.minecraft.client.Minecraft.getInstance().gui.screen();
        *///?} else {
        return net.minecraft.client.Minecraft.getInstance().screen;
        //?}
    }

    /** Whether the arm is in a swing. */
    public static boolean swinging(net.minecraft.world.entity.LivingEntity entity) {
        //? if >=26.3 {
        /*return entity.isSwinging();
        *///?} else {
        return entity.swinging;
        //?}
    }

    /** Ticks into the swing: it starts over at each new one. */
    public static int swingTime(net.minecraft.world.entity.LivingEntity entity) {
        //? if >=26.3 {
        /*var swing = entity.getCurrentSwing();
        return swing == null ? 0 : Math.round(entity.getSwingAnimation(1f) * swing.durationTicks());
        *///?} else {
        return entity.swingTime;
        //?}
    }

    public static net.minecraft.world.InteractionHand swingingArm(net.minecraft.world.entity.LivingEntity entity) {
        //? if >=26.3 {
        /*var swing = entity.getCurrentSwing();
        return swing == null ? net.minecraft.world.InteractionHand.MAIN_HAND : swing.hand();
        *///?} else {
        return entity.swingingArm;
        //?}
    }

    /** Turns what is drawn from here on. */
    public static void rotate(com.mojang.blaze3d.vertex.PoseStack stack, org.joml.Quaternionf by) {
        //? if >=26.3 {
        /*stack.rotate(by);
        *///?} else {
        stack.mulPose(by);
        //?}
    }

    /** A cow of any kind, a mooshroom among them: from 1.21.11 on that one is a cow's sibling and not a cow. */
    public static boolean cow(net.minecraft.world.entity.Entity entity) {
        //? if >=1.21.11 {
        /*return entity instanceof net.minecraft.world.entity.animal.cow.AbstractCow;
        *///?} else {
        return entity instanceof net.minecraft.world.entity.animal.Cow;
        //?}
    }

    public static boolean isAxe(ItemStack stack) {
        //? if >=26.3 {
        /*return stack.is(net.minecraft.tags.ItemTags.AXES);
        *///?} else {
        return stack.getItem() instanceof net.minecraft.world.item.AxeItem;
        //?}
    }

    public static boolean isShovel(ItemStack stack) {
        //? if >=26.3 {
        /*return stack.is(net.minecraft.tags.ItemTags.SHOVELS);
        *///?} else {
        return stack.getItem() instanceof net.minecraft.world.item.ShovelItem;
        //?}
    }

    public static boolean isHoe(ItemStack stack) {
        //? if >=26.3 {
        /*return stack.is(net.minecraft.tags.ItemTags.HOES);
        *///?} else {
        return stack.getItem() instanceof net.minecraft.world.item.HoeItem;
        //?}
    }

    /** A music disc. */
    public static boolean isDisc(ItemStack stack) {
        //? if >=1.21 {
        return stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);
        //?} else {
        /*return stack.getItem() instanceof net.minecraft.world.item.RecordItem;
        *///?}
    }

    public static boolean isWaterBottle(ItemStack stack) {
        //? if >=1.20.5 {
        net.minecraft.world.item.alchemy.PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return stack.is(net.minecraft.world.item.Items.POTION) && contents != null && contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        //?} else {
        /*return stack.is(net.minecraft.world.item.Items.POTION)
                && net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack) == net.minecraft.world.item.alchemy.Potions.WATER;
        *///?}
    }

    /** Dyed, as leather armour is. */
    public static boolean isDyed(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.has(net.minecraft.core.component.DataComponents.DYED_COLOR);
        //?} else {
        /*return stack.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem dyeable && dyeable.hasCustomColor(stack);
        *///?}
    }

    /** A banner with a pattern on it. */
    public static boolean hasPatterns(ItemStack stack) {
        //? if >=1.20.5 {
        return !stack.getOrDefault(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY).layers().isEmpty();
        //?} else {
        /*return net.minecraft.world.level.block.entity.BannerBlockEntity.getPatternCount(stack) > 0;
        *///?}
    }

    /** Who holds the lead of that creature; {@code null} for no one, and for what cannot be led. */
    public static net.minecraft.world.entity.Entity leashHolder(net.minecraft.world.entity.Entity led) {
        //? if >=1.21 {
        return led instanceof net.minecraft.world.entity.Leashable leashed ? leashed.getLeashHolder() : null;
        //?} else {
        /*return led instanceof net.minecraft.world.entity.Mob mob ? mob.getLeashHolder() : null;
        *///?}
    }

    /** Whether that creature can be on a lead at all. */
    public static boolean leashable(net.minecraft.world.entity.Entity entity) {
        //? if >=1.21 {
        return entity instanceof net.minecraft.world.entity.Leashable;
        //?} else {
        /*return entity instanceof net.minecraft.world.entity.Mob;
        *///?}
    }

    /** How far into the tick the frame is. {@code running}: the value that goes on while the game is paused; otherwise the one a paused game holds. Client only. */
    public static float partialTick(boolean running) {
        //? if >=1.21.11 {
        /*return net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(running);
        *///?} elif >=1.21 {
        return net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(running);
        //?} else {
        /*return running ? net.minecraft.client.Minecraft.getInstance().getFrameTime() : net.minecraft.client.Minecraft.getInstance().getPartialTick();
        *///?}
    }

    /** What is in a flower pot; air in an empty one. */
    public static Block pottedIn(FlowerPotBlock pot) {
        //? if >=1.20.5 {
        return pot.getPotted();
        //?} else {
        /*return pot.getContent();
        *///?}
    }

    /** How far a player's hand reaches a block. */
    public static double blockReach(net.minecraft.world.entity.player.Player player) {
        //? if >=1.20.5 {
        return player.blockInteractionRange();
        //?} elif forge {
        /*return player.getBlockReach();
        *///?} else {
        /*return player.isCreative() ? 5.0 : 4.5;
        *///?}
    }

    /** How far a player's hand reaches a creature. */
    public static double entityReach(net.minecraft.world.entity.player.Player player) {
        //? if >=1.20.5 {
        return player.entityInteractionRange();
        //?} elif forge {
        /*return player.getEntityReach();
        *///?} else {
        /*return player.isCreative() ? 5.0 : 3.0;
        *///?}
    }

    /** Applies a transform to what is drawn from here on. */
    public static void apply(com.mojang.blaze3d.vertex.PoseStack stack, org.joml.Matrix4f by) {
        //? if >=1.20.5 {
        stack.mulPose(by);
        //?} else {
        /*stack.mulPoseMatrix(by);
        *///?}
    }

    /** The block an entity's feet are in. */
    public static net.minecraft.world.level.block.state.BlockState blockAtFeet(net.minecraft.world.entity.Entity entity) {
        //? if >=1.20.5 {
        return entity.getInBlockState();
        //?} else {
        /*return entity.getFeetBlockState();
        *///?}
    }

    /** In water, a column of bubbles counted. */
    public static boolean inWater(net.minecraft.world.entity.Entity entity) {
        //? if >=1.21.11 {
        /*return entity.isInWater();
        *///?} else {
        return entity.isInWaterOrBubble();
        //?}
    }

    /** Armour a player wears: what is put on by a click with it in the hand. */
    public static boolean isArmor(ItemStack stack) {
        //? if >=1.21.11 {
        /*net.minecraft.world.item.equipment.Equippable worn = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        return worn != null && worn.slot().getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR
                && !stack.is(net.minecraft.world.item.Items.ELYTRA);
        *///?} else {
        return stack.getItem() instanceof net.minecraft.world.item.ArmorItem;
        //?}
    }

    /** The horizontal direction nearest to that way. */
    public static net.minecraft.core.Direction nearest(double x, double z) {
        //? if >=1.21.11 {
        /*return net.minecraft.core.Direction.getApproximateNearest(x, 0, z);
        *///?} else {
        return net.minecraft.core.Direction.getNearest(x, 0, z);
        //?}
    }

    /** Whether a campfire cooks it. */
    public static boolean cooksOnCampfire(net.minecraft.world.level.block.entity.CampfireBlockEntity campfire, ItemStack stack) {
        //? if >=1.21.11 {
        /*return campfire.getLevel() != null && campfire.getLevel().recipeAccess()
                .propertySet(net.minecraft.world.item.crafting.RecipePropertySet.CAMPFIRE_INPUT).test(stack);
        *///?} else {
        return campfire.getCookableRecipe(stack).isPresent();
        //?}
    }

    public static int selectedSlot(net.minecraft.world.entity.player.Player player) {
        //? if >=1.21.11 {
        /*return player.getInventory().getSelectedSlot();
        *///?} else {
        return player.getInventory().selected;
        //?}
    }

    public static float x(net.minecraft.client.model.geom.PartPose pose) {
        //? if >=1.21.11 {
        /*return pose.x();
        *///?} else {
        return pose.x;
        //?}
    }

    public static float y(net.minecraft.client.model.geom.PartPose pose) {
        //? if >=1.21.11 {
        /*return pose.y();
        *///?} else {
        return pose.y;
        //?}
    }

    public static float z(net.minecraft.client.model.geom.PartPose pose) {
        //? if >=1.21.11 {
        /*return pose.z();
        *///?} else {
        return pose.z;
        //?}
    }

    public static float yRot(net.minecraft.client.model.geom.PartPose pose) {
        //? if >=1.21.11 {
        /*return pose.yRot();
        *///?} else {
        return pose.yRot;
        //?}
    }

    public static net.minecraft.resources.ResourceLocation id(net.minecraft.client.resources.sounds.SoundInstance sound) {
        //? if >=1.21.11 {
        /*return sound.getIdentifier();
        *///?} else {
        return sound.getLocation();
        //?}
    }

    /** Where on an animal its lead is tied, from its feet. */
    public static net.minecraft.world.phys.Vec3 leashOffset(net.minecraft.world.entity.Entity animal, float partialTick) {
        //? if >=1.21.11 {
        /*return animal instanceof net.minecraft.world.entity.Leashable leashed ? leashed.getLeashOffset(partialTick)
                : new net.minecraft.world.phys.Vec3(0, animal.getEyeHeight(), animal.getBbWidth() * 0.4f);
        *///?} else {
        return animal.getLeashOffset(partialTick);
        //?}
    }

    /**
     * Where a cart at that point of the rails is drawn, {@code along} blocks further down them;
     * {@code null} off rails, and for a cart that does not ride the rails the old way.
     */
    public static net.minecraft.world.phys.Vec3 railPos(net.minecraft.world.entity.vehicle.AbstractMinecart cart, double x, double y, double z, double along) {
        //? if >=1.21.11 {
        /*if (!(cart.getBehavior() instanceof net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior rails)) return null;
        return along == 0 ? rails.getPos(x, y, z) : rails.getPosOffs(x, y, z, along);
        *///?} else {
        return along == 0 ? cart.getPos(x, y, z) : cart.getPosOffs(x, y, z, along);
        //?}
    }

    /** How much larger than its model a horse is drawn. */
    public static float horseScale(net.minecraft.world.entity.animal.horse.AbstractHorse horse) {
        //? if >=1.21.11 {
        /*// The renderer no longer scales: the size is in the model itself.
        return 1.0f;
        *///?} else {
        return net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(horse)
                instanceof strm.touchnmotion.mixin.legacy.horsesync.AbstractHorseRendererAccessor accessor ? accessor.emfhorsesync$getScale() : 1.0f;
        //?}
    }

    /** Whether the server we are on knows the channels. Client only. */
    public static boolean canSendToServer() {
        //? if neoforge {
        net.minecraft.client.multiplayer.ClientPacketListener connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(strm.touchnmotion.net.HandsState.TYPE);
        //?} elif forge {
        /*return strm.touchnmotion.forge.ForgeNet.canSendToServer();
        *///?} else {
        /*return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(strm.touchnmotion.net.HandsState.TYPE);
        *///?}
    }

    /** A {@code HandsState} or a {@code HandsAct}, to the server. Client only. */
    public static void sendToServer(Object payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer((net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        //?} elif forge {
        /*strm.touchnmotion.forge.ForgeNet.sendToServer(payload);
        *///?} else {
        /*net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send((net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        *///?}
    }

}
