package strm.touchnmotion.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import strm.emfcompat.core.client.ConfigScreen;
import strm.touchnmotion.ClientInit;
import strm.touchnmotion.TouchNMotionMod;
import strm.touchnmotion.horsesync.ClientEventHandler;
import strm.touchnmotion.horsesync.HorseSync;

/** The client on Forge: the mod's client part started, and Forge's events passed on to it. Never loaded on a server. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void run(IEventBus modEventBus) {
        ClientInit.run();
        modEventBus.addListener((FMLClientSetupEvent event) -> HorseSync.registerHook());
        // The mod's own entry in the mod list opens the settings: the shared screen, on this mod's tab.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new ConfigScreen(parent, TouchNMotionMod.MOD_ID)));

        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientInit.leftWorld());
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) ClientEventHandler.onClientTick();
            else ClientInit.tickEnd();
        });

        // The rider carried with an animated horse and tilted with one on uneven ground (BodyStages.RIDER).
        // Innermost: after everyone else has moved the rider (a cancelled draw never reaches the lowest
        // listener), and undone first, so the pushes and pops pair up with no one in between.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderPlayerEvent.Pre event) ->
                strm.touchnmotion.stages.BodyStages.beforeRider(event.getEntity(), event.getPoseStack(), event.getPartialTick()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (RenderPlayerEvent.Post event) ->
                strm.touchnmotion.stages.BodyStages.afterRider(event.getPoseStack()));
    }
}
