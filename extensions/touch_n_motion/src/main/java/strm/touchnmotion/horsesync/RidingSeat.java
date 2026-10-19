package strm.touchnmotion.horsesync;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.touchnmotion.DebugLog;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.Smoothing;
import strm.touchnmotion.platform.Platform;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The rider's seat, once the game has posed the model: {@link RidingPose} written and captured into
 * the core's {@code PoseManager}, which puts it back over the pack. A hand the rider uses - eating,
 * drawing a bow, a shield up, a swing - lets go of the reins and is left to the game and the pack;
 * while one is off the reins the upper body sits up, so the arm the pack draws at a standing
 * shoulder stays on it. The core fades a hand off and back on; the sitting up is eased here.
 */
public final class RidingSeat {
    private static final int RIGHT = 1, LEFT = 2, TORSO = 4;
    /** Seconds the upper body takes to sit up when a hand leaves the reins, and to lean in again. */
    private static final double SIT_UP_SECONDS = .08;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private static final class State {
        float upright;
        int busy;
    }

    private RidingSeat() {
    }

    /** From {@code PlayerModel.setupAnim}'s end, on every version. */
    public static void pose(HumanoidModel<?> model, AbstractClientPlayer player) {
        UUID uuid = player.getUUID();
        if (strm.emfcompat.core.EMFCompatCore.isLocalPlayerInFirstPerson(uuid)
                || !HorseSync.isEnabled() || !HorseSync.isRidingAnimation()
                || !(player.getVehicle() instanceof AbstractHorse horse)) {
            PoseManager.clearPoses(uuid, HorseSync.RIDING_SOURCE);
            return;
        }

        // Another addon's pose of the torso or head - a gun aim, a melee swing - takes the whole upper
        // body and the seat keeps the legs; one of only an arm or two - eating, a map - takes those
        // hands off the reins. The seat stays a low-priority base (see HorseSync).
        int others = others(uuid);
        boolean upperBody = (others & TORSO) == 0;
        int busy = upperBody ? busy(player) | others : 0;

        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        State state = entry.value;
        double dt = EntityStates.due(entry, now);
        if (dt >= 0) state.upright += ((busy != 0 ? 1 : 0) - state.upright) * Smoothing.follow(dt, SIT_UP_SECONDS);
        if (busy != state.busy) {
            if (DebugLog.decisions())
                org.slf4j.LoggerFactory.getLogger("EMFCompatHorseSync").info("[RidingSeat] {} off the reins: {}",
                        player.getName().getString(), busy == 0 ? "none" : busy == RIGHT ? "right" : busy == LEFT ? "left" : "both");
            state.busy = busy;
        }

        // The bob follows the horse's gait (the passenger's own limb swing is ~0): still when it
        // stands, bobbing when it moves.
        RidingPose.apply(model, horse.walkAnimation.position(), horse.walkAnimation.speed(), upperBody,
                (busy & RIGHT) == 0, (busy & LEFT) == 0, state.upright);

        // Captured whole; the core puts it back over the pack, sleeves and pants follow their parts.
        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("left_leg", new PoseSnapshot(model.leftLeg));
        parts.put("right_leg", new PoseSnapshot(model.rightLeg));
        if (upperBody) {
            parts.put("body", new PoseSnapshot(model.body));
            parts.put("head", new PoseSnapshot(model.head));
            if ((busy & RIGHT) == 0) parts.put("right_arm", new PoseSnapshot(model.rightArm));
            if ((busy & LEFT) == 0) parts.put("left_arm", new PoseSnapshot(model.leftArm));
        }
        PoseManager.savePoses(uuid, HorseSync.RIDING_SOURCE, null, null, parts);
    }

    /** What the other pose sources hold: either arm, and the torso or head. */
    private static int others(UUID uuid) {
        if (!strm.emfcompat.core.EMFCompatCore.isCompatEnabled()) return 0;
        int held = held(PoseManager.entitySavedPoses.get(uuid));
        Map<String, strm.emfcompat.core.SavedPoses> sources = PoseManager.entitySavedPosesBySource.get(uuid);
        if (sources != null)
            for (Map.Entry<String, strm.emfcompat.core.SavedPoses> e : sources.entrySet())
                if (!e.getKey().equals(HorseSync.RIDING_SOURCE)) held |= held(e.getValue());
        return held;
    }

    private static int held(strm.emfcompat.core.SavedPoses poses) {
        if (poses == null) return 0;
        Map<String, PoseSnapshot> parts = poses.parts();
        int held = 0;
        if (poses.rightArm() != null || parts != null && parts.containsKey("right_arm")) held |= RIGHT;
        if (poses.leftArm() != null || parts != null && parts.containsKey("left_arm")) held |= LEFT;
        if (parts != null && (parts.containsKey("body") || parts.containsKey("head"))) held |= TORSO;
        return held;
    }

    /** The arms something else is using now: an item in use, a charged crossbow held, a swing. */
    private static int busy(AbstractClientPlayer player) {
        int busy = 0;
        if (player.isUsingItem()) {
            String use = player.getUseItem().getUseAnimation().name();
            busy |= "BOW".equals(use) || "CROSSBOW".equals(use) ? RIGHT | LEFT : arm(player, player.getUsedItemHand());
        }
        // A loaded crossbow is held up in both hands, as the game draws it.
        if (charged(player.getMainHandItem()) || charged(player.getOffhandItem())) busy |= RIGHT | LEFT;
        if (Platform.swinging(player)) busy |= arm(player, Platform.swingingArm(player));
        return busy;
    }

    private static boolean charged(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.CrossbowItem && net.minecraft.world.item.CrossbowItem.isCharged(stack);
    }

    private static int arm(AbstractClientPlayer player, InteractionHand hand) {
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return side == HumanoidArm.RIGHT ? RIGHT : LEFT;
    }
}
