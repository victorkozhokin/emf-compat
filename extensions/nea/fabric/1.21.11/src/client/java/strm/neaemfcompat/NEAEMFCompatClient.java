package strm.neaemfcompat;

import net.fabricmc.api.ClientModInitializer;
import strm.neaemfcompat.compat.EMFCompat;

public class NEAEMFCompatClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EMFCompat.init();
    }
}
