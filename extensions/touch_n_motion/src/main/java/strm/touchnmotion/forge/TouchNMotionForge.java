package strm.touchnmotion.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import strm.touchnmotion.TouchNMotionMod;

/** Touch'n Motion on Forge: the entry point, and the one thing a server has of the mod - the channel it passes players' hands on by. */
@Mod(TouchNMotionMod.MOD_ID)
public class TouchNMotionForge {

    public TouchNMotionForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ForgeNet.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.run(modEventBus);
        }
    }
}
