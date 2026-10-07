package strm.emfcompat.core;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Takes out the height animation libraries add to a crouching player on top of the crouch itself.
 *
 * <p>Vanilla drops a crouching player 2/16 of a block ({@code AvatarRenderer#getRenderOffset}), and
 * an EMF pack that animates the crouch lifts the legs by the same two pixels to keep the feet on the
 * ground — Fresh Animations' player pack does it with {@code -2*var.sneak2}. That pair balances.
 * Animation libraries then move the whole render again from their {@code body} bone, and nothing
 * balances that: Hackers 'n Slashers' sneak poses set it to -4, a quarter of a block, so the player
 * sat three times deeper than vanilla. Measured in game on 1.21.1:</p>
 *
 * <pre>
 *   standing, stance     library shift  0.000   total  0.000
 *   crouched, stance     library shift -0.249   total -0.374
 *   crouched, attacking  library shift -0.028   total -0.153
 * </pre>
 *
 * <p>The last two lines are also the jump: an attack replaces the sneak pose with an animation that
 * does not use the body bone, so the player rose a quarter block for the length of every swing and
 * dropped back after it.</p>
 *
 * <p>So while a player is crouching, the vertical part of what was added to the render between
 * {@code setupRotations} and {@code scale} is removed. That stretch, not {@code setupRotations}
 * alone: since render states Player Animation Library applies the body bone in {@code submit},
 * right before {@code scale}. Its rotation, scale and horizontal movement stay — a lunge still
 * lunges. The removal lingers briefly after standing up, because the libraries fade their sneak
 * pose out over a few ticks (0.17–0.24 s measured) while vanilla's own offset is gone at once;
 * without the linger that fade reads as the player sinking just after standing.</p>
 */
public final class CrouchNormalizer {

    private CrouchNormalizer() {
    }

    /**
     * Config key for the whole crouch fix — this height correction and the crouch-pose repair in
     * {@link EMFCompatAnimationHook}. They mend two layers of the same thing and neither is any use
     * without the other, so there is one switch for both.
     */
    public static final String KEY_ENABLED = "core.crouchFix";

    /** Reused by {@link #normalise}; see the note there. */
    private static final Matrix4f DELTA = new Matrix4f();

    /** Longer than the libraries' sneak fade-out, with room to spare. */
    private static final long LINGER_NANOS = 350_000_000L;

    // Keyed by entity id: a render state carries no UUID, and it is all this sees.
    private static final Map<Integer, Long> LAST_CROUCH = new HashMap<>();

    public static boolean isEnabled() {
        return EMFCompatCore.isCompatEnabled() && EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    /** What {@link #appliesTo} decided for this render. */
    public enum Mode {
        /** Leave the render alone. */
        NONE,
        /** Crouching: any vertical shift from the libraries goes. */
        ALL,
        /** Just stood up: only a leftover downward shift goes, never an upward one. */
        DOWNWARD_ONLY
    }

    /** Decides whether, and how, this render's vertical body shift should be taken out. */
    public static Mode appliesTo(LivingEntityRenderState state) {
        if (!(state instanceof AvatarRenderState avatar) || !isEnabled()) {
            return Mode.NONE;
        }
        long now = System.nanoTime();
        if (avatar.isCrouching) {
            LAST_CROUCH.put(avatar.id, now);
            return Mode.ALL;
        }
        Long last = LAST_CROUCH.get(avatar.id);
        if (last == null) {
            return Mode.NONE;
        }
        if (now - last > LINGER_NANOS) {
            LAST_CROUCH.remove(avatar.id);
            return Mode.NONE;
        }
        return Mode.DOWNWARD_ONLY;
    }

    /**
     * Removes the vertical translation that was added on top of {@code before}, leaving everything
     * else {@code pose} carries.
     *
     * <p>Worked in the frame the entity was placed in: {@code before⁻¹ · pose} is exactly what
     * {@code setupRotations} added, and zeroing its Y translation before multiplying it back on is
     * the same as moving the result back down (or up) along world Y — whatever rotation the libraries
     * applied in between.</p>
     */
    public static void normalise(Matrix4f before, Matrix4f pose, Mode mode) {
        // Scratch, not a new matrix: this runs per crouching player per frame, on the render
        // thread only, and the value never outlives the call.
        Matrix4f delta = DELTA.set(before).invert().mul(pose);
        float dy = delta.m31();
        if (dy == 0f || (mode == Mode.DOWNWARD_ONLY && dy > 0f)) {
            return;
        }
        delta.m31(0f);
        pose.set(before).mul(delta);
    }

    /**
     * Drops entries whose linger has run out. Called by the core cleanup; an entry otherwise only
     * goes when its player is drawn again, which a player who left never is.
     */
    public static void pruneExpired() {
        long now = System.nanoTime();
        Iterator<Long> it = LAST_CROUCH.values().iterator();
        while (it.hasNext()) {
            if (now - it.next() > LINGER_NANOS) {
                it.remove();
            }
        }
    }
}
