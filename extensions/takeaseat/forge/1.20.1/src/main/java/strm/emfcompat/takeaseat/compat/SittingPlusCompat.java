package strm.emfcompat.takeaseat.compat;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Pair;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import strm.emfcompat.takeaseat.mixin.AnimationStackAccessor;
import strm.emfcompat.takeaseat.mixin.KeyframeAnimationPlayerAccessor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detects whether Sitting+ is currently posing a player.
 *
 * <p>Sitting+ exposes no state API — everything is private, and the local player's animation
 * player is a {@code static} field, so it says nothing about other players. What it does do,
 * for the local player and for remote ones alike (via its sit packets), is push a
 * {@link KeyframeAnimationPlayer} onto that player's Player Animator stack. So the stack is the
 * one place where every case is visible.</p>
 *
 * <p>A plain "is the stack active" check would be wrong here: Better Combat and TACZ drive their
 * own animations through the same kosmx stack on this version, and their poses belong to their own
 * addons. The layer is therefore matched by identity — every animation Player Animator has loaded
 * under the {@code sittingplus} namespace is collected once, and only those count as sitting.</p>
 */
public final class SittingPlusCompat {

    private static final String SITTING_PLUS_ID = "sittingplus";
    private static final boolean LOADED = ModList.get().isLoaded(SITTING_PLUS_ID);

    /** UUIDs of every loaded Sitting+ animation; rebuilt lazily after a resource reload. */
    private static Set<UUID> sittingAnimationIds;
    private static int knownRegistrySize = -1;

    private SittingPlusCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isSitting(AbstractClientPlayer player) {
        if (!LOADED || player == null) return false;
        try {
            AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
            if (stack == null || !stack.isActive()) return false;

            Set<UUID> ids = sittingAnimationIds();
            if (ids.isEmpty()) return false;

            for (Pair<Integer, IAnimation> layer : ((AnimationStackAccessor) stack).emfcompat$getLayers()) {
                IAnimation animation = layer.getRight();
                if (!(animation instanceof KeyframeAnimationPlayer player_) || !animation.isActive()) {
                    continue;
                }
                KeyframeAnimation data = ((KeyframeAnimationPlayerAccessor) player_).emfcompat$getData();
                if (data != null && ids.contains(data.getUuid())) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Set<UUID> sittingAnimationIds() {
        Map<ResourceLocation, KeyframeAnimation> all = PlayerAnimationRegistry.getAnimations();
        // The registry is replaced on resource reload; its size is a cheap staleness signal.
        if (sittingAnimationIds == null || all.size() != knownRegistrySize) {
            Set<UUID> ids = new HashSet<>();
            for (Map.Entry<ResourceLocation, KeyframeAnimation> e : all.entrySet()) {
                if (SITTING_PLUS_ID.equals(e.getKey().getNamespace()) && e.getValue() != null) {
                    ids.add(e.getValue().getUuid());
                }
            }
            sittingAnimationIds = ids;
            knownRegistrySize = all.size();
        }
        return sittingAnimationIds;
    }
}
