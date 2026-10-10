package strm.touchnmotion.interaction;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * From 1.21.11 on a renderer draws from a render state, which does not say whose it is. The
 * state is filled from the entity first ({@code mixin/modern/StateEntityMixin} notes the pair
 * there), so what is drawn from it can ask.
 */
public final class DrawnEntities {
    private static final Map<Object, LivingEntity> BY_STATE = new WeakHashMap<>();

    private DrawnEntities() {
    }

    public static void filled(Object state, LivingEntity from) {
        BY_STATE.put(state, from);
    }

    /**
     * Whether this is an entity of the world and not a stand-in made to be drawn - a mod shows a player wearing
     * what the mouse is over with one, under the player's own id, and what it wears is not what the player does.
     */
    public static boolean inWorld(net.minecraft.world.entity.Entity entity) {
        return entity.level().getEntity(entity.getId()) == entity;
    }

    /** The entity {@code state} was last filled from; {@code null} if none, or gone from the world. */
    public static LivingEntity of(Object state) {
        LivingEntity entity = BY_STATE.get(state);
        return entity == null || entity.isRemoved() ? null : entity;
    }
}
