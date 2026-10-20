package strm.touchnmotion.interaction;

import java.util.List;

/**
 * A feature that offers interactions: it looks at the world around the player and returns
 * candidates, nothing more. It never touches a model part and never reads another provider; the
 * {@link InteractionRuntime} decides, smooths and applies. What it has to remember between frames
 * (a target it keeps, an idle timer) goes in {@link InteractionContext#data}, which the runtime
 * clears with the player.
 */
public interface InteractionProvider {

    /** Short stable name: the source of its candidates and the name in the log. */
    String id();

    boolean isEnabled();

    /**
     * Adds this frame's candidates to {@code out}, and says in a word what it decided through
     * {@link InteractionContext#decide} (logged when it changes).
     */
    void collect(InteractionContext context, List<Candidate> out);

    /**
     * What it does, as packs read it: one of {@link strm.touchnmotion.compat.PackStates}' groups
     * ({@code tnm_reach}, {@code tnm_hold}, {@code tnm_lean}, {@code tnm_gesture}, {@code tnm_work},
     * {@code tnm_ride}), weighed by the parts it holds; {@code null} for none.
     */
    default String packState() {
        return null;
    }

    /** Whether it carries on while another mod has the arms - carrying, a gun in hand: a seat carries the body, not the hands. */
    default boolean armsFree() {
        return false;
    }

    /** Whether it moves only the head, and so sits out while another mod has the head instead of the arms. */
    default boolean headOnly() {
        return false;
    }

    /** Whether the body support it set up is kept for it from one frame to the next, as its own (see the runtime's support pass). */
    default boolean holdsBody() {
        return false;
    }

    /**
     * The arms the game's own use of them takes while this provider has the player - an item used, a swing -
     * instead of both; {@code null} to leave it to the runtime's rule.
     */
    default java.util.Set<Effector> reserves(net.minecraft.client.player.AbstractClientPlayer player) {
        return null;
    }
}
