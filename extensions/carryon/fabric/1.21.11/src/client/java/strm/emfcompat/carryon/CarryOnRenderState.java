package strm.emfcompat.carryon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.model.geom.ModelPart;
import strm.emfcompat.core.BodyPartSync;
import strm.emfcompat.core.PoseSnapshot;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFState;
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
 * being carried this frame, used by the registered EMF vanilla-model condition.</p>
 */
public final class CarryOnRenderState {

    private CarryOnRenderState() {
    }

    // Entities currently being rendered as carried by Carry On.
    // EMF uses this set via the registered vanilla-model condition.
    private static final Set<UUID> CARRIED_ENTITIES = Collections.newSetFromMap(new HashMap<>());
    private static final Map<UUID, Map<ModelPart, PoseSnapshot>> FROZEN_POSES = new HashMap<>();
    private static final Map<UUID, Long> LAST_CARRIED_TICK = new HashMap<>();
    // EMF's frame counter at the last time each mob was drawn in hands.
    private static final Map<UUID, Float> MARKED_FRAME = new HashMap<>();
    // A mob not drawn in hands for this long has been put down; its state is dropped.
    private static final long FORGET_AFTER_TICKS = 40;

    static {
        try {
            EMFAnimationApi.registerAnimationHook(new EMFAnimationApi.EMFAnimationHook() {
                @Override
                public boolean onAnimationStart(AnimationContext context, boolean cancelled) {
                    // Carry On draws the mob inside its carrier's own draw, and EMF then animates that mob's
                    // model once more as the carrier's - first thing in the frame, before the mob's own pass:
                    // the mob's animation then runs on the player's variables and writes its own into
                    // them - a villager's "swim", "walk" and "run" are the player pack's too, and the
                    // player was posed by them. That pass is called off, and the mob drawn as its own
                    // pass left it.
                    if (!EMFCarryOnClient.isEnabled() || context.activeState() == null) return true;
                    Map<ModelPart, PoseSnapshot> own = ownPose(context.animatingModelRoot());
                    if (own == null || !(context.activeState().emfEntity() instanceof net.minecraft.world.entity.player.Player)) return true;
                    own.forEach((part, pose) -> pose.apply(part));
                    return false;
                }

                @Override
                public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
                    UUID uuid = context.activeState() == null ? null : context.activeState().uuid();
                    // Not CARRIED_ENTITIES: from 1.21.11 on, a render is extracted inside Carry On's
                    // draw call and animated again when the frame is actually drawn, after that call
                    // has returned and cleared the set. The second pass is the one on screen, so a
                    // mob marked at any point of this frame counts for the whole frame.
                    if (uuid != null && carriedThisFrame(uuid)
                            && EMFCarryOnClient.isEnabled() && EMFCarryOnClient.pauseCarriedAnimations()) {
                        freeze(uuid, context.animatingModelRoot());
                    }
                    // The pose a carried mob's model was left in by its own pass, for the pass called off above.
                    // A carried player is left out: its model is its carrier's too.
                    if (uuid != null && carriedThisFrame(uuid) && EMFCarryOnClient.isEnabled()
                            && !(context.activeState().emfEntity() instanceof net.minecraft.world.entity.player.Player)) {
                        keepOwnPose(context.animatingModelRoot());
                    }
                }
            });
        } catch (Exception ignored) {
            // EMF is a required dependency, so this should not happen.
        }
    }

    /**
     * Marks the given entity as being carried this frame.
     * EMF's registered vanilla-model condition will pick this up and force the
     * vanilla model for the duration of the render pass.
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
        MARKED_FRAME.put(uuid, EMFState.getFrameCounter());

        LAST_CARRIED_TICK.values().removeIf(tick -> now - tick > FORGET_AFTER_TICKS);
        FROZEN_POSES.keySet().retainAll(LAST_CARRIED_TICK.keySet());
        MARKED_FRAME.keySet().retainAll(LAST_CARRIED_TICK.keySet());
    }

    /** Whether the mob was drawn in hands during the frame being drawn now. */
    private static boolean carriedThisFrame(UUID uuid) {
        Float frame = MARKED_FRAME.get(uuid);
        return frame != null && frame == EMFState.getFrameCounter();
    }

    // The models last animated as a carried mob's own, with the pose that pass left them in and its frame.
    private static final Map<EMFModelPartRoot, Map<ModelPart, PoseSnapshot>> OWN_POSES = new IdentityHashMap<>();
    private static final Map<EMFModelPartRoot, Float> OWN_FRAMES = new IdentityHashMap<>();
    // Frames such a pose stands for: the pass that has to be called off comes before the mob's own in a frame.
    private static final float OWN_POSE_FRAMES = 3f;

    private static void keepOwnPose(EMFModelPartRoot root) {
        float frame = EMFState.getFrameCounter();
        OWN_FRAMES.entrySet().removeIf(kept -> Math.abs(frame - kept.getValue()) > OWN_POSE_FRAMES);
        OWN_POSES.keySet().retainAll(OWN_FRAMES.keySet());
        Map<ModelPart, PoseSnapshot> pose = new IdentityHashMap<>();
        root.getAllParts().forEach(part -> pose.put(part, new PoseSnapshot(part)));
        OWN_POSES.put(root, pose);
        OWN_FRAMES.put(root, frame);
    }

    private static Map<ModelPart, PoseSnapshot> ownPose(EMFModelPartRoot root) {
        Float frame = OWN_FRAMES.get(root);
        return frame != null && Math.abs(EMFState.getFrameCounter() - frame) <= OWN_POSE_FRAMES ? OWN_POSES.get(root) : null;
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
     * Clears the carried-entity tracking set at the start of each Carry On
     * third-person render pass.
     */
    public static void clearCarriedEntities() {
        CARRIED_ENTITIES.clear();
    }

}
