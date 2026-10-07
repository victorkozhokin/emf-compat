package strm.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class MctestDriverMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (!Driver.enabled()) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Driver.tick());
        ServerTickEvents.START_SERVER_TICK.register(server -> CraftProbe.tick());
        ServerTickEvents.END_SERVER_TICK.register(server -> CraftProbe.ropeTick());
    }
}
