package strm.emfcompat.parcool.compat;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.parcool.EMFCompatParCoolMod;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared capture side of the addon: turns "ParCool owns these parts right now" into a pose
 * saved in the core {@code PoseManager}, which restores it after EMF animates the model.
 *
 * <p>Both ParCool generations funnel through here. What differs is only how each one reports
 * the parts it owns - ParCool 4 lists them per animation, ParCool 3 does not, so its capture
 * paths pass the sets its two modes imply (see the mixins).</p>
 */
public final class ParCoolPose {

    /** Pose source name; see {@link PoseManager#setSourcePriority}. */
    public static final String SOURCE = "parcool";

    /**
     * Parkour is a specific, short, whole-body action, so it outranks the generic sources
     * (attack and riding poses) the same way a spell cast does. Ties are not expected:
     * nothing else drives the whole model while a vault or wall run is playing.
     */
    public static final int SOURCE_PRIORITY = 10;

    /** The model parts ParCool can drive; mirrors ParCool 4's {@code AnimatableModelPart}. */
    public enum Part {
        HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
    }

    /** ParCool built the whole pose from a reset model, so every part is its own. */
    public static final Set<Part> WHOLE_MODEL = EnumSet.allOf(Part.class);

    /** ParCool adjusted a vanilla pose; the torso keeps whatever the pack does with it. */
    public static final Set<Part> LIMBS_AND_HEAD =
            EnumSet.of(Part.HEAD, Part.LEFT_ARM, Part.RIGHT_ARM, Part.LEFT_LEG, Part.RIGHT_LEG);

    private static final Map<Part, String> PART_NAMES = new EnumMap<>(Part.class);

    static {
        PART_NAMES.put(Part.HEAD, "head");
        PART_NAMES.put(Part.BODY, "body");
        PART_NAMES.put(Part.LEFT_ARM, "left_arm");
        PART_NAMES.put(Part.RIGHT_ARM, "right_arm");
        PART_NAMES.put(Part.LEFT_LEG, "left_leg");
        PART_NAMES.put(Part.RIGHT_LEG, "right_leg");
    }

    private ParCoolPose() {
    }

    public static void clear(Player player) {
        PoseManager.clearPoses(player.getUUID(), SOURCE);
        releaseBody(player.getUUID());
    }

    /** How long the core's fade takes to let a part go: its time constant. */
    private static final float RELEASE_SECONDS = 0.07f;

    /** The torso weight last captured, and when; render thread only. */
    private static final Map<UUID, float[]> BODY_HELD = new HashMap<>();
    private static final Map<UUID, Long> BODY_HELD_AT = new HashMap<>();
    /** Weight the torso was released at, and when. */
    private static final Map<UUID, Float> BODY_RELEASED = new HashMap<>();
    private static final Map<UUID, Long> BODY_RELEASED_AT = new HashMap<>();

    /**
     * How much of the torso the model shows in ParCool's pose right now, 0 to 1: the weight it was
     * captured at, and on release the same fade the core runs. A resource pack's cape follows the
     * pack's own torso variables, not the part, so it uses this to follow the torso ParCool holds.
     */
    public static float bodyHeld(UUID uuid) {
        long now = System.nanoTime();
        Long at = BODY_HELD_AT.get(uuid);
        if (at != null) {
            return BODY_HELD.get(uuid)[0];
        }
        Float released = BODY_RELEASED.get(uuid);
        if (released == null) return 0f;
        float t = (now - BODY_RELEASED_AT.get(uuid)) / 1e9f;
        float w = released * (float) Math.exp(-t / RELEASE_SECONDS);
        if (w < 0.01f) {
            BODY_RELEASED.remove(uuid);
            BODY_RELEASED_AT.remove(uuid);
            return 0f;
        }
        return w;
    }

    private static void holdBody(UUID uuid, float weight) {
        BODY_HELD.computeIfAbsent(uuid, k -> new float[1])[0] = weight;
        BODY_HELD_AT.put(uuid, System.nanoTime());
        BODY_RELEASED.remove(uuid);
        BODY_RELEASED_AT.remove(uuid);
    }

