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
import strm.emfcompat.animationadditions.doorhold.DoorHold;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.animationadditions.plantreach.PlantReach;
import strm.emfcompat.animationadditions.wallhand.WallHand;

/**
 * Animation Additions: small player-animation features that are not a compat layer for any one
 * mod, gathered in one addon. Each one registers its own toggles in the shared config section and
 * lives in its own package; {@link AnimationAdditionsHook} puts them all on the model:
 *
 * <ul>
 *   <li>{@code horsesync} - the rider follows a horse animated by EMF, with a riding pose;</li>
 *   <li>{@code footgrounding} - Foot IK: both feet stand on uneven ground;</li>
 *   <li>{@code wallhand} - standing at a wall, the hands rest on it;</li>
 *   <li>{@code plantreach} - in grass or crops, the hands brush the plants;</li>
 *   <li>{@code lookat} - standing idle, the head turns to a creature nearby;</li>
 *   <li>{@code buttonpress} - the right hand reaches for a button, a lever or a door handle about to be used, a foot stamps on a button on the floor;</li>
 *   <li>{@code doorhold} - going through an open door, the hand on its side holds it.</li>
 * </ul>
 */
@Mod(EMFCompatAnimationAdditionsMod.MOD_ID)
public class EMFCompatAnimationAdditionsMod {

    public static final String MOD_ID = "emf_compat_animation_additions";

    public EMFCompatAnimationAdditionsMod(IEventBus modEventBus, ModContainer modContainer) {
        ConfigRegistry.Section config = ConfigRegistry.section(MOD_ID, "Animation Additions");
        HorseSync.register(config, modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FootGroundingFeature.register(config);
            WallHand.register(config);
            PlantReach.register(config);
            LookAt.register(config);
            TorsoLean.register(config);
            ButtonPress.register(config);
            DoorHold.register(config);
            // Order is only the log's order; who wins is the arbiter's call.
            InteractionRuntime.register(WallHand.INSTANCE);
            InteractionRuntime.register(PlantReach.INSTANCE);
            InteractionRuntime.register(LookAt.INSTANCE);
            InteractionRuntime.register(ButtonPress.INSTANCE);
            InteractionRuntime.register(DoorHold.INSTANCE);
            AnimationAdditionsHook.register();
            // Leaving a world drops every feature's per-entity state with it.
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> EntityStates.clearAll());
        }
    }
}
