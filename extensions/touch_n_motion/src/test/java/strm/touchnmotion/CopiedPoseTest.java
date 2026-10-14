package strm.touchnmotion;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CopiedPoseTest {
    @Test void copyOwnershipIsScopedToTheTargetPlayerAndRenderFrame() {
        Object armour = new Object(), otherArmour = new Object();
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        CopiedPoseTracker tracker = new CopiedPoseTracker();
        tracker.mark(player, armour, 10);
        assertTrue(tracker.copied(player, armour, 10));
        assertFalse(tracker.copied(other, armour, 10));
        assertFalse(tracker.copied(player, armour, 11));
        assertFalse(tracker.copied(player, otherArmour, 10));
        tracker.mark(other, armour, 10);
        assertFalse(tracker.copied(player, armour, 10));
        tracker.clear();
        assertFalse(tracker.copied(other, armour, 10));
    }
}
