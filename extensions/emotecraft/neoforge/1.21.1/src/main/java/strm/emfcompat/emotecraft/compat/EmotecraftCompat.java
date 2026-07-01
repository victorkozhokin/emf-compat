package strm.emfcompat.emotecraft.compat;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayer;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Helpers for detecting Emotecraft emote state and which body parts are animated.
 */
public final class EmotecraftCompat {

    private static final float EPSILON = 1e-5f;

    private EmotecraftCompat() {
    }

    /**
     * Returns {@code true} if the player is currently playing an Emotecraft emote.
     */
    public static boolean isPlayingEmote(AbstractClientPlayer player) {
        if (!(player instanceof IEmotePlayerEntity emotePlayer)) {
            return false;
        }
        return emotePlayer.isPlayingEmote();
    }

    /**
     * Returns the set of vanilla-model part names that the currently playing emote actually
     * animates (e.g. {@code "left_arm"}, {@code "body"}). Parts with only default/zero keyframes
     * are omitted so that EMF can keep animating them.
     */
    public static Set<String> getAnimatedParts(AbstractClientPlayer player) {
        if (!(player instanceof IEmotePlayerEntity emotePlayer) || !emotePlayer.isPlayingEmote()) {
            return Collections.emptySet();
        }

        IEmotePlayer emote = emotePlayer.emotecraft$getEmote();
        if (emote == null) {
            return Collections.emptySet();
        }

        KeyframeAnimation data = emote.getData();
        if (data == null || data.getBodyParts() == null) {
            return Collections.emptySet();
        }

        Set<String> animated = new HashSet<>();
        for (var entry : data.getBodyParts().entrySet()) {
            String name = entry.getKey();
            KeyframeAnimation.StateCollection part = entry.getValue();
            if (part == null || !isPartAnimated(part)) {
                continue;
            }

            String coreName = mapPartName(name);
            if (coreName != null) {
                animated.add(coreName);
            }
        }
        return animated;
    }

    private static boolean isPartAnimated(KeyframeAnimation.StateCollection part) {
        return isStateAnimated(part.x)
                || isStateAnimated(part.y)
                || isStateAnimated(part.z)
                || isStateAnimated(part.pitch)
                || isStateAnimated(part.yaw)
                || isStateAnimated(part.roll)
                || isStateAnimated(part.scaleX)
                || isStateAnimated(part.scaleY)
                || isStateAnimated(part.scaleZ);
    }

    private static boolean isStateAnimated(KeyframeAnimation.StateCollection.State state) {
        if (state == null) {
            return false;
        }
        float defaultValue = state.defaultValue;
        for (KeyframeAnimation.KeyFrame frame : state.getKeyFrames()) {
            if (Math.abs(frame.value - defaultValue) > EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static String mapPartName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "head" -> "head";
            case "body", "torso" -> "body";
            case "leftarm", "left_arm" -> "left_arm";
            case "rightarm", "right_arm" -> "right_arm";
            case "leftleg", "left_leg" -> "left_leg";
            case "rightleg", "right_leg" -> "right_leg";
            default -> null;
        };
    }
}
