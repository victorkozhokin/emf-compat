package strm.touchnmotion.interaction;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Where a player stood on a Sable sub-level when something around them was last measured. On a
 * moving one the world position is new every frame, so a "measure again when the player moves"
 * cache never holds there; this one goes by the spot on the deck, the facing on it and its tilt.
 */
public final class DeckSpot {
    private static final double MOVE_SQR = 0.02 * 0.02;
    private static final double FACING_COS = Math.cos(Math.toRadians(2));
    private static final double TILT_COS = Math.cos(Math.toRadians(1));
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private SubLevels.Space space;
    private Vec3 local, facing, up;
    private long at;

    /**
     * Whether the player is where they were, on {@code space}, at most {@code nanos} ago; if not,
     * this spot becomes the one to compare with from now on.
     */
    public boolean same(AbstractClientPlayer player, SubLevels.Space space, long now, long nanos) {
        Vec3 l = space.tickToLocal(player.position());
        Vec3 f = space.directionToLocal(Vec3.directionFromRotation(0, player.yBodyRot));
        Vec3 u = space.directionToLocal(UP);
        boolean same = local != null && space.same(this.space) && now - at < nanos
                && l.distanceToSqr(local) < MOVE_SQR && f.dot(facing) > FACING_COS && u.dot(up) > TILT_COS;
        if (!same) {
            this.space = space;
            local = l;
            facing = f;
            up = u;
            at = now;
        }
        return same;
    }

    /** The first sub-level among {@code spaces}, or {@code null} when there is none. */
    public static SubLevels.Space craft(java.util.List<SubLevels.Space> spaces) {
        for (SubLevels.Space space : spaces) if (!space.isWorld()) return space;
        return null;
    }
}
