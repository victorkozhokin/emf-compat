package strm.mctest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "mctest_driver", dist = Dist.CLIENT)
public final class MctestDriverMod {

    public MctestDriverMod() {
        if (!Driver.enabled()) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> Driver.tick());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) -> CraftProbe.tick());
    }
}
