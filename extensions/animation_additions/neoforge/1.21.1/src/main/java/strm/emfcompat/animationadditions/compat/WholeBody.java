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
}
