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
 * <p>The linger exists because PAL keeps fading an animation out for a frame or two after the
 * addon has already dropped its pose. Without it the model would snap back to vanilla for exactly
 * those frames, between the addon letting go and EMF resuming.</p>
 */
public final class PauseOverride {

    private PauseOverride() {
    }

    /**
     * How many further frames the pause stays lifted after the last pose disappears. Three is
     * what the Better Combat addon arrived at for its own attack hand-off, which is the longest
     * fade any addon here produces.
     */
    private static final int LINGER_FRAMES = 3;

    private static final Map<UUID, Integer> LINGER = new HashMap<>();

    /**
     * Returns {@code true} if EMF should keep animating this player despite wanting to pause.
     *
     * <p>Also advances the linger countdown, so this must be called once per frame per entity —
     * which is exactly how EMF uses the pause check. It cannot be ticked from the animation hook
     * instead: that hook is one of the things a pause skips.</p>
     */
    public static boolean shouldLiftPause(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        // A pose that is fading back out still needs the hook to run: a paused entity is skipped
        // before it, which would strand the fade halfway and snap the limb after all.
        if (PoseManager.hasAnyPose(uuid) || PoseInterpolator.isActive(uuid)) {
            LINGER.put(uuid, LINGER_FRAMES);
            return true;
        }
        Integer remaining = LINGER.get(uuid);
        if (remaining == null) {
            return false;
        }
        if (remaining <= 1) {
            LINGER.remove(uuid);
        } else {
            LINGER.put(uuid, remaining - 1);
        }
        return true;
    }

    /** Drops linger entries for players no longer in the level. Called by the core cleanup. */
    public static void retainOnly(Collection<UUID> activeUUIDs) {
        LINGER.keySet().retainAll(activeUUIDs);
    }
}
