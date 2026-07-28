package strm.emfcompat.gliders.compat;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import net.minecraft.world.entity.player.Player;
import net.venturecraft.gliders.client.animation.AnimatedPlayer;
import net.venturecraft.gliders.util.GliderUtil;

/**
 * Compatibility with the Gliders mod (vc_gliders, Jeryn99). Only referenced
 * behind {@link GlidingState#isVcGlidersLoaded()}, so this class is never
 * loaded when the mod is absent.
 */
public final class VCGlidersCompat {

    /**
     * Length of the deploy part of the {@code gliding} animation, in ticks.
     *
     * <p>The animation opens with a dense burst of keyframes — the glider snapping open and the
     * body and legs swinging under it — which ends around 1.6 s; after that only sparse keyframes
     * remain (2.4 s, 3.1 s, 4.4 s, 7.4 s …), i.e. a slow idle sway. 40 ticks (2 s) covers the
     * swing and lands in the quiet part, so handing over to the flight animation there is the
     * least visible.</p>
     */
    private static final int DEPLOY_TICKS = 40;

    private VCGlidersCompat() {}

    /**
     * Whether the player is currently gliding with an active VC glider. Uses the
     * mod's own ground-truth check; the equipped glider stack (and its glide
     * component) is synced for remote players. Never throws.
     */
    public static boolean isGliding(Player player) {
        if (player == null) return false;
        try {
            return GliderUtil.isGlidingWithActiveGlider(player);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether the glider is still in its opening animation.
     *
     * <p>VC Gliders plays a single {@code gliding} Player Animator animation, started fresh on its
     * own layer the moment gliding begins, so the animation's own tick counter is the deploy
     * timer. Reading it (rather than timing the gliding flag ourselves) keeps the two in sync if
     * the animation is restarted, and works for remote players — the layer lives on every client
     * player. Never throws: the mod may change how it drives the layer.</p>
     */
    public static boolean isDeploying(Player player) {
        if (player == null) return false;
        try {
            if (!(player instanceof AnimatedPlayer animated)) return false;
            ModifierLayer<IAnimation> layer = animated.gliders_getModifierLayer();
            if (layer == null) return false;
            if (!(layer.getAnimation() instanceof KeyframeAnimationPlayer animation)) return false;
            return animation.isActive() && animation.getTick() < DEPLOY_TICKS;
        } catch (Throwable t) {
            return false;
        }
    }
}
