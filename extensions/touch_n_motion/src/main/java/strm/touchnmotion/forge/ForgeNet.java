package strm.touchnmotion.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import strm.touchnmotion.net.ClientHands;
import strm.touchnmotion.net.HandsAct;
import strm.touchnmotion.net.HandsNet;
import strm.touchnmotion.net.HandsState;

import java.util.function.Supplier;

/**
 * The channel on Forge 1.20.1, which has no payloads of the game's own yet: one channel, two
 * messages, either way. Optional on both sides - a game without the mod is neither refused nor
 * sent to.
 */
public final class ForgeNet {
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("touch_n_motion", "hands"), () -> VERSION, version -> true, version -> true);

    private ForgeNet() {
    }

    static void register() {
        CHANNEL.registerMessage(0, HandsState.class, (state, out) -> HandsState.write(out, state), HandsState::read, ForgeNet::got);
        CHANNEL.registerMessage(1, HandsAct.class, HandsAct::write, HandsAct::read, ForgeNet::got);
    }

    private static void got(Object message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        boolean server = c.getDirection().getReceptionSide().isServer();
        c.enqueueWork(() -> {
            if (server) {
                ServerPlayer player = c.getSender();
                if (player == null) return;
                if (message instanceof HandsState state) HandsNet.onServer(player, state);
                else if (message instanceof HandsAct act) HandsNet.onServer(player, act);
            } else {
                Client.got(message);
            }
        });
        c.setPacketHandled(true);
    }

    /** Kept apart so that no client class is touched on a server. */
    private static final class Client {
        static void got(Object message) {
            if (message instanceof HandsState state) ClientHands.receive(state);
            else if (message instanceof HandsAct act) ClientHands.receive(act);
        }
    }

    public static boolean canSendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && CHANNEL.isRemotePresent(connection.getConnection());
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    public static boolean canSend(ServerPlayer player) {
        return player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void send(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
