package strm.emfcompat.animationadditions.interaction;

import net.minecraft.client.player.AbstractClientPlayer;
import strm.emfcompat.core.ik.IKFrame;

import java.util.Map;
import java.util.function.Supplier;

/**
 * What a provider gets for one frame of one player: the player, the model's space and a place to
 * keep its own state between frames.
 */
public final class InteractionContext {

    private final AbstractClientPlayer player;
    private final IKFrame frame;
    private final long now;
    private final double dt;
    private final Map<String, Object> data;
    private final Map<String, String> decided;
    private String provider;
    private boolean armsClaimed;

    InteractionContext(AbstractClientPlayer player, IKFrame frame, long now, double dt,
                       Map<String, Object> data, Map<String, String> decided) {
        this.player = player;
        this.frame = frame;
        this.now = now;
        this.dt = dt;
        this.data = data;
        this.decided = decided;
    }

    void enter(String provider) {
        this.provider = provider;
    }

    public AbstractClientPlayer player() {
        return player;
    }

    public IKFrame frame() {
        return frame;
    }

    /** {@link System#nanoTime()} of this frame. */
    public long now() {
        return now;
    }

    /** Seconds since the last solve of this player, 0 on the first. */
    public double dt() {
        return dt;
    }

    /** This provider's own state for this player, made on first use. */
    @SuppressWarnings("unchecked")
    public <T> T data(Supplier<T> make) {
        return (T) data.computeIfAbsent(provider, k -> make.get());
    }

    /**
     * The arms are this provider's even through a swing: the swing is part of what it shows
     * (a hand pressing a button swings it).
     */
    public void claimArms() {
        armsClaimed = true;
    }

    boolean armsClaimed() {
        return armsClaimed;
    }

    /** What this provider decided, in a word; the runtime logs it when it changes. */
    public void decide(String what) {
        decided.put(provider, what);
    }
}
