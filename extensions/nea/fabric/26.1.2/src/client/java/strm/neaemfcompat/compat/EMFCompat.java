package strm.neaemfcompat.compat;

import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.BurningAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.FreezingAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.HorseAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.BoatAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.EatDrinkAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.HugAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.ItemSwapAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.LookAtItemAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.MapHoldingAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.NarutoRunningAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.PetAnimation;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.utils.EMFEntity;

public class EMFCompat {

    public static final Logger LOGGER = LogManager.getLogger("nea-emf-compat");

    public static boolean shouldPauseForAnimation(BasicAnimation anim) {
        if (anim == null) return false;
        return anim instanceof BoatAnimation
                || anim instanceof HorseAnimation
                || anim instanceof EatDrinkAnimation
                || anim instanceof HugAnimation
                || anim instanceof ItemSwapAnimation
                || anim instanceof MapHoldingAnimation
                || anim instanceof LookAtItemAnimation
                || anim instanceof PetAnimation
                || anim instanceof NarutoRunningAnimation
                || anim instanceof BurningAnimation
                || anim instanceof FreezingAnimation;

    }

    public static void init() {
        try {
            EMFAnimationApi.registerPauseCondition(EMFCompat::shouldForceVanillaModel);
            EMFAnimationApi.registerVanillaModelCondition(EMFCompat::shouldForceVanillaModel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean shouldForceVanillaModel(EMFEntity emfEntity) {
        if (emfEntity.etf$isBlockEntity()) {
            return false;
        }
        Entity entity = (Entity) emfEntity;
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (NEABaseMod.config == null) {
            return false;
        }
        if (NEABaseMod.config.enableHorseLegAnimation) {
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof AbstractHorse) {
                return true;
            }
        }
        if (NEABaseMod.config.freezeArmsInBed && player.isSleeping()) {
            return true;
        }
        return false;
    }

}
