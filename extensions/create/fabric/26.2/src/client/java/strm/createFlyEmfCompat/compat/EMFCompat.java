package strm.createFlyEmfCompat.compat;

import traben.entity_model_features.EMFAnimationApi;

public class EMFCompat {
    public static void init() {
        try {
            EMFAnimationApi.registerPauseCondition(emfEntity -> {
                if (emfEntity.etf$isBlockEntity()) {
                    return false;
                }

                return SkyhookHelper.isSkyhooking(emfEntity.etf$getUuid());
            });
            EMFAnimationApi.registerVanillaModelCondition(emfEntity -> {
                if (emfEntity.etf$isBlockEntity()) {
                    return false;
                }

                return SkyhookHelper.isSkyhooking(emfEntity.etf$getUuid());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
