package strm.emfcompat.animationadditions;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;
import strm.emfcompat.animationadditions.horsesync.HorseSync;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.lookat.LookAt;
import strm.emfcompat.animationadditions.buttonpress.ButtonPress;
import strm.emfcompat.animationadditions.blockuse.BlockUse;
import strm.emfcompat.animationadditions.doorhold.DoorHold;
import strm.emfcompat.animationadditions.furniture.Furniture;
import strm.emfcompat.animationadditions.ejector.EjectorLaunch;
import strm.emfcompat.animationadditions.mining.Mining;
import strm.emfcompat.animationadditions.motion.PoseInertia;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.animationadditions.plantreach.PlantReach;
import strm.emfcompat.animationadditions.wallhand.WallHand;
import strm.emfcompat.animationadditions.wallhand.WallSqueeze;

/** Everything of the addon that is the client's: kept out of the mod's own class, which a server loads too. */
final class ClientInit {
    private ClientInit() {
    }

    static void run(IEventBus modEventBus) {
        ConfigRegistry.Section config = ConfigRegistry.section(EMFCompatAnimationAdditionsMod.MOD_ID, "Animation Additions");
        String KEY_ENABLED = EMFCompatAnimationAdditionsMod.KEY_ENABLED;
        // Read through EMFCompatConfig, every option below is off while this one is.
        config.master(KEY_ENABLED, "Animation Additions", true,
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
        WallSqueeze.register(surroundings, debug);
        PlantReach.register(surroundings);
        LookAt.register(surroundings);
        strm.emfcompat.animationadditions.lead.LeadHold.register(surroundings);
        strm.emfcompat.animationadditions.pocket.PocketStash.register(surroundings);
        strm.emfcompat.animationadditions.gesture.AnimalCare.register(gestures);
        strm.emfcompat.animationadditions.gesture.HandTo.register(gestures);
        strm.emfcompat.animationadditions.gesture.ContainerSearch.register(gestures);
        strm.emfcompat.animationadditions.gesture.ArmorDon.register(gestures);
        strm.emfcompat.animationadditions.gesture.ShakeOff.register(gestures);
        strm.emfcompat.animationadditions.gesture.Gesture.register(gestures);
        ButtonPress.register(blocks);
        strm.emfcompat.animationadditions.buttonpress.HeavyThrottle.register(blocks);
        BlockUse.register(blocks);
        DoorHold.register(blocks);
        Furniture.register(blocks);
        Mining.register(blocks);
        EjectorLaunch.register(blocks);
        HorseSync.register(riding, modEventBus);
        strm.emfcompat.animationadditions.transport.TransportGrip.register(riding, debug);
        DebugLog.register(debug);
        strm.emfcompat.animationadditions.compat.ParCoolActivity.register(movement, debug);
        // Order is only the log's order; who wins is the arbiter's call.
        InteractionRuntime.register(WallHand.INSTANCE);
        InteractionRuntime.register(PlantReach.INSTANCE);
        InteractionRuntime.register(LookAt.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.lead.LeadHold.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.pocket.PocketStash.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.gesture.AnimalCare.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.gesture.HandTo.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.gesture.ShakeOff.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.gesture.ArmorDon.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.gesture.ContainerSearch.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.transport.TransportGrip.INSTANCE);
        InteractionRuntime.register(ButtonPress.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.buttonpress.HeavyThrottle.INSTANCE);
        InteractionRuntime.register(BlockUse.INSTANCE);
        InteractionRuntime.register(strm.emfcompat.animationadditions.blockuse.aeronautics.CockpitControls.INSTANCE);
        InteractionRuntime.register(DoorHold.INSTANCE);
        InteractionRuntime.register(Furniture.INSTANCE);
        InteractionRuntime.register(Mining.INSTANCE);
        AnimationAdditionsHook.register();
        // Leaving a world drops every feature's per-entity state with it.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> EntityStates.clearAll());
        // What our own hands are at, told to a server that passes it on; and all of it dropped with the world.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> strm.emfcompat.animationadditions.net.ClientHands.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> strm.emfcompat.animationadditions.net.ClientHands.forgetAll());
    }
}
