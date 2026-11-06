package strm.touchnmotion.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import strm.touchnmotion.net.HandsWork;

/** Watches the native screen even when first person does not render its owner's model. */
public final class MenuWork {
    private BlockPos pos;
    private BlockTarget target;
    private Object previous;

    public void clear() { pos = null; target = null; previous = null; }

    public HandsWork sample(AbstractClientPlayer player, BlockPos at) {
        if (at == null) { clear(); return null; }
        var block = player.level().getBlockState(at);
        BlockTarget current = BlockUse.menuTarget(block);
        if (current == null) { clear(); return null; }
        Object now = current.snapshot(player, player.level(), at, block);
        BlockTarget.Gesture gesture = at.equals(pos) && current == target && previous != null && !previous.equals(now)
                ? current.changed(at, previous, now) : null;
        pos = at.immutable();
        target = current;
        previous = now;
        return gesture == null ? null : new HandsWork(0, pos, gesture.motion().ordinal(), gesture.spot().point(), gesture.spot().out(), gesture.sweep());
    }
}
