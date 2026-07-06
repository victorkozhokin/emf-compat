package strm.emfcompat.bettercombat.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps EMF unpaused for a few frames after Better Combat stops considering the player attacking.
 *
 * <p>EMF pauses its animations whenever Player Animator is active. Better Combat's attack stack and
 * Player Animator's manager can stay active one or two frames longer than {@link BetterCombatCompat#getAttackHand}
 * returns non-null, which creates a single frame where the model snaps back to vanilla/BC before
 * EMF resumes. This tracker maintains a short cooldown so that EMF keeps animating through that
 * hand-off.</p>
 */
public final class AttackPauseOverride {

    private AttackPauseOverride() {
    }

    private static final int LINGER_FRAMES = 3;
    private static final Map<UUID, Integer> COOLDOWN_TICKS = new HashMap<>();

    /**
     * Marks the given player as currently attacking, resetting the post-attack cooldown.
     */
    public static void markAttackActive(UUID uuid) {
        COOLDOWN_TICKS.put(uuid, LINGER_FRAMES);
    }

    /**
     * Decrements the post-attack cooldown for the given player. Call this every frame even when
     * no attack is active.
     */
    public static void tickCooldown(UUID uuid) {
        COOLDOWN_TICKS.computeIfPresent(uuid, (u, ticks) -> ticks <= 1 ? null : ticks - 1);
    }

    /**
     * Returns {@code true} if EMF should remain unpaused for this player.
     */
    public static boolean isUnpaused(UUID uuid) {
        return COOLDOWN_TICKS.containsKey(uuid);
    }
}
