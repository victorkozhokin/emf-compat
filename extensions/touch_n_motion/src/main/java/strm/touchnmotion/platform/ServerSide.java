package strm.touchnmotion.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * {@link Platform}'s part that a server runs too: what differs by loader and version of the game
 * in the code of both sides. No client class is named here - a dedicated server loads this one,
 * and has none of them.
 */
public final class ServerSide {

    private ServerSide() {
    }

    public static boolean isRaft(Boat boat) {
        //? if neoforge {
        return boat.getVariant().isRaft();
        //?} elif >=1.21.11 {
        /*return boat instanceof net.minecraft.world.entity.vehicle.boat.Raft || boat instanceof net.minecraft.world.entity.vehicle.boat.ChestRaft;
        *///?} else {
        /*return boat.getVariant() == Boat.Type.BAMBOO;
        *///?}
    }

    public static net.minecraft.server.level.ServerLevel level(ServerPlayer player) {
        //? if >=1.21.11 {
        /*return player.level();
        *///?} else {
        return player.serverLevel();
        //?}
    }

    /** Whether that player's game knows the channels. */
    public static boolean canSend(ServerPlayer player) {
        //? if neoforge {
        return player.connection != null && player.connection.hasChannel(strm.touchnmotion.net.HandsState.TYPE);
        //?} elif forge {
        /*return strm.touchnmotion.forge.ForgeNet.canSend(player);
        *///?} else {
        /*return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, strm.touchnmotion.net.HandsState.TYPE);
        *///?}
    }

    /** A {@code HandsState} or a {@code HandsAct}, to that player. */
    public static void send(ServerPlayer player, Object payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        //?} elif forge {
        /*strm.touchnmotion.forge.ForgeNet.send(player, payload);
        *///?} else {
        /*net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        *///?}
    }
}
