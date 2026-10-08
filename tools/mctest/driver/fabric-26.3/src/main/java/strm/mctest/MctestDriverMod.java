package strm.mctest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class MctestDriverMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (!Driver.enabled()) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Driver.tick());
    }
}
