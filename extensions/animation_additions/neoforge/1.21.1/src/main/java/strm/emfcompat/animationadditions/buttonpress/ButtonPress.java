package strm.emfcompat.animationadditions.buttonpress;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pressing a button or throwing a lever with the body: the right hand goes to a button or a
 * lever's handle, a foot stamps on a button on the floor. Doors are {@code DoorHold}'s.
 *
 * <p>The press itself is instant, so the gesture starts before it: a button in reach near where
 * the player looks is the target, and the hand points at it (the foot lifts over it) while it is
 * looked at. When it goes down - it turns powered, for this player or anyone watched - the
 * hand pushes onto its middle, the torso leaning to make up the arm's length when it has to.</p>
 *
 * <p>In reach means the arm's length from a shoulder with the torso leant at most
 * {@link #MAX_LEAN_PITCH} forwards and turned {@link #MAX_LEAN_YAW} towards it; for a foot, a
 * button on the floor the player stands on, in front of or beside a foot.</p>
 */
public final class ButtonPress implements InteractionProvider {

    public static final ButtonPress INSTANCE = new ButtonPress();
    public static final String KEY_ENABLED = "buttonpress.enabled";

    /** Above everything passive: a press takes the hand off a wall or a plant. */
    private static final int PRIORITY = 10;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.12, 0.18, 0.05);

    /** Model space: pixels, y down, facing -z. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f RIGHT_HIP = new Vector3f(-1.9f, 12f, 0f);
    private static final Vector3f LEFT_HIP = new Vector3f(1.9f, 12f, 0f);
    /** The torso turns round the waist, as {@code TorsoLean} does. */
    private static final Vector3f WAIST = new Vector3f(0f, 12f, 0f);
    /** Shoulder to fingertips, pixels. */
    private static final float ARM = 11f;
    private static final float LEG = 12f;
    private static final float MAX_LEAN_PITCH = (float) Math.toRadians(15);
    private static final float MAX_LEAN_YAW = (float) Math.toRadians(15);
    private static final int LEAN_STEPS = 6;
    /** Turn of the torso towards the foot on a button. */
    private static final float FOOT_YAW = (float) Math.toRadians(10);

    private static final double GRIP_SECONDS = 0.06;
    /**
     * Where the end of a lever's handle is from the middle of its base, pixels: out from the wall
     * and up (off) or down (on). The handle stands out at 45 degrees; the hand takes it just short
     * of the knob.
     */
    private static final float LEVER_OUT = 6f;
    private static final float LEVER_UP = 5.5f;
    /** How far in front of the shoulder, pixels, a grip has to be for the arm to go to it. */
    private static final float MIN_AHEAD = 3f;
    /** Within this many pixels above or below the shoulder a grip counts as at its height. */
    private static final float LEVEL_BAND = 6f;
    /**
     * The same for a lever, smaller: the hand follows the end of its handle from well off, and
     * holding it up close too felt like letting go early. Only a grip right beside the shoulder,
     * where the arm would turn inside out, is left.
     */
    private static final float LEVER_MIN_AHEAD = -1.5f;
    private static final float LEVER_LEVEL_BAND = 2f;
    /**
     * How far past the arm's length a lever still counts as in reach, as a share of it. Well past
     * the hand's reach on purpose: the hand points at the end of the handle and follows it over,
     * as if it threw it from a distance - on a floor or a ceiling it could not reach otherwise.
     */
    private static final float LEVER_REACH = 4.0f;
    /** Buttons are looked for this far round the eyes, blocks, and this close to the look. */
    private static final double SCAN_RADIUS = 3.0;
    private static final double LOOK_CONE = Math.cos(Math.toRadians(30));
    /** A lever: within this of where the body faces, across the ground, from this high over the feet. */
    private static final double LEVER_CONE = Math.cos(Math.toRadians(60));
    private static final double CHEST = 1.3;
    private static final long SCAN_EVERY_NANOS = 100_000_000L;
    /** How long a press shows: the push in, then back to waiting. */
    private static final double PRESS_SECONDS = 0.3;

    /** A foot: how far out from under the hip, pixels, and how high it waits over the button. */
    private static final float FOOT_REACH = 9f;
    /** How much of carrying the foot over the button is a turn of the leg; the rest moves it. */
    private static final float FOOT_TURN = 0.2f;
    /** Waiting, the sole rests on the button; pressing, it goes down with it. */
    private static final float FOOT_HOVER = 0f;
    private static final float FOOT_PRESS = -1.5f;
    /** How much nearer, pixels, the other foot has to be to take the button over. */
    private static final float FOOT_KEEP = 2f;
    private static final double LEG_SECONDS = 0.05;
    private static final double LEG_FADE_IN = 0.1;
    private static final double LEG_FADE_OUT = 0.15;
    /** Faster than this, blocks per tick, the player walks past buttons. */
    private static final double SLOW_BELOW = 0.15;

    private static final long NEVER = 0L;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private ButtonPress() {
    }

    private static final class State {
        final List<BlockPos> nearby = new ArrayList<>();
        final List<Boolean> powered = new ArrayList<>();
        long scannedAt;
        String why = "none";
        BlockPos target;
        long pressedAt = NEVER;
        /** The torso turn asked for, {pitch, yaw, roll}. */
        final float[] lean = new float[3];
        /** The button's middle in model pixels, and the arm on it. */
        final Vector3f button = new Vector3f();
        boolean armRight;
        /** A foot on a button: which, {pitch, roll, lift} as shown, and how much of it shows. */
        boolean footRight;
        final float[] leg = new float[5];
        float legWeight;
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Press buttons", true,
                "On", "The right hand reaches for a button or a lever in reach before it is used; a foot stamps on a button on the floor.",
                "Off", "Leave the arms and legs to EMF.");
    }

    @Override
    public String id() {
        return "ButtonPress";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        long now = context.now();
        State state = STATES.seen(player.getUUID(), now).value;
        double dt = context.dt();
        state.lean[0] = state.lean[1] = state.lean[2] = 0f;
        float[] legTarget = null;
        try {
            String why = ineligible(player);
            if (why != null) {
                state.target = null;
                context.decide(why);
                return;
            }
            Level level = player.level();
            if (now - state.scannedAt > SCAN_EVERY_NANOS || state.nearby.isEmpty() && state.scannedAt == 0) {
                scan(player, state);
                state.scannedAt = now;
            }
            // A button going down, or a lever thrown either way, near the player is a press -
            // whoever looks, it is theirs.
            BlockPos pressed = null;
            for (int i = 0; i < state.nearby.size(); i++) {
                BlockState block = level.getBlockState(state.nearby.get(i));
                boolean on = isTarget(block) && on(block);
                boolean was = state.powered.get(i);
                if (block.getBlock() instanceof ButtonBlock ? on && !was : on != was) pressed = state.nearby.get(i);
                state.powered.set(i, on);
            }
            IKFrame frame = context.frame();
            BlockPos target = pressed != null && reachable(player, frame, pressed) ? pressed : look(player, frame, state);
            if (pressed != null && pressed.equals(target)) state.pressedAt = now;
            if (target == null) {
                state.target = null;
                context.decide(state.why);
                return;
            }
            if (!target.equals(state.target)) state.pressedAt = pressed != null && pressed.equals(target) ? now : NEVER;
            state.target = target;
            boolean pressing = state.pressedAt != NEVER && (now - state.pressedAt) / 1e9 < PRESS_SECONDS;

            BlockState block = level.getBlockState(target);
            Foot foot = foot(player, frame, target, block, state.legWeight > 1e-3f ? state.footRight : null);
            if (foot != null) {
                state.footRight = foot.right;
                // The torso turns a little towards the foot on the button: -yRot turns right.
                state.lean[1] = (foot.right ? -1f : 1f) * FOOT_YAW;
                legTarget = foot.leg(pressing ? FOOT_PRESS : FOOT_HOVER);
                context.decide((pressing ? "stamp-" : "foot-") + (foot.right ? "R" : "L"));
                return;
            }
            Hand hand = hand(player, frame, target, block);
            if (hand == null) {
                context.decide("out-of-reach");
                return;
            }
            // The aim is settled again after the pack has animated (aimArm), from where the
            // shoulder really is; this one is what the arbiter and the fade work with.
            float[] aim = {hand.aim.x(), hand.aim.y()};
            // A lever's handle flips over when it is thrown: the hand goes over with it.
            if (InteractionRuntime.weight(player.getUUID(), Effector.RIGHT_ARM, id()) < 1e-3f) state.button.set(hand.button);
            else state.button.lerp(hand.button, Smoothing.follow(dt, GRIP_SECONDS));
            state.armRight = hand.right;
            if (pressing) {
                state.lean[0] = hand.pitch;
                state.lean[1] = hand.yaw;
            }
            out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING,
                    hand.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, aim));
            // The press swings the arm; the push is the swing.
            context.claimArms();
            context.decide((pressing ? "press-" : "hover-") + (hand.right ? "R" : "L"));
        } finally {
            legs(state, legTarget, dt);
        }
    }

    private static String ineligible(AbstractClientPlayer player) {
        if (!player.onGround() || player.isPassenger() || player.isSleeping()
                || player.isInWaterOrBubble()) return "off:state";
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) return "off:pose";
        if (Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) > SLOW_BELOW) return "off:moving";
        return null;
    }

    /** The buttons round the player's eyes, and whether each is down now. */
    private static void scan(AbstractClientPlayer player, State state) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        int r = Mth.ceil(SCAN_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(eye.offset(-r, -r - 1, -r), eye.offset(r, r, r))) {
            if (isTarget(player.level().getBlockState(pos))) found.add(pos.immutable());
        }
        List<Boolean> powered = new ArrayList<>();
        for (BlockPos pos : found) {
            int was = state.nearby.indexOf(pos);
            powered.add(was >= 0 ? state.powered.get(was) : on(player.level().getBlockState(pos)));
        }
        state.nearby.clear();
        state.nearby.addAll(found);
        state.powered.clear();
        state.powered.addAll(powered);
    }

    /**
     * The button in reach nearest the look, within the cone round it; the one already kept wins
     * ties. A lever or a button on a wall is found the way a door is, by the body rather than the
     * eyes: across the ground, within {@link #LEVER_CONE} of where the body faces, from the chest -
     * looking straight at one or past it no longer decides it.
     */
    private static BlockPos look(AbstractClientPlayer player, IKFrame frame, State state) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1f);
        Vec3 chest = player.position().add(0, CHEST, 0);
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        BlockPos best = null;
        double bestDot = -2;
        state.why = "none";
        double viewDot = -2;
        for (BlockPos pos : state.nearby) {
            BlockState block = player.level().getBlockState(pos);
            if (!isTarget(block)) continue;
            Vec3 grip = grip(player, pos, block);
            double dot;
            if (fromAfar(block)) {
                Vec3 flat = new Vec3(grip.x - chest.x, 0, grip.z - chest.z);
                if (grip.subtract(chest).length() > SCAN_RADIUS + 0.5) continue;
                // Right over or under the chest there is no way across: it counts as ahead.
                dot = flat.length() < 0.2 ? 1 : flat.normalize().dot(facing);
                if (dot <= LEVER_CONE) continue;
            } else {
                Vec3 to = grip.subtract(eye);
                if (to.length() > SCAN_RADIUS + 0.5) continue;
                dot = to.normalize().dot(view);
                if (dot <= LOOK_CONE) continue;
            }
            dot += pos.equals(state.target) ? 0.03 : 0;
            // With the trace on, what was nearest and how far it was, when nothing is in reach.
            if (dot > viewDot && FootGroundingFeature.isTrace()) {
                viewDot = dot;
                Vector3f rel = frame.relativeToJoint(grip, RIGHT_SHOULDER);
                state.why = String.format("none (in view: %s at %.1f px from the shoulder, %.1f,%.1f,%.1f)",
                        block.getBlock().getClass().getSimpleName(), rel.length(), rel.x, rel.y, rel.z);
            }
            if (dot > bestDot && reachable(player, frame, pos)) {
                best = pos;
                bestDot = dot;
            }
        }
        return best;
    }

    private static boolean reachable(AbstractClientPlayer player, IKFrame frame, BlockPos pos) {
        BlockState block = player.level().getBlockState(pos);
        if (!isTarget(block)) return false;
        return foot(player, frame, pos, block, null) != null || hand(player, frame, pos, block) != null;
    }

    /**
     * A lever, or a button on a wall: found by the body and followed from well off, the hand
     * pointing at it with no touch - as if thrown or pressed from a distance.
     */
    private static boolean fromAfar(BlockState block) {
        return block.getBlock() instanceof LeverBlock || ThrottleLever.is(block)
                || block.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == AttachFace.WALL;
    }

    /** A button or a lever. */
    private static boolean isTarget(BlockState block) {
        return block.getBlock() instanceof ButtonBlock || block.getBlock() instanceof LeverBlock || ThrottleLever.is(block);
    }

    /** Whether it is down or thrown. */
    private static boolean on(BlockState block) {
        // A throttle lever has no on and off: it is dragged, and the hand goes along.
        return block.hasProperty(BlockStateProperties.POWERED) && block.getValue(BlockStateProperties.POWERED);
    }

    /**
     * Where the hand goes: a button's middle; the end of a lever's handle, which is up when it is
     * off and down when it is on (on a wall; on a floor or a ceiling it leans along its facing),
     * so the hand follows it over when it is thrown.
     */
    private static Vec3 grip(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        Level level = player.level();
        if (ThrottleLever.is(block)) {
            Vec3 knob = ThrottleLever.knob(level, pos);
            if (knob != null) return knob;
        }
        Vec3 centre = centre(level, pos, block);
        if (!(block.getBlock() instanceof LeverBlock)) return centre;
        Vec3 out = centre.add(outwards(block).scale(LEVER_OUT / 16.0));
        float side = (block.getValue(BlockStateProperties.POWERED) ? -1 : 1) * LEVER_UP / 16f;
        if (block.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == AttachFace.WALL) return out.add(0, side, 0);
        // On a floor or a ceiling the handle leans along its facing: away from it when off, towards
        // it when on.
        Direction facing = block.getValue(FaceAttachedHorizontalDirectionalBlock.FACING);
        return out.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-side));
    }

    private static Vec3 centre(Level level, BlockPos pos, BlockState block) {
        AABB box = block.getShape(level, pos).isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : block.getShape(level, pos).bounds();
        return box.getCenter().add(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Which way the button's face looks, out of what it is on. */
    private static Vec3 outwards(BlockState block) {
        AttachFace face = block.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        if (face == AttachFace.FLOOR) return new Vec3(0, 1, 0);
        if (face == AttachFace.CEILING) return new Vec3(0, -1, 0);
        Direction facing = block.getValue(FaceAttachedHorizontalDirectionalBlock.FACING);
        return Vec3.atLowerCornerOf(facing.getNormal());
    }

    // ---- the hand

    private record Hand(boolean right, IKResult aim, float pitch, float yaw, Vector3f button) {
    }

    /**
     * The right arm aimed at the button or the lever, with the least torso lean that brings it in
     * reach; {@code null} when even the most does not. Only the right arm presses: with the left,
     * the torso turning for the press left the arm behind and the click's swing fought it.
     */
    private static Hand hand(AbstractClientPlayer player, IKFrame frame, BlockPos pos, BlockState block) {
        Vec3 point = grip(player, pos, block);
        Vector3f model = frame.relativeToJoint(point, new Vector3f());
        boolean right = true;
        Vector3f shoulder = RIGHT_SHOULDER;
        // A lever or a button on a wall is followed from further off (see LEVER_REACH); on a
        // floor or a ceiling, a button only in reach.
        boolean afar = fromAfar(block);
        float maxReach = afar ? LEVER_REACH : 1f;
        // Turning towards the button's side: towards -x (the right) is a turn to the right, +yRot.
        float yawSign = model.x < 0 ? 1f : -1f;
        // Right up against it the grip is beside or behind the shoulder, at its height, and the arm
        // would turn inside out to reach it: leave it. Above or below the shoulder it is fine.
        Vector3f fromShoulder = frame.relativeToJoint(point, shoulder);
        float minAhead = afar ? LEVER_MIN_AHEAD : MIN_AHEAD;
        float band = afar ? LEVER_LEVEL_BAND : LEVEL_BAND;
        if (fromShoulder.z > -minAhead && Math.abs(fromShoulder.y) < band) return null;
        for (int i = 0; i <= LEAN_STEPS; i++) {
            float share = i / (float) LEAN_STEPS;
            float pitch = MAX_LEAN_PITCH * share;
            float yaw = Math.abs(model.x) < 1.5f ? 0f : yawSign * MAX_LEAN_YAW * share;
            IKResult aim = OneBoneIK.solveXY(frame, leant(shoulder, pitch, yaw), point, ARM, 0f, 0f);
            if (aim != null && aim.reach() <= maxReach) return new Hand(right, aim, pitch, yaw, model);
        }
        return null;
    }

    /** A shoulder pivot carried round the waist by the torso's lean. */
    private static Vector3f leant(Vector3f shoulder, float pitch, float yaw) {
        Quaternionf turn = new Quaternionf().rotationZYX(0f, yaw, pitch);
        return turn.transform(new Vector3f(shoulder).sub(WAIST)).add(WAIST);
    }

    // ---- the foot

    private record Foot(boolean right, float dx, float dz, float top) {
        /**
         * {pitch, roll, lift, shift x, shift z} putting the sole {@code over} pixels above the
         * button's top. The leg is carried out over the button nearly upright, the way a raised
         * foot steps: only {@link #FOOT_TURN} of the way is a turn of the leg, the rest moves it.
         */
        float[] leg(float over) {
            float pitch = (float) Math.asin(Mth.clamp(dz * FOOT_TURN / LEG, -1f, 1f));
            float roll = (float) Math.asin(Mth.clamp(-dx * FOOT_TURN / LEG, -1f, 1f));
            float sole = LEG * (float) (Math.cos(pitch) * Math.cos(roll));
            // y is down: the sole has to come up to top - over; the hip takes what the turn does not.
            float lift = Math.max(0f, sole - (top - over));
            return new float[]{pitch, roll, lift, dx * (1f - FOOT_TURN), dz * (1f - FOOT_TURN)};
        }
    }

    /**
     * A foot over a button on the floor the player stands on: in front of or beside a foot, never
     * behind. {@code null} otherwise.
     */
    private static Foot foot(AbstractClientPlayer player, IKFrame frame, BlockPos pos, BlockState block, Boolean keep) {
        if (!(block.getBlock() instanceof ButtonBlock) || block.getValue(ButtonBlock.FACE) != AttachFace.FLOOR) return null;
        if (pos.getY() != Mth.floor(player.getY() + 1e-3)) return null;
        if (player.getPose() != Pose.STANDING) return null;
        Level level = player.level();
        AABB box = block.getShape(level, pos).bounds();
        Vec3 top = new Vec3(pos.getX() + box.getCenter().x, pos.getY() + box.maxY, pos.getZ() + box.getCenter().z);
        Foot best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int leg = 0; leg < 2; leg++) {
            boolean isRight = leg == 0;
            Vector3f rel = frame.relativeToJoint(top, isRight ? RIGHT_HIP : LEFT_HIP);
            // Never behind, never across the other leg.
            if (rel.z > 1f || (isRight ? rel.x > 2f : rel.x < -2f)) continue;
            float distance = (float) Math.hypot(rel.x, rel.z);
            if (distance > FOOT_REACH) continue;
            // The foot already over it keeps it until the other is clearly nearer.
            float rank = distance - (keep != null && keep == isRight ? FOOT_KEEP : 0f);
            if (rank >= bestDistance) continue;
            best = new Foot(isRight, rel.x, rel.z, rel.y);
            bestDistance = rank;
        }
        return best;
    }

    private static void legs(State state, float[] target, double dt) {
        if (target != null) {
            float k = Smoothing.snapFirst(dt, state.legWeight < 1e-3f ? 0 : LEG_SECONDS);
            for (int i = 0; i < 5; i++) state.leg[i] += (target[i] - state.leg[i]) * k;
            state.legWeight += (1f - state.legWeight) * Smoothing.fadeIn(dt, LEG_FADE_IN);
        } else {
            state.legWeight -= state.legWeight * Smoothing.fadeOut(dt, LEG_FADE_OUT);
            if (state.legWeight < 1e-3f) state.legWeight = 0f;
        }
    }

    /** The torso turn a foot on a button asks for, {pitch, yaw, roll}; {@code null} when none. */
    public static float[] torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.lean[0] == 0f && state.lean[1] == 0f && state.lean[2] == 0f) return null;
        return state.lean.clone();
    }

    /**
     * Points the arm on a button at its middle from where its shoulder is drawn this frame -
     * after the pack's breathing and swing and the torso's lean have moved it - as much as the
     * arm is this provider's. Called last, after the interaction runtime.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        Effector effector = state.armRight ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
        float w = InteractionRuntime.weight(uuid, effector, INSTANCE.id());
        if (w < 1e-3f) return;
        ModelPart arm = parts.apply(effector.part);
        if (arm == null) return;
        Vector3f to = new Vector3f(state.button).sub(arm.x, arm.y, arm.z);
        if (to.lengthSquared() < 1e-6f) return;
        to.normalize();
        // As OneBoneIK: the arm hangs along +y.
        float x = -(float) Math.acos(Mth.clamp(to.y, -1f, 1f));
        float y = (float) Math.atan2(-to.x, -to.z);
        arm.xRot += IKMath.wrap(x - arm.xRot) * w;
        arm.yRot += IKMath.wrap(y - arm.yRot) * w;
    }

    /**
     * Puts a foot on its button over whatever the feet were given. Called after the pack has animated.
     */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        if (state.legWeight < 1e-3f) return;
        ModelPart leg = parts.apply(state.footRight ? "right_leg" : "left_leg");
        if (leg == null) return;
        Vector3f hip = state.footRight ? RIGHT_HIP : LEFT_HIP;
        float w = state.legWeight;
        leg.xRot += (state.leg[0] - leg.xRot) * w;
        leg.yRot += (0f - leg.yRot) * w;
        leg.zRot += (state.leg[1] - leg.zRot) * w;
        leg.x += (hip.x + state.leg[3] - leg.x) * w;
        leg.y += (hip.y - state.leg[2] - leg.y) * w;
        leg.z += (hip.z + state.leg[4] - leg.z) * w;
    }
}