    private static void releaseBody(UUID uuid) {
        if (BODY_HELD_AT.remove(uuid) == null) return;
        BODY_RELEASED.put(uuid, BODY_HELD.remove(uuid)[0]);
        BODY_RELEASED_AT.put(uuid, System.nanoTime());
    }

    /**
     * Captures the parts ParCool owns this frame. Snapshots are full (rotation, position and
     * scale): ParCool translates limbs as well as rotating them - a vault moves the arms off
     * the shoulders - so a rotation-only capture would leave them hanging at EMF's pivots.
     */
    public static void capture(Player player, PlayerModel<?> model, Set<Part> owned) {
        capture(player, model, owned, null, 1f, false);
    }

    /**
     * ParCool 3.4's capture: rotations only, so every part keeps the pivot EMF and the pack gave
     * it. Nominal support - ParCool 3 poses a vanilla-shaped model, and its positions put on a
     * pack's model (which moves the torso, and the head with it) tore the head and limbs off the
     * body. Losing the few moves that shift a limb off its joint is the price.
     */
    public static void captureRotations(Player player, PlayerModel<?> model, Set<Part> owned) {
        capture(player, model, owned, null, 1f, true);
    }

    /**
     * Writes the pose ParCool is heading for onto a part that has just been reset to its default
     * pose, i.e. the pose at full weight, before ParCool blended it with anything.
     */
    @FunctionalInterface
    public interface Target {
        void pose(Part part, ModelPart modelPart);
    }

    /**
     * Captures ParCool's own target for each owned part together with its blend factor, so the
     * core blends the move over the resource pack's animation exactly as ParCool eases it in and
     * out - rather than over the vanilla pose ParCool itself blends from.
     *
     * @param target null to take the model as ParCool left it, at full weight
     * @param weight ParCool's blend factor for this frame, 0 to 1
     */
    public static void capture(Player player, PlayerModel<?> model, Set<Part> owned,
                               @Nullable Target target, float weight) {
        capture(player, model, owned, target, weight, false);
    }

    private static void capture(Player player, PlayerModel<?> model, Set<Part> owned,
                                @Nullable Target target, float weight, boolean rotationOnly) {
        UUID uuid = player.getUUID();

        if (!EMFCompatParCoolMod.isEnabled() || owned.isEmpty()) {
            PoseManager.clearPoses(uuid, SOURCE);
            releaseBody(uuid);
            return;
        }
        // The core skips restoration for the first-person view entirely; leave the stored pose
        // alone rather than clearing it, so switching back to third person keeps this frame.
        if (EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            return;
        }

        boolean wholePose = EMFCompatParCoolMod.isWholePose();
        Map<String, PoseSnapshot> parts = new HashMap<>();
        for (Part part : owned) {
            if (!wholePose && (part == Part.HEAD || part == Part.BODY)) {
                continue;
            }
            ModelPart modelPart = modelPart(model, part);
            if (modelPart == null) {
                continue;
            }
            if (target == null) {
                parts.put(PART_NAMES.get(part), new PoseSnapshot(modelPart, rotationOnly));
                continue;
            }
            // Work out the target on the model itself, then put ParCool's frame back untouched.
            PoseSnapshot asParCoolLeftIt = new PoseSnapshot(modelPart);
            modelPart.resetPose();
            target.pose(part, modelPart);
            parts.put(PART_NAMES.get(part), new PoseSnapshot(modelPart).blended(weight));
            asParCoolLeftIt.apply(modelPart);
        }

        if (parts.isEmpty()) {
            PoseManager.clearPoses(uuid, SOURCE);
            releaseBody(uuid);
            return;
        }

        // Arms travel in the part map rather than the dedicated arm slots: those are restored
        // rotation-only (or body-following), and a parkour pose needs its exact positions.
        PoseManager.savePoses(uuid, SOURCE, null, null, parts);
        PoseSnapshot body = parts.get("body");
        if (body != null) {
            holdBody(uuid, body.selfBlended ? body.weight : 1f);
        } else {
            releaseBody(uuid);
        }
    }

    private static ModelPart modelPart(PlayerModel<?> model, Part part) {
        return switch (part) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }
}
