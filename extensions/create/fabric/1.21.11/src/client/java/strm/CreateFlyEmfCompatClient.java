package strm;

import net.fabricmc.api.ClientModInitializer;
import strm.createFlyEmfCompat.compat.EMFCompat;

public class CreateFlyEmfCompatClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EMFCompat.init();
    }
}
