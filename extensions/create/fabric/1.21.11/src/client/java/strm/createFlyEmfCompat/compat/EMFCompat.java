package strm.createFlyEmfCompat.compat;

import strm.CreateFlyEmfCompatClient;
import traben.entity_model_features.EMFAnimationApi;
//old method (not working)
//import traben.entity_model_features.utils.EMFEntity;

public class EMFCompat {
    public static void init() {
        try {
            // The conditions are registered once, so the config is read inside them: the toggle
            // then takes effect immediately instead of only on the next launch.
            EMFAnimationApi.registerPauseCondition(emfEntity -> {
                if (emfEntity.etf$isBlockEntity()) {
                    return false;
                }

                return CreateFlyEmfCompatClient.isEnabled()
                        && SkyhookHelper.isSkyhooking(emfEntity.etf$getUuid());
            });
            EMFAnimationApi.registerVanillaModelCondition(emfEntity -> {
                if (emfEntity.etf$isBlockEntity()) {
                    return false;
                }

                return CreateFlyEmfCompatClient.isEnabled()
                        && SkyhookHelper.isSkyhooking(emfEntity.etf$getUuid());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
