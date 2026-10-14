package strm.touchnmotion;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** A recorded copy belongs only to that target, player and render frame; numerical pose similarity is irrelevant. */
final class CopiedPoseTracker {
    private record Copy(UUID player, float frame) {}
    private final Map<Object, Copy> copies = new WeakHashMap<>();
    void mark(UUID player, Object target, float frame) { copies.put(target, new Copy(player, frame)); }
    boolean copied(UUID player, Object target, float frame) {
        Copy copy = copies.get(target);
        return copy != null && copy.player.equals(player) && copy.frame == frame;
    }
    void clear() { copies.clear(); }
}
