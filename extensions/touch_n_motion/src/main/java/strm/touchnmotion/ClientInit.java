package strm.touchnmotion;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import strm.emfcompat.core.ConfigRegistry;
import strm.touchnmotion.footgrounding.FootGroundingFeature;
import strm.touchnmotion.horsesync.HorseSync;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.lookat.LookAt;
import strm.touchnmotion.buttonpress.ButtonPress;
import strm.touchnmotion.blockuse.BlockUse;
import strm.touchnmotion.doorhold.DoorHold;
import strm.touchnmotion.furniture.Furniture;
import strm.touchnmotion.create.ejector.EjectorLaunch;
import strm.touchnmotion.mining.Mining;
import strm.touchnmotion.motion.PoseInertia;
import strm.touchnmotion.torso.TorsoLean;
import strm.touchnmotion.plantreach.PlantReach;
import strm.touchnmotion.wallhand.WallHand;
import strm.touchnmotion.wallhand.WallSqueeze;

/** Everything of the addon that is the client's: kept out of the mod's own class, which a server loads too. */
final class ClientInit {
    private ClientInit() {
    }

    static void run(IEventBus modEventBus) {
        ConfigRegistry.Section config = ConfigRegistry.section(TouchNMotionMod.MOD_ID, "Touch'n Motion");
        String KEY_ENABLED = TouchNMotionMod.KEY_ENABLED;
        // Read through EMFCompatConfig, every option below is off while this one is.
        config.master(KEY_ENABLED, "Touch'n Motion", true,
                "On", "Every animation below works as it is set.",
                "Off", "Turn off every animation of this addon at once; the settings below are kept.");
        // The groups are the screen's; the order inside one is the order of these calls.
        ConfigRegistry.Group movement = config.group("movement", "Movement & body");
        ConfigRegistry.Group surroundings = config.group("surroundings", "Hands & surroundings");
        ConfigRegistry.Group blocks = config.group("blocks", "Blocks & controls");
        ConfigRegistry.Group gestures = config.group("gestures", "Gestures & care");
        ConfigRegistry.Group riding = config.group("riding", "Riding & transport");
        ConfigRegistry.Group debug = config.group("debug", "Debug").collapsedByDefault();
        FootGroundingFeature.register(movement);
        TorsoLean.register(movement);
        PoseInertia.register(movement);
        WallHand.register(surroundings);
        strm.touchnmotion.wallhand.FenceLean.register(surroundings);
        WallSqueeze.register(surroundings, debug);
        PlantReach.register(surroundings);
        LookAt.register(surroundings);
        strm.touchnmotion.lead.LeadHold.register(surroundings);
        strm.touchnmotion.pocket.PocketStash.register(surroundings);
        strm.touchnmotion.gesture.AnimalCare.register(gestures);
        strm.touchnmotion.gesture.HandTo.register(gestures);
        strm.touchnmotion.gesture.ContainerSearch.register(gestures);
        strm.touchnmotion.gesture.ArmorDon.register(gestures);
        strm.touchnmotion.gesture.ShakeOff.register(gestures);
        strm.touchnmotion.gesture.Gesture.register(gestures);
        strm.touchnmotion.fishing.Fishing.register(gestures);
        strm.touchnmotion.fright.Fright.register(gestures);
        ButtonPress.register(blocks);
        strm.touchnmotion.buttonpress.aeronautics.HeavyThrottle.register(blocks);
        BlockUse.register(blocks);
        DoorHold.register(blocks);
        Furniture.register(blocks);
        Mining.register(blocks);
        EjectorLaunch.register(blocks);
        HorseSync.register(riding, modEventBus);
        strm.touchnmotion.ride.BoatRide.register(riding);
        strm.touchnmotion.ride.BoatPassenger.register(riding);
        // As the server has it, when the server has the addon; else as this client is set.
        java.util.function.Function<String, java.util.function.BooleanSupplier> seated = key -> () -> strm.touchnmotion.net.ClientHands.connected()
                || strm.emfcompat.core.EMFCompatCore.isCompatEnabled() && strm.emfcompat.core.EMFCompatConfig.getBoolean(TouchNMotionMod.KEY_ENABLED, true)
                && strm.emfcompat.core.EMFCompatConfig.getBoolean(strm.touchnmotion.ride.BoatRide.KEY_ENABLED, true)
                && strm.emfcompat.core.EMFCompatConfig.getBoolean(key, true);
        strm.touchnmotion.ride.BoatSeats.client = seated.apply(strm.touchnmotion.ride.BoatPassenger.KEY_ENABLED);
        strm.touchnmotion.ride.ChestInBow.register(riding);
        strm.touchnmotion.ride.BoatSeats.chestClient = seated.apply(strm.touchnmotion.ride.ChestInBow.KEY_ENABLED);
        strm.touchnmotion.ride.MinecartRide.register(riding);
        strm.touchnmotion.transport.TransportGrip.register(riding, debug);
        DebugLog.register(debug);
        strm.touchnmotion.compat.ParCoolActivity.register(movement, debug);
        // Order is only the log's order; who wins is the arbiter's call.
        InteractionRuntime.register(WallHand.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.wallhand.FenceLean.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.fishing.Fishing.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.fright.Fright.INSTANCE);
        InteractionRuntime.register(PlantReach.INSTANCE);
        InteractionRuntime.register(LookAt.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.lead.LeadHold.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.pocket.PocketStash.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.gesture.AnimalCare.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.gesture.HandTo.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.gesture.ShakeOff.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.gesture.ArmorDon.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.gesture.ContainerSearch.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.transport.TransportGrip.INSTANCE);
        InteractionRuntime.register(ButtonPress.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.buttonpress.aeronautics.HeavyThrottle.INSTANCE);
        InteractionRuntime.register(BlockUse.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.blockuse.aeronautics.CockpitControls.INSTANCE);
        InteractionRuntime.register(DoorHold.INSTANCE);
        InteractionRuntime.register(Furniture.INSTANCE);
        InteractionRuntime.register(Mining.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.ride.BoatRide.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.ride.BoatPassenger.INSTANCE);
        InteractionRuntime.register(strm.touchnmotion.ride.MinecartRide.INSTANCE);
        TouchNMotionHook.register();
        // Leaving a world drops every feature's per-entity state with it.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> EntityStates.clearAll());
        // What our own hands are at, told to a server that passes it on; and all of it dropped with the world.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> strm.touchnmotion.net.ClientHands.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> strm.touchnmotion.net.ClientHands.forgetAll());
    }

    /** The mod's own entry in the mod list opens the settings: the shared screen, on this mod's tab. */
    static void configScreen(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mod, parent) -> new strm.emfcompat.core.client.ConfigScreen(parent, TouchNMotionMod.MOD_ID));
    }
}
