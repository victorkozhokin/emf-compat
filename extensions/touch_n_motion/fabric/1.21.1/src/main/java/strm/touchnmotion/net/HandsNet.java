package strm.touchnmotion.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The mod's only part that runs on a server: two optional channels, on which the server does
 * one thing - passes what a player's hands are at on to the players near enough to see them. A
 * client on a server without the mod never sends, and guesses as it did; a client without the
 * mod is never sent to.
 */
public final class HandsNet {
    /** No further than this, blocks, is anyone told. */
    private static final double RANGE = 96;
    /** A player's states are passed on no oftener than this many a second; the rest are dropped. */
    private static final int PER_SECOND = 24;

    private static final Map<ServerPlayer, long[]> RATE = new WeakHashMap<>();

    private HandsNet() {
    }

    /** Both sides: the two payloads, either way, and what the server does with them. */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(HandsState.TYPE, HandsState.CODEC);
        PayloadTypeRegistry.playS2C().register(HandsState.TYPE, HandsState.CODEC);
        PayloadTypeRegistry.playC2S().register(HandsAct.TYPE, HandsAct.CODEC);
        PayloadTypeRegistry.playS2C().register(HandsAct.TYPE, HandsAct.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HandsState.TYPE, (state, context) -> {
            ServerPlayer player = context.player();
            if (allowed(player)) relay(player, state.from(player.getId()));
        });
        ServerPlayNetworking.registerGlobalReceiver(HandsAct.TYPE, (act, context) -> {
            ServerPlayer player = context.player();
            if (allowed(player)) relay(player, act.from(player.getId()));
        });
    }

    /** The client: what it does with what the server passes on. Kept apart so no client class is touched on a server. */
    public static void registerClient() {
        Client.register();
    }

    private static final class Client {
        static void register() {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(HandsState.TYPE, (state, context) -> ClientHands.receive(state));
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(HandsAct.TYPE, (act, context) -> ClientHands.receive(act));
        }
    }

    private static boolean allowed(ServerPlayer player) {
        long second = System.nanoTime() / 1_000_000_000L;
        long[] rate = RATE.computeIfAbsent(player, p -> new long[2]);
        if (rate[0] != second) {
            rate[0] = second;
            rate[1] = 0;
        }
        return ++rate[1] <= PER_SECOND;
    }

    /** To every other player of that level near enough, whose game knows the channel. */
    public static void relay(ServerPlayer from, CustomPacketPayload payload) {
        UUID own = from.getUUID();
        for (ServerPlayer other : from.serverLevel().players()) {
            if (other.getUUID().equals(own) || other.distanceToSqr(from) > RANGE * RANGE) continue;
            if (ServerPlayNetworking.canSend(other, payload.type())) ServerPlayNetworking.send(other, payload);
        }
    }
}
