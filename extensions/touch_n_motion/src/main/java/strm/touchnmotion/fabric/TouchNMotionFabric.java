package strm.touchnmotion.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import strm.touchnmotion.net.HandsAct;
import strm.touchnmotion.net.HandsNet;
import strm.touchnmotion.net.HandsState;

/**
 * Touch'n Motion on Fabric: the part that runs on both sides - the channels a server passes
 * players' hands on by. Everything else is the client's ({@link TouchNMotionFabricClient}).
 */
public class TouchNMotionFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(HandsState.TYPE, HandsState.CODEC);
        PayloadTypeRegistry.playS2C().register(HandsState.TYPE, HandsState.CODEC);
        PayloadTypeRegistry.playC2S().register(HandsAct.TYPE, HandsAct.CODEC);
        PayloadTypeRegistry.playS2C().register(HandsAct.TYPE, HandsAct.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HandsState.TYPE, (state, context) -> HandsNet.onServer(context.player(), state));
        ServerPlayNetworking.registerGlobalReceiver(HandsAct.TYPE, (act, context) -> HandsNet.onServer(context.player(), act));
    }
}
