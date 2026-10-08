package strm.touchnmotion.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import strm.touchnmotion.TouchNMotionMod;
import strm.touchnmotion.net.ClientHands;
import strm.touchnmotion.net.HandsAct;
import strm.touchnmotion.net.HandsNet;
import strm.touchnmotion.net.HandsState;

/** Touch'n Motion on NeoForge: the entry point, and the one thing a server has of the mod - the channels it passes players' hands on by. */
@Mod(TouchNMotionMod.MOD_ID)
public class TouchNMotionNeoForge {

    public TouchNMotionNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(TouchNMotionNeoForge::registerChannels);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeClient.run(modEventBus, modContainer);
        }
    }

    private static void registerChannels(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2").optional();
        registrar.playBidirectional(HandsState.TYPE, HandsState.CODEC,
                new DirectionalPayloadHandler<>((state, context) -> ClientHands.receive(state),
                        (state, context) -> {
                            if (context.player() instanceof ServerPlayer player) HandsNet.onServer(player, state);
                        }));
        registrar.playBidirectional(HandsAct.TYPE, HandsAct.CODEC,
                new DirectionalPayloadHandler<>((act, context) -> ClientHands.receive(act),
                        (act, context) -> {
                            if (context.player() instanceof ServerPlayer player) HandsNet.onServer(player, act);
                        }));
    }
}
