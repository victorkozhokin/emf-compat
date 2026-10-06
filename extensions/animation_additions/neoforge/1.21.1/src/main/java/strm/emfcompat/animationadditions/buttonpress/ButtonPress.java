package strm.emfcompat.animationadditions.buttonpress;

import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.animationadditions.DebugLog;
import strm.emfcompat.animationadditions.interaction.ArmAim;
import static strm.emfcompat.animationadditions.interaction.Skeleton.WAIST;
import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_HIP;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_HIP;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.animationadditions.interaction.Visibility;
import net.minecraft.client.Minecraft;
import strm.emfcompat.animationadditions.torso.LowReach;
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
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Seated;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
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
    public static final String KEY_STRETCH = "buttonpress.stretch";

    /** Above everything passive: a press takes the hand off a wall or a plant. */
    private static final int PRIORITY = 10;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.12, 0.18, 0.05);

    /** Shoulder to fingertips, pixels. */
    private static final float ARM = Skeleton.ARM_TO_FINGERTIPS;
    private static final float LEG = Skeleton.LEG;
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
        /** A foot on a button: which, {pitch, roll, lift} as shown, and how much of it shows. */
        boolean footRight;
        final float[] leg = new float[5];
        float legWeight;
        /** How far into the reaching pose, 0..1, as shown. */
        float stretch;
        boolean groundReach;
        final LowReach.State lowReach = new LowReach.State();
        final Vector3f leverLoad = new Vector3f();
        final LeverStep leverStep = new LeverStep();
        boolean vanillaLever;
        long tracedAt;
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Press buttons", true,
                "On", "The right hand reaches for a button or a lever in reach before it is used; a foot stamps on a button on the floor.",
                "Off", "Leave the arms and legs to EMF.");
        config.addBoolean(KEY_STRETCH, "Reaching pose", true,
                "On", "Reaching past the arm's length for a button, a lever or a block, the torso leans after the hand, the left arm and leg go back to balance it.",
                "Off", "Only the hand reaches.");
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
        GRIPS.clear();
        state.lean[0] = state.lean[1] = state.lean[2] = 0f;
        float[] legTarget = null;
        float stretchTarget = 0f;
        Vector3f leverWanted = new Vector3f();
        state.vanillaLever = false;
        state.groundReach = false;
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
            BlockPos pressed = pressed(level, state);
            IKFrame frame = context.frame();
            BlockPos target = look(player, frame, state);
            if (pressed != null && level.getBlockState(pressed).getBlock() instanceof ButtonBlock
                    && reachable(player, frame, pressed)) target = pressed;
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
            state.groundReach = player.onGround() && !Seated.seated(player)
                    && (!player.isCrouching() || hand.button.y > RIGHT_SHOULDER.y + 4);
            state.vanillaLever = block.getBlock() instanceof LeverBlock;
            if (state.groundReach)
                leverWanted.set(LeverEffort.shift(hand.button, pressing)).mul(state.vanillaLever ? 1 : .35f);
            if (pressing) {
                state.lean[0] = hand.pitch;
                state.lean[1] = hand.yaw;
            }
            // Past the arm's length the whole body reaches: the torso leans towards the target,
            // forwards and to its side, as far as the reach asks.
            if (!Seated.seated(player) && EMFCompatConfig.getBoolean(KEY_STRETCH, true)) {
                float reach = new Vector3f(hand.button).sub(RIGHT_SHOULDER).length() / ARM;
                stretchTarget = ReachPose.weight(reach);
                if (!state.groundReach) ReachPose.lean(hand.button, stretchTarget, state.lean);
            }
            out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING,
                    Effector.RIGHT_ARM, aim).withTarget(
                    new strm.emfcompat.animationadditions.interaction.ContactTarget(SubLevels.at(player.level(), target), target, block.getBlock())));
            // The press swings the arm; the push is the swing.
            context.claimArms();
            context.decide(pressing ? "press-R" : "hover-R");
        } finally {
            state.leverStep.stride = state.vanillaLever ? 1 : .35f;
            state.leverStep.height = state.vanillaLever ? .35f : .18f;
            state.leverStep.observe(player, context.frame(), state.groundReach, state.button, state.pressedAt);
            state.leverLoad.lerp(leverWanted, Smoothing.follow(dt, leverWanted.lengthSquared() > state.leverLoad.lengthSquared() ? .09 : .14));
            if (state.leverLoad.lengthSquared() < 1e-8f) state.leverLoad.zero();
            legs(state, legTarget, dt);
            state.stretch += (stretchTarget - state.stretch)
                    * (stretchTarget > state.stretch ? Smoothing.fadeIn(dt, ReachPose.SECONDS) : Smoothing.fadeOut(dt, ReachPose.SECONDS));
            if (state.stretch < 1e-3f) state.stretch = 0f;
        }
    }

    /**
     * A button going down, or a lever thrown either way, near the player is a press - whoever
     * looks, it is theirs. The last of them this frame, or {@code null}; what each is now is kept.
     */
    private static BlockPos pressed(Level level, State state) {
        BlockPos pressed = null;
        for (int i = 0; i < state.nearby.size(); i++) {
            BlockState block = level.getBlockState(state.nearby.get(i));
            boolean on = isTarget(block) && on(block);
            boolean was = state.powered.get(i);
            if (block.getBlock() instanceof ButtonBlock ? on && !was : on != was) pressed = state.nearby.get(i);
            state.powered.set(i, on);
        }
        return pressed;
    }

    private static String ineligible(AbstractClientPlayer player) {
        if (!Seated.steady(player) || player.isSleeping()
                || player.isInWaterOrBubble()) return "off:state";
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) return "off:pose";
        if (Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) > SLOW_BELOW) return "off:moving";
        return null;
    }

    /** The buttons round the player's eyes, and whether each is down now. */
    private static void scan(AbstractClientPlayer player, State state) {
        List<BlockPos> found = new ArrayList<>();
        int r = Mth.ceil(SCAN_RADIUS);
        // Round the eyes in the world, and in the plot of any craft near them (see SubLevels).
        for (SubLevels.Space space : SubLevels.around(player.level(), new AABB(player.getEyePosition(), player.getEyePosition()).inflate(r + 1))) {
            BlockPos eye = BlockPos.containing(space.toLocal(player.getEyePosition()));
            for (BlockPos pos : BlockPos.betweenClosed(eye.offset(-r, -r - 1, -r), eye.offset(r, r, r))) {
                if (isTarget(player.level().getBlockState(pos))) found.add(pos.immutable());
            }
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

    /** Levers require a direct block hit; ordinary buttons retain their existing look cone. */
    private static BlockPos look(AbstractClientPlayer player, IKFrame frame, State state) {
        Vec3 eye = player.getEyePosition();
        var hit = strm.emfcompat.animationadditions.net.Inputs.sight(player, 3, 1);
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
            // Proximity/body heading alone must never pull a hand toward a side lever.
            if (block.getBlock() instanceof LeverBlock || ThrottleLever.is(block) || PhysicsAssembler.is(block)) {
                if (hit instanceof net.minecraft.world.phys.BlockHitResult aimed && pos.equals(aimed.getBlockPos())
                        && reachable(player, frame, pos)) return pos;
                continue;
            }
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
            if (dot > viewDot && DebugLog.trace()) {
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
        if (!Visibility.visible(player, pos, grip(player, pos, block))) return false;
        return foot(player, frame, pos, block, null) != null || hand(player, frame, pos, block) != null;
    }

    /**
     * A lever, or a button on a wall: found by the body and followed from well off, the hand
     * pointing at it with no touch - as if thrown or pressed from a distance.
     */
    private static boolean fromAfar(BlockState block) {
        return block.getBlock() instanceof LeverBlock || ThrottleLever.is(block) || PhysicsAssembler.is(block)
                || block.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == AttachFace.WALL;
    }

    /** A button or a lever. */
    private static boolean isTarget(BlockState block) {
        return block.getBlock() instanceof ButtonBlock || block.getBlock() instanceof LeverBlock || ThrottleLever.is(block)
                || PhysicsAssembler.is(block);
    }

    /** Whether it is down or thrown. */
    private static boolean on(BlockState block) {
        // A throttle lever or a physics assembler has no on and off: the hand goes along with its handle.
        return block.hasProperty(BlockStateProperties.POWERED) && block.getValue(BlockStateProperties.POWERED);
    }

    /**
     * Where the hand goes: a button's middle; the end of a lever's handle, which is up when it is
     * off and down when it is on (on a wall; on a floor or a ceiling it leans along its facing),
     * so the hand follows it over when it is thrown.
     */
    private static Vec3 grip(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        // Worked out in the block's own space; on a craft carried out to where it is drawn.
        // Asked for the same block three or four times in one solve - choosing it, checking its
        // reach, aiming the hand: worked out once.
        return GRIPS.computeIfAbsent(pos.asLong(), k -> SubLevels.toWorld(player.level(), pos, localGrip(player, pos, block)));
    }

    /** The grips worked out in the solve going on now; emptied as each one starts. */
    private static final java.util.Map<Long, Vec3> GRIPS = new java.util.HashMap<>();

    private static Vec3 localGrip(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        Level level = player.level();
        if (ThrottleLever.is(block)) {
            Vec3 knob = ThrottleLever.knob(level, pos);
            if (knob != null) return knob;
        }
        if (PhysicsAssembler.is(block)) {
            Vec3 knob = PhysicsAssembler.knob(level, pos, block);
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

    private record Hand(IKResult aim, float pitch, float yaw, Vector3f button) {
    }

    /**
     * The right arm aimed at the button or the lever, with the least torso lean that brings it in
     * reach; {@code null} when even the most does not. Only the right arm presses: with the left,
     * the torso turning for the press left the arm behind and the click's swing fought it.
     */
    private static Hand hand(AbstractClientPlayer player, IKFrame frame, BlockPos pos, BlockState block) {
        Vec3 point = grip(player, pos, block);
        Vector3f model = frame.relativeToJoint(point, new Vector3f());
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
            if (aim != null && aim.reach() <= maxReach) return new Hand(aim, pitch, yaw, model);
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
        if (player.getPose() != Pose.STANDING || Seated.seated(player)) return null;
        Level level = player.level();
        SubLevels.Space space = SubLevels.at(level, pos);
        // On the floor the player stands on: a craft's deck is as high as it is drawn.
        if (space.isWorld() ? pos.getY() != Mth.floor(player.getY() + 1e-3)
                : Math.abs(space.toWorld(Vec3.atBottomCenterOf(pos)).y - player.getY()) > 0.25) return null;
        AABB box = block.getShape(level, pos).bounds();
        Vec3 top = space.toWorld(new Vec3(pos.getX() + box.getCenter().x, pos.getY() + box.maxY, pos.getZ() + box.getCenter().z));
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

    /** The torso turn a hand or a foot on a button asks for; {@code null} when none. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.lean[0] == 0f && state.lean[1] == 0f && state.lean[2] == 0f) return null;
        if (!INSTANCE.isEnabled()) return null;
        float w = state.legWeight > 1e-3f ? state.legWeight
                : InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, INSTANCE.id());
        return TorsoLean.Hint.turn(state.lean[0] * w, state.lean[1] * w, state.lean[2] * w);
    }

    /**
     * Points the arm on a button at its middle from where its shoulder is drawn this frame -
     * after the pack's breathing and swing and the torso's lean have moved it - as much as the
     * arm is this provider's. Called last, after the interaction runtime.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts, java.util.Map<Effector, float[]> base) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        Effector effector = Effector.RIGHT_ARM;
        float w = InteractionRuntime.weight(uuid, effector, INSTANCE.id());
        if (w < 1e-3f) return;
        ModelPart arm = parts.apply(effector.part);
        if (arm == null) return;
        float[] original = base.get(effector);
        if (original == null) return;
        float[] aim = strm.emfcompat.animationadditions.interaction.ContactAim.rotation(original,
                state.button.x - arm.x, state.button.y - arm.y, state.button.z - arm.z, w);
        arm.setRotation(aim[0], aim[1], aim[2]);
    }

    /** Ground-supported reach before the final hand aim, shared with the low crank. */
    public static void reachContact(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        float weight = state.groundReach && INSTANCE.isEnabled() && EMFCompatConfig.getBoolean(KEY_STRETCH, true)
                && InteractionRuntime.weight(uuid, Effector.LEFT_ARM) <= 0.01f
                ? InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, INSTANCE.id()) : 0;
        state.lowReach.weightShift = state.leverLoad.x;
        state.lowReach.weightForward = state.leverLoad.z;
        state.leverStep.apply(parts, weight);
        LowReach.apply(parts, true, state.button, weight, state.lowReach);
        long now = System.nanoTime();
        if ((state.vanillaLever || state.leverStep.consumed > 0) && strm.emfcompat.animationadditions.DebugLog.trace() && now - state.tracedAt > 50_000_000L) {
            state.tracedAt = now;
            org.slf4j.LoggerFactory.getLogger("EMFCompatButtonPress").info(
                    "[LeverPoseTrace] grounded={} weight={} loadX={} loadZ={} pressing={} step={} progress={} footX={} footZ={}",
                    state.groundReach, weight, state.leverLoad.x, state.leverLoad.z,
                    state.pressedAt != NEVER && (now - state.pressedAt) * 1e-9 < PRESS_SECONDS,
                    state.leverStep.foot, state.leverStep.progress, state.leverStep.offset.x, state.leverStep.offset.z);
        }
    }

    /**
     * Puts a foot on its button over whatever the feet were given. Called after the pack has animated.
     */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        float owned = InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, INSTANCE.id());
        ReachPose.balance(parts, true, state.stretch * owned,
                InteractionRuntime.weight(uuid, Effector.LEFT_ARM) < 0.01f, !state.groundReach && !state.lowReach.active());
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
