package strm.touchnmotion.neoforge;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import strm.emfcompat.core.client.ConfigScreen;
import strm.touchnmotion.ClientInit;
import strm.touchnmotion.TouchNMotionMod;
import strm.touchnmotion.footgrounding.FootGroundingFeature;
import strm.touchnmotion.horsesync.ClientEventHandler;
import strm.touchnmotion.horsesync.HorseSync;

/** The client on NeoForge: the mod's client part started, and NeoForge's events passed on to it. Never loaded on a server. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void run(IEventBus modEventBus, ModContainer container) {
        ClientInit.run();
        modEventBus.addListener((FMLClientSetupEvent event) -> HorseSync.registerHook());
        // The mod's own entry in the mod list opens the settings: the shared screen, on this mod's tab.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigScreen(parent, TouchNMotionMod.MOD_ID));

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientInit.leftWorld());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientInit.tickEnd());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> ClientEventHandler.onClientTick());

        // The rider lifted with an animated horse.
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre event) ->
                ClientEventHandler.onRenderPlayerPre(event.getEntity(), event.getPoseStack()));
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post event) ->
                ClientEventHandler.onRenderPlayerPost(event.getEntity(), event.getPoseStack()));
        // The rider lowered and pitched with a horse on uneven ground. Innermost: after everyone
        // else has moved the rider (a cancelled draw never reaches the lowest listener), and undone
        // first, so the push and pop pair up with no one in between.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderPlayerEvent.Pre event) ->
                FootGroundingFeature.onRenderPlayerPre(event.getEntity(), event.getPoseStack(), event.getPartialTick()));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (RenderPlayerEvent.Post event) ->
                FootGroundingFeature.onRenderPlayerPost(event.getPoseStack()));
    }
}
