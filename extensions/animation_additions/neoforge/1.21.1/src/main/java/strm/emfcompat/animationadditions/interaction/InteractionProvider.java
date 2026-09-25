package strm.emfcompat.animationadditions.interaction;

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
}
