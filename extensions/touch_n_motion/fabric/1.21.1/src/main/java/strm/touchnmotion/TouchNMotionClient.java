package strm.touchnmotion;

import net.fabricmc.api.ClientModInitializer;

/** Touch'n Motion on Fabric, the client: the settings, the providers, the hooks. */
public class TouchNMotionClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientInit.run();
        strm.touchnmotion.net.HandsNet.registerClient();
    }
}
