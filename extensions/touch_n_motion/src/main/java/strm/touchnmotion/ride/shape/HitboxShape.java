package strm.touchnmotion.ride.shape;

import net.minecraft.world.entity.Entity;
import strm.touchnmotion.ride.MountShape;

/**
 * Any mount, from its hitbox alone: the barrel taken as this share of the hitbox's width (the
 * vanilla horse's barrel is 0.8 of its 1.4-block hitbox drawn at 1.1, about 0.4 of it from the
 * middle). No hooves - nothing to stand on them by.
 */
public final class HitboxShape {

    static final float SHARE = 0.4f;

    private HitboxShape() {
    }

    public static MountShape measure(Entity mount) {
        return new MountShape(mount.getBbWidth() * 16f * SHARE, null, 24f, 1f, "hitbox");
    }
}
