package strm.emfcompat.animationadditions;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;
import strm.emfcompat.animationadditions.horsesync.HorseSync;

/**
 * Animation Additions: small player-animation features that are not a compat layer for any one
 * mod, gathered in one addon. Each one registers its own toggles in the shared config section and
 * lives in its own package:
 *
 * <ul>
 *   <li>{@code horsesync} - the rider follows a horse animated by EMF, with a riding pose;</li>
 *   <li>{@code footgrounding} - Foot IK: both feet stand on uneven ground.</li>
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
        }
    }
}
