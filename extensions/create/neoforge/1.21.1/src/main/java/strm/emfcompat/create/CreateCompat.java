package strm.emfcompat.create;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import traben.entity_model_features.EMFAnimationApi;

/**
 * Create integration. Registers EMF pause/vanilla model conditions for Create skyhook
 * and (optionally) Create: Grappling Hooks.
 */
public final class CreateCompat {

    private CreateCompat() {
    }

    /**
     * Returns true when the player is performing a Create activity (skyhook or grapple)
     * that should suppress NEA's item-swap animation.
     */
    public static boolean shouldDisableItemSwap(Player player) {
        if (SkyhookHelper.isSkyhooking(player.getUUID())) return true;
        if (ModList.get().isLoaded("addon_gancho") && GrappleHookHelper.isGrappling(player)) return true;
        return false;
    }

    public static void init() {
        try {
            EMFAnimationApi.registerPauseCondition(emfEntity -> {
                if (emfEntity.etf$isBlockEntity()) return false;
                return SkyhookHelper.isSkyhooking(emfEntity.etf$getUuid());
            });
            EMFAnimationApi.registerVanillaModelCondition(emfEntity -> {
                if (emfEntity.etf$isBlockEntity()) return false;
                return SkyhookHelper.isSkyhooking(emfEntity.etf$getUuid());
            });

            if (ModList.get().isLoaded("addon_gancho")) {
                EMFAnimationApi.registerPauseCondition(emfEntity -> {
                    if (emfEntity.etf$isBlockEntity()) return false;
                    Entity entity = (Entity) emfEntity;
                    return entity instanceof Player player && GrappleHookHelper.isGrappling(player);
                });
                EMFAnimationApi.registerVanillaModelCondition(emfEntity -> {
                    if (emfEntity.etf$isBlockEntity()) return false;
                    Entity entity = (Entity) emfEntity;
                    return entity instanceof Player player && GrappleHookHelper.isGrappling(player);
                });
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
