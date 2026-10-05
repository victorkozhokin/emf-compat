package strm.emfcompat.animationadditions.interaction;

import net.minecraft.core.BlockPos;

/** Block identity excludes animated block-state properties (wheel angle, open leaf, etc.).
 * A fresh sub-level transform still identifies the same physical contact. */
public final class ContactTarget {
    private final SubLevels.Space space;
    private final BlockPos block;
    private final Object kind;
    public ContactTarget(SubLevels.Space space, BlockPos block, Object kind) {
        this.space=space;this.block=block.immutable();this.kind=kind;
    }
    @Override public boolean equals(Object other) {
        return other instanceof ContactTarget t && space.same(t.space) && block.equals(t.block) && kind.equals(t.kind);
    }
    @Override public int hashCode() { return 31*block.hashCode()+kind.hashCode(); }
}
