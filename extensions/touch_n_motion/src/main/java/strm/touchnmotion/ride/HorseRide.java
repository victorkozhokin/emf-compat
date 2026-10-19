package strm.touchnmotion.ride;

import static strm.touchnmotion.interaction.Skeleton.RIGHT_SHOULDER;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.ik.IKFrame;
import strm.touchnmotion.horsesync.HorseSync;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.interaction.Candidate;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.InteractionContext;
import strm.touchnmotion.interaction.InteractionProvider;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.interaction.Skeleton;
import strm.touchnmotion.interaction.Smoothing;
import strm.touchnmotion.torso.TorsoLean;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Riding a horse - any horse, donkey, mule or camel the game seats a rider on: the hands hold the
 * reins over the withers, each a hand's width off the neck on its own side, in the horse's own
 * measure, so they stay with it as it turns and go after its head as it grazes or rears; the legs
 * grip its sides; the torso leans over the neck as a jockey's, if asked; the head looks where the
 * rider looks.
 *
 * <p>A hand the rider uses - eating, a shield, a swing - lets go of the reins and the other keeps
 * hold; while one is off the rider sits up, so the arm the game and the pack pose at a standing
 * shoulder stays on it. Both go for a bow or a crossbow. Another mod's pose of one arm takes that
 * arm only.</p>
 */
public final class HorseRide implements InteractionProvider {

    public static final HorseRide INSTANCE = new HorseRide();

    private static final Candidate.Timing TIMING = new Candidate.Timing(0.2, 0.15, 0.05);
    private static final float MAX_REACH = 1.6f;
    private static final float FIST = Riders.FIST;

    /** Blocks, from the waist, along the horse, to its side and up: where the hands hold the reins, the nearest and furthest forward. */
    private static final double GRIP_UP = 0.12, GRIP_WIDE = 0.15, GRIP_NEAR = 0.22, GRIP_FAR = 0.55;
    /** Blocks: the hands going after the head as the horse grazes (forward, down) and as it rears (up). */
    private static final double GRAZE_FORWARD = 0.1, GRAZE_DOWN = 0.08, REAR_UP = 0.2;
    /** Radians: the jockey's lean over the neck; and the legs gripping the sides. */
    private static final float JOCKEY = 0.38f, LEG_ROLL = 0.5f, LEG_X = 2.9f;
    /** Seconds the rider takes to sit up as a hand leaves the reins, and to lean in again. */
    private static final double SIT_UP_SECONDS = 0.08;
    private static final int RIGHT = 1, LEFT = 2, TORSO = 4;

    private static final class State {
        float weight, upright;
        boolean riding;
        int busy = -1;
        float headYaw, headPitch;
        final Vector3f[] place = {new Vector3f(), new Vector3f()};
        final float[] gap = new float[2];
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private HorseRide() {
    }

    @Override
    public String id() {
        return "HorseRide";
    }

    @Override
    public boolean isEnabled() {
        return HorseSync.isEnabled() && HorseSync.isRidingAnimation();
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        State state = STATES.seen(player.getUUID(), context.now()).value;
        double dt = context.dt();
        if (!(player.getVehicle() instanceof AbstractHorse horse)) {
            // Off the horse the pose is over at once: nothing of it is carried onto the ground.
            if (state.riding) TorsoLean.drop(player.getUUID());
            state.riding = false;
            state.weight = state.upright = 0;
            state.busy = -1;
            context.decide("off");
            return;
        }
        state.riding = true;
        state.weight += (1f - state.weight) * Smoothing.follow(dt, 0.2);
        Riders.carried(player);
        int busy = busy(player);
        state.upright += ((busy != 0 ? 1 : 0) - state.upright) * Smoothing.follow(dt, SIT_UP_SECONDS);
        state.busy = busy;

        float partial = strm.touchnmotion.platform.Platform.partialTick(false);
        IKFrame frame = context.frame();
        // The horse's own measure: along its body as it is drawn, to its right, and up.
        double yaw = Math.toRadians(Mth.rotLerp(partial, horse.yBodyRotO, horse.yBodyRot));
        Vec3 along = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)), up = new Vec3(0, 1, 0);
        Vec3 waist = frame.jointWorld(new Vector3f(Skeleton.WAIST));
        Vec3 origin = frame.jointWorld(new Vector3f());
        // Where the rider looks, against the body as it is drawn.
        float[] look = strm.touchnmotion.seat.CockpitFacing.look(Body.model(frame, origin.add(player.getViewVector(partial))));
        state.headYaw = look[0];
        state.headPitch = look[1];

