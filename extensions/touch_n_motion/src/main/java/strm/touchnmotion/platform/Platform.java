package strm.touchnmotion.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import strm.touchnmotion.blockuse.Held;

/**
 * What the mod asks of its loader, each loader answering in its own way. The one class of the
 * shared code that is written twice over: everything else that is a loader's own sits in the
 * {@code neoforge} and {@code fabric} packages, of which a build takes one.
 */
public final class Platform {
    private Platform() {
    }

    public static boolean isModLoaded(String id) {
        //? if fabric {
        /*return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
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
        //?} else {
        /*return Held.stacks(inventory);
        *///?}
    }

    /** How much fluid a mod's tank holds; zero for what is not one. */
    public static long fluid(Object tank) {
        //? if neoforge {
        return tank instanceof net.neoforged.neoforge.fluids.capability.templates.FluidTank handler ? handler.getFluidAmount() : 0;
        //?} else {
        /*return Held.fluid(tank);
        *///?}
    }

    /** Whether a furnace would burn it. */
    public static boolean isFuel(ItemStack stack) {
        //? if neoforge {
        return stack.getBurnTime(null) > 0;
        //?} else {
        /*return net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(stack);
        *///?}
    }

    /** Whether the loader's own list, beside the game's, has it as something a composter takes. */
    public static boolean compostable(ItemStack stack) {
        //? if neoforge {
        return stack.getItemHolder().getData(net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.COMPOSTABLES) != null;
        //?} else {
        /*return false;
        *///?}
    }

    private static final java.util.Set<Block> POTTED = new java.util.HashSet<>();

    /** Whether a flower pot takes this plant. */
    public static boolean potted(Block plant) {
        //? if neoforge {
        return ((FlowerPotBlock) Blocks.FLOWER_POT).getFullPotsView()
                .getOrDefault(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(plant), () -> Blocks.AIR).get() != Blocks.AIR;
        //?} else {
        /*// Every pot with something in it knows what: the plants that go in a pot are the ones some pot holds.
        if (POTTED.isEmpty()) {
            for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (block instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) POTTED.add(pot.getPotted());
            }
        }
        return POTTED.contains(plant);
        *///?}
    }

    public static boolean isRaft(Boat boat) {
        //? if neoforge {
        return boat.getVariant().isRaft();
        //?} else {
        /*return boat.getVariant() == Boat.Type.BAMBOO;
        *///?}
    }

    /** Whether the server we are on knows the channel. Client only. */
    public static boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        //? if neoforge {
        net.minecraft.client.multiplayer.ClientPacketListener connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
        //?} else {
        /*return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(type);
        *///?}
    }

    /** Client only. */
    public static void sendToServer(CustomPacketPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
        *///?}
    }

    /** Whether that player's game knows the channel. */
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        //? if neoforge {
        return player.connection != null && player.connection.hasChannel(type);
        //?} else {
        /*return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, type);
        *///?}
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        *///?}
    }
}
