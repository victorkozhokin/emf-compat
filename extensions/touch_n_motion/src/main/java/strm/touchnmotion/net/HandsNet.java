package strm.touchnmotion.net;

import net.minecraft.server.level.ServerPlayer;
import strm.touchnmotion.platform.ServerSide;

import java.util.UUID;

/**
 * The addon's only part that runs on a server: two optional channels, on which the server does
 * one thing - passes what a player's hands are at on to the players near enough to see them. A
 * client on a server without the addon never sends, and guesses as it did; a client without the
 * addon is never sent to.
 */
public final class HandsNet {
    /** No further than this, blocks, is anyone told. */
    private static final double RANGE = 96;
    private static final LatestStates<ServerPlayer, HandsState> PENDING = new LatestStates<>();

    private HandsNet() {
    }

    /** A server got a player's state: passed on, under the player's own id. The channels themselves are the loader's part to register. */
    public static void onServer(ServerPlayer player, HandsState state) {
        PENDING.put(player, state.from(player.getId()));
    }

    public static void onServer(ServerPlayer player, HandsAct act) {
        // A click must not use up the input budget, nor arrive before its target/held control.
        PENDING.flush(player, HandsNet::sendState);
        relay(player, act.from(player.getId()));
    }

    public static void onServer(ServerPlayer player, HandsWork work) {
        PENDING.flush(player, HandsNet::sendState);
        relay(player, work.from(player.getId()));
    }

    /** End of a server tick: collapse a burst of input to its newest state, including release. */
    public static void tick() {
        PENDING.flush(HandsNet::sendState);
    }

    private static void sendState(ServerPlayer player, HandsState state) {
        if (ServerSide.level(player).players().contains(player)) relay(player, state);
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