        // Each hand as far before its shoulder as an arm reaches, the shoulder where the lean will have put it; never
        // closer to the rider than the saddle's front, nor further than the withers.
        float lean = -jockey(state);
        double px = Skeleton.SCALE / 16.0, reach = FIST * px;
        float graze = horse.getEatAnim(partial), rear = horse.getStandAnim(partial);
        Vec3[] grips = new Vec3[2];
        for (int hand = 0; hand < 2; hand++) {
            float side = hand == 0 ? RIGHT_SHOULDER.x : -RIGHT_SHOULDER.x;
            Vec3 shoulder = frame.jointWorld(Riders.shoulder(side, lean, 0f));
            Vec3 base = waist.add(up.scale(GRIP_UP)).add(right.scale((hand == 0 ? 1 : -1) * GRIP_WIDE));
            Vec3 d = base.subtract(shoulder);
            double ahead = d.dot(along);
            double far = -ahead + Math.sqrt(Math.max(0, ahead * ahead - d.lengthSqr() + reach * reach));
            far = Mth.clamp(far, GRIP_NEAR, GRIP_FAR);
            grips[hand] = base.add(along.scale(far + graze * GRAZE_FORWARD)).add(up.scale(rear * REAR_UP - graze * GRAZE_DOWN));
            state.place[hand].set(Body.model(frame, grips[hand]));
        }
        boolean held = Riders.hold(out, id(), TIMING, frame, grips[0], grips[1], MAX_REACH);
        context.decide(!held ? "out-of-reach" : busy == 0 ? "reins" : busy == RIGHT ? "reins:left" : busy == LEFT ? "reins:right" : "reins:free");
    }

    /** The jockey's lean over the neck now, radians forward: none sat up, or with the option off. */
    private static float jockey(State state) {
        if (!EMFCompatConfig.getBoolean(HorseSync.KEY_JOCKEY, true)) return 0f;
        return JOCKEY * state.weight * (1f - state.upright);
    }

    /**
     * The arms the reins cannot have, on a horse; {@code null} off one. The runtime leaves a used arm to
     * the game: here only the one in use, not both.
     */
    public static Set<Effector> reserved(AbstractClientPlayer player) {
        if (!INSTANCE.isEnabled() || !(player.getVehicle() instanceof AbstractHorse)) return null;
        int busy = busy(player);
        Set<Effector> reserved = EnumSet.noneOf(Effector.class);
        if ((busy & (RIGHT | TORSO)) != 0) reserved.add(Effector.RIGHT_ARM);
        if ((busy & (LEFT | TORSO)) != 0) reserved.add(Effector.LEFT_ARM);
        return reserved;
    }

    /** The arms something else is using: an item in use, a loaded crossbow held, a swing, another mod's pose. */
    private static int busy(AbstractClientPlayer player) {
        int busy = 0;
        if (player.isUsingItem()) {
            String use = player.getUseItem().getUseAnimation().name();
            busy |= "BOW".equals(use) || "CROSSBOW".equals(use) ? RIGHT | LEFT : arm(player, player.getUsedItemHand());
        }
        // A loaded crossbow is held up in both hands, as the game draws it.
        if (charged(player.getMainHandItem()) || charged(player.getOffhandItem())) busy |= RIGHT | LEFT;
        if (strm.touchnmotion.platform.Platform.swinging(player)) busy |= arm(player, strm.touchnmotion.platform.Platform.swingingArm(player));
        busy |= others(player.getUUID());
        if (strm.touchnmotion.compat.BodyClaims.arms(player.getUUID())) busy |= RIGHT | LEFT;
        return busy;
    }

    /** What other mods' poses hold: either arm, and the torso or head (then both arms with it). */
    private static int others(UUID uuid) {
        if (!strm.emfcompat.core.EMFCompatCore.isCompatEnabled()) return 0;
        int held = held(PoseManager.entitySavedPoses.get(uuid));
        var sources = PoseManager.entitySavedPosesBySource.get(uuid);
        if (sources != null) for (var poses : sources.values()) held |= held(poses);
        return held;
    }

    private static int held(strm.emfcompat.core.SavedPoses poses) {
        if (poses == null) return 0;
        var parts = poses.parts();
        int held = 0;
        if (poses.rightArm() != null || parts != null && parts.containsKey("right_arm")) held |= RIGHT;
        if (poses.leftArm() != null || parts != null && parts.containsKey("left_arm")) held |= LEFT;
        if (parts != null && (parts.containsKey("body") || parts.containsKey("head"))) held |= TORSO | RIGHT | LEFT;
        return held;
    }

    private static boolean charged(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.CrossbowItem && net.minecraft.world.item.CrossbowItem.isCharged(stack);
    }

    private static int arm(AbstractClientPlayer player, InteractionHand hand) {
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return side == HumanoidArm.RIGHT ? RIGHT : LEFT;
    }

    /** What the ride asks of the torso: the jockey's lean; {@code null} off a horse. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled() || (state.busy & TORSO) != 0) return null;
        // Leaning forward is +xRot.
        return TorsoLean.Hint.turn(jockey(state), 0f, 0f);
    }

    /** The legs gripping the horse's sides; before anything that works from where they are. */
    public static void legs(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || state.weight < 1e-3f || !INSTANCE.isEnabled()) return;
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (r == null || l == null) return;
        float w = state.weight;
        // Hanging straight down the barrel, spread to its width; turned out from the hip, the right leg is +zRot.
        r.x += (-LEG_X - r.x) * w;
        l.x += (LEG_X - l.x) * w;
        r.xRot += (0f - r.xRot) * w;
        l.xRot += (0f - l.xRot) * w;
        r.yRot += (0f - r.yRot) * w;
        l.yRot += (0f - l.yRot) * w;
        r.zRot += (LEG_ROLL - r.zRot) * w;
        l.zRot += (-LEG_ROLL - l.zRot) * w;
    }

    /** The last word on the hands and the head: each fist kept on its place on the reins, the head on the look. */
    public static void grip(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return;
        ModelPart head = parts.apply("head"), hat = parts.apply("hat");
        if (head != null && (state.busy & TORSO) == 0 && !strm.emfcompat.core.EMFCompatCore.isLocalPlayerInFirstPerson(uuid)
                && InteractionRuntime.aim(uuid, Effector.HEAD) == null) {
            strm.touchnmotion.seat.SeatedBody.turnHead(head, hat, state.headYaw, state.headPitch, state.weight);
        }
        Riders.grip(uuid, parts, INSTANCE.id(), state.place, state.gap);
    }
}
