package strm.emfcompat.carryon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.model.geom.ModelPart;
import strm.emfcompat.core.PoseSnapshot;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks Carry On render state that needs to be shared across mixins.
 *
 * <p>Body-part pose deltas are delegated to {@link BodyPartSync} in the core
 * module. This class only keeps Carry-On-specific state: the set of entities
 * being carried in the current render pass, used by the registered EMF pause condition.</p>
 */
public final class CarryOnRenderState {

    private CarryOnRenderState() {
    }

    // Entities currently being rendered as carried by Carry On.
    // EMF uses this set via the registered animation-pause condition.
    private static final Set<UUID> CARRIED_ENTITIES = Collections.newSetFromMap(new HashMap<>());
    private static final Map<UUID, Map<ModelPart, PoseSnapshot>> FROZEN_POSES = new HashMap<>();
    private static final Map<UUID, Long> LAST_CARRIED_TICK = new HashMap<>();
    // A mob not drawn in hands for this long has been put down; its state is dropped.
    private static final long FORGET_AFTER_TICKS = 40;

    static {
        try {
            EMFAnimationApi.registerAnimationHook(new EMFAnimationApi.EMFAnimationHook() {
                @Override
                public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
                    UUID uuid = context.activeState() == null ? null : context.activeState().uuid();
                    if (uuid != null && CARRIED_ENTITIES.contains(uuid)
                            && EMFCarryOnClient.isEnabled() && EMFCarryOnClient.pauseCarriedAnimations()) {
                        freeze(uuid, context.animatingModelRoot());
                    }
                }
            });
        } catch (Exception ignored) {
            // EMF is a required dependency, so this should not happen.
        }
    }

    /**
     * Marks the given entity as being carried this frame.
     * EMF's registered pause condition freezes the animation for the duration of
     * the render pass while preserving the resource-pack model and matching texture.
     */
    public static void markCarried(Entity entity) {
        if (entity == null) return;
        UUID uuid = entity.getUUID();
        if (uuid == null) return;

        // Carry On builds a fresh copy of the mob from its NBT every frame, and a fresh entity's
        // clock is 0. EMF animates from that clock, so Animated showed a mob with time standing
        // still, and Frozen could never tell a new pick-up from the carry before it. The level's
        // clock runs for both: the copy ages like a living mob, and a gap means it was put down.
        long now = entity.level().getGameTime();
        entity.tickCount = (int) now;
        Long last = LAST_CARRIED_TICK.put(uuid, now);
        if (last == null || now - last > 2 || !EMFCarryOnClient.pauseCarriedAnimations()) {
            FROZEN_POSES.remove(uuid);
        }
        CARRIED_ENTITIES.add(uuid);

        LAST_CARRIED_TICK.values().removeIf(tick -> now - tick > FORGET_AFTER_TICKS);
        FROZEN_POSES.keySet().retainAll(LAST_CARRIED_TICK.keySet());
    }

    private static void freeze(UUID uuid, EMFModelPartRoot root) {
        Map<ModelPart, PoseSnapshot> frozen = FROZEN_POSES.get(uuid);
        if (frozen == null || !frozen.containsKey(root)) {
            Map<ModelPart, PoseSnapshot> captured = new IdentityHashMap<>();
            root.getAllParts().forEach(part -> captured.put(part, new PoseSnapshot(part)));
            FROZEN_POSES.put(uuid, captured);
            return;
        }
        frozen.forEach((part, pose) -> pose.apply(part));
    }

    /**
     * Makes the carried copy's "previous" and "current" state agree, so what is drawn is what Carry
     * On meant to draw, and EMF reads the same. Runs in both Frozen and Animated.
     *
     * <p>Carry On draws the copy at partial tick 0, which takes the previous-frame values, and
     * sets those itself: the rotations to 0 (so the mob faces away from the player) and, in third
     * person, the old position to 0. The current values are whatever the mob had in the world when
     * it was picked up. The two directions matter:</p>
     * <ul>
     *   <li>Rotations: current from previous. Copying the other way overwrote Carry On's zeros with
     *       the mob's world yaw, so a mob picked up while facing sideways was drawn sideways - in
     *       first person, its body filled the screen.</li>
     *   <li>Position: previous from current. Carry On's old position of 0 against the real one reads
     *       to EMF as an enormous speed, and the legs run.</li>
     * </ul>
     */
    public static void stabilizeAnimated(Entity entity) {
        // Both modes: the rotations are not about animation - Carry On 26.1.2 zeroes only the
        // previous body turn, so even a Frozen mob was drawn half turned at its real partial tick.
        if (entity == null || !EMFCarryOnClient.isEnabled()) return;
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.setXRot(entity.xRotO);
        entity.setYRot(entity.yRotO);
        if (entity instanceof LivingEntity living) {
            living.setYHeadRot(living.yHeadRotO);
            living.yBodyRot = living.yBodyRotO;
        }
    }

    /**
     * Clears the carried-entity tracking set at render-pass boundaries.
     */
    public static void clearCarriedEntities() {
        CARRIED_ENTITIES.clear();
    }
}
