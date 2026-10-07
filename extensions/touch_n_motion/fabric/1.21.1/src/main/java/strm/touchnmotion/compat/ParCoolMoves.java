package strm.touchnmotion.compat;

import java.util.Collection;
import java.util.Set;

/**
 * ParCool 4's animations, by the path of their id, that this addon keeps out of. Found by running
 * every move with the addon left on and measuring what it changed in the drawn pose; the table is
 * in docs/parcool-additions-gate-2026-10-06.md.
 */
final class ParCoolMoves {
    static final Set<String> WEAK = Set.of(
            // On the ground but not on the feet: the foot IK and the stance have nothing to stand on.
            "slide", "crawl", "hide_in_block_crawl", "breakfall_forward",
            "dodge_right", "dodge_front", "dodge_back",
            // In the air head over heels, or thrown off a wall.
            "trick_jump_back", "trick_jump_forward", "wall_jump", "castaway",
            "dive", "dive_in_air", "dive_into_water", "skydive", "skydive_in_air",
            // The hands are ParCool's: on a ledge, a wall, a bar, a rope.
            "vault_forward", "vault_side", "hang_on", "climb_up", "climb_up_jump", "horizontal_wall_run",
            "hang_down", "hang_down_jump_forward", "hang_down_jump_backward", "ride_zipline", "grapple");

    /** The run-up to a wall run: the body is not turned to "fit the gap" beside the wall. */
    static final String FAST_RUN = "fast_run";

    private ParCoolMoves() {
    }

    /** Whether, with these animations running, the addon stands down altogether. */
    static boolean yields(Collection<String> running) {
        for (String name : running) if (WEAK.contains(name)) return true;
        return false;
    }
}
