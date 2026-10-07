package strm.emfcompat.animationadditions.compat;

import net.minecraft.client.player.AbstractClientPlayer;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.SavedPoses;

import java.util.Map;

/**
 * Another addon holds the player's whole body - the torso and both legs - while they are off the
 * ground: hanging from a chain by Create's skyhook is the case. That pose is rigid and complete;
 * the lean with the motion, the inertia and the feet looking for ground only pull it apart, so
 * this addon adds nothing for as long as it lasts. (Sitting is not this: a rider is carried.)
 */
public final class WholeBody {
    private WholeBody() {
    }

    public static boolean held(AbstractClientPlayer player) {
        if (player.onGround() || player.isPassenger()) return false;
        Map<String, SavedPoses> sources = PoseManager.entitySavedPosesBySource.get(player.getUUID());
        if (sources == null || sources.isEmpty()) return false;
        for (SavedPoses poses : sources.values()) {
            var parts = poses.parts();
            if (parts != null && parts.containsKey("body") && parts.containsKey("left_leg") && parts.containsKey("right_leg")) return true;
        }
        return false;
    }

    private static final strm.emfcompat.animationadditions.blockuse.ModFailures SEAT =
            new strm.emfcompat.animationadditions.blockuse.ModFailures("Take a Seat");
    private static java.lang.reflect.Method layer, active;
    private static Object sitLayer;
    private static boolean looked;

    /**
     * Sat down with Take a Seat: its pose is put back over the whole body but the head after the
     * pack has animated, so whatever this addon moves the body by is undone - and a head carried
     * along with the body is left behind, off the neck. Only the head's own turn is added then.
     */
    public static boolean seated(AbstractClientPlayer player) {
        if (SEAT.off()) return false;
        try {
            if (!looked) {
                looked = true;
                if (!net.neoforged.fml.ModList.get().isLoaded("takeaseat")) return false;
                sitLayer = Class.forName("com.takeaseat.client.TakeASeatClient").getField("SIT_LAYER").get(null);
                layer = Class.forName("com.zigythebird.playeranim.api.PlayerAnimationAccess")
                        .getMethod("getPlayerAnimationLayer", AbstractClientPlayer.class, net.minecraft.resources.ResourceLocation.class);
                active = layer.getReturnType().getMethod("isActive");
            }
            if (active == null) return false;
            Object animation = layer.invoke(null, player, sitLayer);
            return animation != null && (Boolean) active.invoke(animation);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            active = null;
            return false;
        } catch (Throwable t) {
            SEAT.failed(t);
            return false;
        }
    }
}
