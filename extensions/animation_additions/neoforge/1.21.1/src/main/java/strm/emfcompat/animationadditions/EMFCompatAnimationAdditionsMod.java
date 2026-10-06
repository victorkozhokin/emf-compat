package strm.emfcompat.animationadditions;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
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

/**
 * Animation Additions: small player-animation features that are not a compat layer for any one
 * mod, gathered in one addon. Each one registers its own toggles in the shared config section and
 * lives in its own package; {@link AnimationAdditionsHook} puts them all on the model:
 *
 * <ul>
 *   <li>{@code horsesync} - the rider follows a horse animated by EMF, with a riding pose;</li>
 *   <li>{@code footgrounding} - Foot IK: both feet stand on uneven ground, a horse's too;</li>
 *   <li>{@code wallhand} - standing at a wall, the hands rest on it; in a gap narrower than the shoulders the torso turns to fit;</li>
 *   <li>{@code plantreach} - in grass or crops, the hands brush the plants;</li>
 *   <li>{@code lookat} - standing idle, the head turns to a creature nearby;</li>
 *   <li>{@code buttonpress} - the right hand reaches for a button or a lever about to be used, a foot stamps on a button on the floor;</li>
 *   <li>{@code blockuse} - the hand goes to the spot of a block it uses, and puts in, takes out, taps or holds on;</li>
 *   <li>{@code doorhold} - near a door the hands go to its handles and hold it going through;</li>
 *   <li>{@code furniture} - looking at a lectern or a chest close by, both hands go to it;</li>
 *   <li>{@code motion} - how the player moves as smooth signals; the pack's pose cuts settle with inertia;</li>
 *   <li>{@code torso} - the torso leans with the motion, after the head and over the foot that carries the weight;</li>
 *   <li>{@code mining} - breaking a block, the swing brings the tool's head onto the point hit;</li>
 *   <li>{@code pocket} - a moment after a run of pick-ups the right hand tucks it all away at the hip;</li>
 *   <li>{@code gesture} - short gestures after a real action: feeding, milking and shearing, an armour stand,
 *       a seed planted, armour and accessories put on, a shake after water, looking through a container;</li>
 *   <li>{@code ejector} - on a Create weighted ejector the body braces, and flies when thrown.</li>
 * </ul>
 */
@Mod(EMFCompatAnimationAdditionsMod.MOD_ID)
public class EMFCompatAnimationAdditionsMod {

    public static final String MOD_ID = "emf_compat_animation_additions";
    /** The addon's master option; the core's global switch covers it by its suffix. */
    public static final String KEY_ENABLED = "animationadditions.enabled";

    public EMFCompatAnimationAdditionsMod(IEventBus modEventBus, ModContainer modContainer) {
        ConfigRegistry.Section config = ConfigRegistry.section(MOD_ID, "Animation Additions");
        if (FMLEnvironment.dist == Dist.CLIENT) {
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
            strm.emfcompat.animationadditions.leash.LeashHold.register(surroundings);
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
            InteractionRuntime.register(strm.emfcompat.animationadditions.leash.LeashHold.INSTANCE);
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
            InteractionRuntime.register(strm.emfcompat.animationadditions.blockuse.CockpitControls.INSTANCE);
            InteractionRuntime.register(DoorHold.INSTANCE);
            InteractionRuntime.register(Furniture.INSTANCE);
            InteractionRuntime.register(Mining.INSTANCE);
            AnimationAdditionsHook.register();
            // Leaving a world drops every feature's per-entity state with it.
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> EntityStates.clearAll());
        }
    }
}
