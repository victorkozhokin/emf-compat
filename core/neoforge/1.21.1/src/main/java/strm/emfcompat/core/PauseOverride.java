package strm.emfcompat.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decides whether EMF's animation pause should be lifted for a player.
 *
 * <p>EMF pauses a player's pack animation while Player Animation Library has anything playing —
 * its {@code PALCompat} pause listener asks the whole animation <em>manager</em> whether it is
 * active, not the individual layer. Any mod driving a player animation through PAL therefore
 * freezes everything the pack animates on that player: body, legs and head alike.</p>
 *
 * <p>That is a problem for every addon here, because a paused entity is skipped before EMF's
 * animation hooks run — which is where the core restores captured poses. So a paused player is a
 * player whose captured pose can never be put back. The rule is simply: if any addon has a pose
 * saved for this player, it means to restore it, and the pause has to go.</p>
 *
 * <p>An addon that genuinely wants EMF paused — Not Enough Animations' frozen arms in bed is the
 * remaining case — registers its own pause condition and saves no pose for that state, so nothing
 * here lifts it. It also registers a vanilla-model condition, which is what actually shows the
 * pose and is independent of pausing. The Create addon used to work that way for the skyhook and
 * now captures the hang pose instead, precisely so the pack keeps animating the face.</p>
 *
 * <p>A pose that is fading out (see {@link PoseInterpolator}) counts as a pose here: the fade runs
 * from the same hook, and a pause landing halfway through it would snap the limb after all.</p>
 *
 * <p>The linger exists because PAL keeps fading an animation out after the addon has already
 * dropped its pose - Better Combat's attack fades for about four ticks. Without it EMF would pause
 * for exactly that fade and the model would flicker between the addon letting go and EMF resuming.
 * It is measured in time, not in calls: on 1.21.11 and later EMF asks about the pause many times a
 * frame, and a linger counted in calls ran out within a single frame.</p>
 */
public final class PauseOverride {

    private PauseOverride() {
    }

    /**
     * How long the pause stays lifted after the last pose disappears. Better Combat's attack fade,
     * the longest any addon here leaves behind, takes about 200 ms.
     */
    private static final long LINGER_NANOS = 500_000_000L;

    /** When the linger of each player runs out, in {@link System#nanoTime()}. */
    private static final Map<UUID, Long> LINGER = new HashMap<>();

    /**
     * Returns {@code true} if EMF should keep animating this player despite wanting to pause.
     *
     * <p>Called from EMF's pause check, which a pause does not skip - unlike the animation hook,
     * which is why the linger cannot be kept there.</p>
     */
    public static boolean shouldLiftPause(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        long now = System.nanoTime();
        // A pose that is fading back out still needs the hook to run: a paused entity is skipped
        // before it, which would strand the fade halfway and snap the limb after all.
        if (PoseManager.hasAnyPose(uuid) || PoseInterpolator.isActive(uuid)) {
            LINGER.put(uuid, now + LINGER_NANOS);
            return true;
        }
        Long until = LINGER.get(uuid);
        if (until == null) {
            return false;
        }
        if (now - until >= 0) {
            LINGER.remove(uuid);
            return false;
        }
        return true;
    }

    /** Drops linger entries for players no longer in the level. Called by the core cleanup. */
    public static void retainOnly(Collection<UUID> activeUUIDs) {
        LINGER.keySet().retainAll(activeUUIDs);
    }
}
