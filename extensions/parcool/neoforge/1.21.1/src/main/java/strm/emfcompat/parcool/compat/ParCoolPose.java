package strm.emfcompat.parcool.compat;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.parcool.EMFCompatParCoolMod;

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
    }

    /**
     * Captures the parts ParCool owns this frame. Snapshots are full (rotation, position and
     * scale): ParCool translates limbs as well as rotating them - a vault moves the arms off
     * the shoulders - so a rotation-only capture would leave them hanging at EMF's pivots.
     */
    public static void capture(Player player, PlayerModel<?> model, Set<Part> owned) {
        UUID uuid = player.getUUID();

        if (!EMFCompatParCoolMod.isEnabled() || owned.isEmpty()) {
            PoseManager.clearPoses(uuid, SOURCE);
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
            if (modelPart != null) {
                parts.put(PART_NAMES.get(part), new PoseSnapshot(modelPart));
            }
        }

        if (parts.isEmpty()) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        // Arms travel in the part map rather than the dedicated arm slots: those are restored
        // rotation-only (or body-following), and a parkour pose needs its exact positions.
        PoseManager.savePoses(uuid, SOURCE, null, null, parts);
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
