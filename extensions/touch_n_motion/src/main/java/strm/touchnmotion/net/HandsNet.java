package strm.touchnmotion.net;

import net.minecraft.server.level.ServerPlayer;
import strm.touchnmotion.platform.ServerSide;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The addon's only part that runs on a server: two optional channels, on which the server does
 * one thing - passes what a player's hands are at on to the players near enough to see them. A
 * client on a server without the addon never sends, and guesses as it did; a client without the
 * addon is never sent to.
 */
public final class HandsNet {
    /** No further than this, blocks, is anyone told. */
    private static final double RANGE = 96;
    /** A player's states are passed on no oftener than this many a second; the rest are dropped. */
    private static final int PER_SECOND = 24;

    private static final Map<ServerPlayer, long[]> RATE = new WeakHashMap<>();

    private HandsNet() {
    }

    /** A server got a player's state: passed on, under the player's own id. The channels themselves are the loader's part to register. */
    public static void onServer(ServerPlayer player, HandsState state) {
        if (allowed(player)) relay(player, state.from(player.getId()));
    }

    public static void onServer(ServerPlayer player, HandsAct act) {
        if (allowed(player)) relay(player, act.from(player.getId()));
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
    public static void relay(ServerPlayer from, Object payload) {
        UUID own = from.getUUID();
        for (ServerPlayer other : ServerSide.level(from).players()) {
            if (other.getUUID().equals(own) || other.distanceToSqr(from) > RANGE * RANGE) continue;
            if (ServerSide.canSend(other)) ServerSide.send(other, payload);
        }
    }
}
