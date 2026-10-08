package strm.touchnmotion.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import strm.touchnmotion.ClientInit;
import strm.touchnmotion.horsesync.ClientEventHandler;
import strm.touchnmotion.horsesync.HorseSync;
import strm.touchnmotion.net.ClientHands;
import strm.touchnmotion.net.HandsAct;
import strm.touchnmotion.net.HandsState;

/**
 * Touch'n Motion on Fabric, the client: the mod's client part started, and Fabric's events passed
 * on to it. Fabric has no event for a player's draw: that comes from {@code mixin/fabric/PlayerRenderEventsMixin}.
 */
public class TouchNMotionFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientInit.run();
        HorseSync.registerHook();
        ClientPlayNetworking.registerGlobalReceiver(HandsState.TYPE, (state, context) -> ClientHands.receive(state));
        ClientPlayNetworking.registerGlobalReceiver(HandsAct.TYPE, (act, context) -> ClientHands.receive(act));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientInit.leftWorld());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientInit.tickEnd());
        ClientTickEvents.START_CLIENT_TICK.register(client -> ClientEventHandler.onClientTick());
    }
}
