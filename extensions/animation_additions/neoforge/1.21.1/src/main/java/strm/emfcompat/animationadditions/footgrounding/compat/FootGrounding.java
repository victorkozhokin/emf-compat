package strm.emfcompat.animationadditions.footgrounding.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.Smoothing;

import java.util.UUID;
import java.util.function.Function;

/**
 * Finds the floor under each foot and plants both feet on it.
 *
 * <p>Detection: right before the model is animated the pose stack is the model's space
 * ({@link IKFrame}). The bottom of each straight leg is carried into the world, and a few rays go
 * down from there through block collision shapes, so steps, slabs, snow layers and carpets all
 * count. The highest hit under a foot is its floor; its distance below the foot, in model pixels,
 * is the foot's drop.</p>
 *
 * <p>Body: the model is lowered by the drop of the foot that hangs most (capped at
 * {@link #MAX_LOWER}), so that foot reaches its floor. Both feet can hang at once, on stairs.</p>
 *
 * <p>Legs: each foot is planted on its own floor against the lowered body - a foot whose floor is
 * higher rises by the difference. The leg is pitched forward a little (at most {@link #MAX_BEND})
 * and the rest is taken by moving its hip up, the leg sliding into the torso: the legs are one
 * bone, and bending it the full way looked far worse.</p>
 *
 * <p>Whatever animation plays - walking, crouching, another addon's pose - the leg offset is added
 * on top of it after the pack has animated ({@link #apply}), so nothing is replaced.</p>
 */
public final class FootGrounding {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatFootGrounding");

    private static final Vector3f RIGHT_HIP = new Vector3f(-1.9f, 12f, 0f);
    private static final Vector3f LEFT_HIP = new Vector3f(1.9f, 12f, 0f);
    private static final float LEG = 12f;
    /** Probe offsets around the foot centre, model pixels: the sole is 4x4, so just inside its corners. */
    private static final float[][] PROBES = {{0, 0}, {1.8f, 1.8f}, {-1.8f, 1.8f}, {1.8f, -1.8f}, {-1.8f, -1.8f}};
    /**
     * Rays start this far above the ground level, blocks, so a step in front of the foot - higher
     * than where the foot is now - is seen too. A rise past {@link #MAX_STEP} is a wall.
     */
    private static final double RAY_UP = 0.7;
    private static final double RAY_DOWN = 0.8;

    /** Below this, model pixels, a foot counts as standing on its floor. */
    private static final float MIN_STEP = 0.75f;
    /** Past this the foot is over a drop, not a step, and is left hanging. */
    private static final float MAX_STEP = 10f;
    /** How far the body is lowered at most, model pixels. A slab is ~8.5. */
    private static final float MAX_LOWER = 9f;
    /** How far forward (pitch) a raised leg turns at most; the rest of the rise is the hip moving up. */
    private static final double MAX_BEND = Math.toRadians(12);

    /** A raised leg steps forward a little, and out from the body, model pixels... */
    private static final float STEP_FORWARD = 2f;
    private static final float STEP_OUT = 1.5f;
    /** ...in full once the hip has slid up this far. */
    private static final float STEP_FULL_AT = 3f;

    /** A leg swung forward past this, radians, looks for a step ahead of it. */
    private static final float SWING_FORWARD = 0.1f;

    /**
     * Standing where the hitbox rests on a step neither foot is over, a foot reaches for it: this
     * far from the sole at most, model pixels, forwards or out to its own side, never behind.
     */
    private static final float REACH_MIN = 2.5f;
    private static final float REACH_MAX = 6f;
    private static final float REACH_STEP = 0.5f;
    /** Directions a foot reaches in, model {x, z} per unit, for the right leg (x mirrored for the left). */
    private static final float[][] REACH_WAYS = {{0f, -1f}, {-0.707f, -0.707f}, {-1f, 0f}};
    /** A rise lower than this, model pixels (farmland against grass is ~1), is not worth a foot. */
    private static final float REACH_MIN_RISE = 3f;
    /** The other foot takes the step over only this long after one went up, and when nearer by this much, model pixels. */
    private static final long REACH_HOLD_NANOS = 1_500_000_000L;
    private static final float REACH_KEEP = 1.5f;
    /** Standing still the ground does not change: the step is looked for this often, not every frame. */
    private static final long REACH_EVERY_NANOS = 100_000_000L;

    /** A swing takes this long at least and at most, seconds; it reaches forwards this far at least, radians. */
    private static final double MIN_SWING = 0.1;
    private static final double MAX_SWING = 0.6;
    private static final float MIN_AMP = 0.15f;
    /** Past this share of the swing the landing is held. */
    private static final float HOLD_LANDING_AT = 0.5f;
    /** Over a rise, the swinging foot goes up this share of it more, at the middle of the swing. */
    private static final double ARC = 0.35;
    /** The player model's scale: model pixels are 1/16 of a block times this. */
    private static final float SCALE = 0.9375f;

    /** Faster than this, blocks per tick, the player walks and the weight shifts with the stride. */
    private static final double WALKING = 0.02;
    /** How quickly the weight goes over to the other foot. */
    private static final double SUPPORT_SECONDS = 0.08;
    /** A leg turning slower than this, radians per solve, is not telling which way it swings. */
    private static final float SWING_EPSILON = 1e-3f;

    private static final double LOWER_SECONDS = 0.12;
    /** Walking, the body follows the weight between the feet: quick enough to keep up with a step. */
    private static final double STRIDE_LOWER_SECONDS = 0.05;
    /**
     * Walking, each foot stands where the body as drawn puts it, and changes over quickly both
     * ways: one smoothing slower than the other left both feet up while the weight went over.
     */
    private static final double STRIDE_LEG_SECONDS = 0.03;
    /**
     * Walking with both feet on one level again, the body catches up at once: slowly, both legs
     * slid up to keep the feet down - with one-bone legs that reads as both feet lifted.
     */
    private static final double LEVEL_LOWER_SECONDS = 0.025;
    private static final double RAISE_SECONDS = 0.05;
    private static final double SETTLE_SECONDS = 0.15;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private FootGrounding() {
    }

    private static final class State {
        float lower, rightBend, leftBend;
        /** The legs as the animation left them last frame, {xRot, yRot, zRot}; null before one. */
        float[] rightPose, leftPose;
        /** The legs' pitch at the last solve, to see which way each swings. */
        float lastRightX = Float.NaN, lastLeftX = Float.NaN;
        /**
         * The last three floors read under each foot while walking: the median is used, so a foot
         * right on an edge flickering over it for one frame does not flip the body.
         */
        final float[] rightFloors = {Float.NaN, Float.NaN, Float.NaN}, leftFloors = {Float.NaN, Float.NaN, Float.NaN};
        /** Each foot's step cycle while walking. */
        final Leg right = new Leg(), left = new Leg();
        /** How much of the weight is on the right foot, 0 to 1, while walking. */
        float support = 0.5f;
        /** Whether the last solve was a walking one (the weight shifting with the stride). */
        boolean walking;
        /** A leg reaching for a step standing still: {pitch, roll} of each, smoothed. */
        final float[] rightReach = new float[2], leftReach = new float[2];
        /** The last step looked for standing still, and when; the same spot keeps it. */
        Reach reachFound;
        long reachAt, reachChosenAt;
        /** The foot last put up on a step, standing: true right, null none. */
        Boolean reachFoot;
        double reachX, reachY, reachZ;
        float reachYaw;
        String logged = "";
    }

    /**
     * One foot's step while walking, in the world: where it stands, and while it swings, where it
     * took off from and where it will come down. Heights are world y, so the hitbox stepping up
     * or the body lowering does not move a planted foot.
     */
    private static final class Leg {
        boolean swinging, known;
        /** World y of the floor the foot stands on, or took off from; of where it will land. */
        double plantedY, landingY;
        /** The leg's pitch at take-off (forwards negative, walking direction folded in), its lowest in this swing. */
        float liftPitch, minPitch;
        /** How far the leg swings forwards, radians, and how long a swing takes, seconds: the last swing's. */
        float amp = 0.5f;
        double swingSeconds = 0.25;
        long liftAt;
        float progress;
        float lastPitch = Float.NaN;

        /** In the air on the way to a lower floor than the one it left. */
        boolean descending() {
            return swinging && landingY < plantedY - MIN_STEP * SCALE / 16;
        }

        void reset() {
            swinging = known = false;
            lastPitch = Float.NaN;
            progress = 0f;
        }
    }

    /**
     * Called with the pose stack right before the model is animated: solves (at most every
     * {@link EntityStates#SOLVE_EVERY_NANOS}) and lowers the model by the current amount.
     */
    public static void modelPose(AbstractClientPlayer player, PoseStack stack) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        State state = entry.value;
        double dt = EntityStates.due(entry, now);
        if (dt >= 0) solve(player, stack, state, dt);

        if (state.lower > 1e-3f) {
            // Model space: +y is down, one unit is 16 pixels.
            stack.translate(0f, state.lower / 16f, 0f);
        }
    }

    private static void solve(AbstractClientPlayer player, PoseStack stack, State state, double dt) {
        float targetLower = 0f, right = 0f, left = 0f;
        float plantRight = 0f, plantLeft = 0f;
        float footRight = Float.NaN, footLeft = Float.NaN;
        float[] reachRight = new float[2], reachLeft = new float[2];
        String decided;

        String why = ineligible(player);
        if (why != null) {
            decided = "off:" + why;
        } else {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            IKFrame frame = IKFrame.capture(stack.last().pose(), camera);
            // The body goes by the floor under the hips: steady whatever the stride does. A foot
            // swung far back in the stride is in the air anyway and must not pull the body down.
            // Measured from where the model stands before this frame's lowering.
            // A floor above the straight leg is only the model shifted down by something else
            // (the crouch's render offset, an animation library moving the whole pose): never
            // lift the legs for it.
            float rawRight = drop(player, frame, RIGHT_HIP, null);
            float rawLeft = drop(player, frame, LEFT_HIP, null);
            right = Math.max(0f, rawRight);
            left = Math.max(0f, rawLeft);
            // Down onto the lowest floor under a foot; a foot over a drop-off does not count.
            float low = Math.max(right, left) <= MAX_STEP ? Math.max(right, left) : Math.min(right, left);
            if (low >= MIN_STEP && low <= MAX_STEP) targetLower = Math.min(low, MAX_LOWER);
            decided = targetLower > 0f ? "lowered" : "flat";

            Stride stride = stride(player, frame, state, rawRight, rawLeft, dt);
            state.walking = stride != null;
            if (stride != null) {
                // Walking: the body stands on the foot that carries the weight. The weight goes
                // over with the stride, so the body rises onto the step with the foot on it and
                // sinks with the foot below; the swinging foot is in the air and is not held to
                // its floor. The body never goes above the hitbox: a step ahead the hitbox has
                // not climbed yet is the foot's to go up onto, as before.
                float onFeet = state.support * stride.right + (1f - state.support) * stride.left;
                // Stepping down - off a slab, down a stair - the body goes down with the foot
                // reaching for the lower floor, the other leg bending on the step: legs only get
                // shorter, so held up by the foot behind, the front one hung in the air.
                if (state.right.descending()) onFeet = Math.max(onFeet, stride.right);
                if (state.left.descending()) onFeet = Math.max(onFeet, stride.left);
                targetLower = Math.max(0f, Math.min(MAX_LOWER, onFeet));
                // The feet are placed below, against the body as it is drawn this frame.
                decided = "stride";
                footRight = stride.right;
                footLeft = stride.left;
            } else {
                plantRight = plant(player, frame, RIGHT_HIP, state.rightPose, targetLower, rawRight);
                plantLeft = plant(player, frame, LEFT_HIP, state.leftPose, targetLower, rawLeft);
                // Both feet on one level with a step next to them - the hitbox resting on it (none
                // of the feet over it) or the player standing in front of it: one foot goes up.
                boolean level = Math.abs(right - left) < MIN_STEP && right <= MAX_STEP;
                if (level) {
                    long now = System.nanoTime();
                    boolean same = now - state.reachAt < REACH_EVERY_NANOS && player.getX() == state.reachX
                            && player.getY() == state.reachY && player.getZ() == state.reachZ
                            && player.yBodyRot == state.reachYaw;
                    if (!same) {
                        state.reachFound = keep(state, reach(player, frame, rawRight, rawLeft, true),
                                reach(player, frame, rawRight, rawLeft, false), now);
                        state.reachAt = now;
                        state.reachX = player.getX();
                        state.reachY = player.getY();
                        state.reachZ = player.getZ();
                        state.reachYaw = player.yBodyRot;
                    }
                    Reach reach = state.reachFound;
                    if (reach != null) {
                        float[] angles = reach.right ? reachRight : reachLeft;
                        angles[0] = reach.pitch;
                        angles[1] = reach.roll;
                        float lift = Math.max(0f, Math.min(MAX_STEP, targetLower - reach.floor));
                        if (reach.right) plantRight = lift;
                        else plantLeft = lift;
                        decided = reach.right ? "reach-R" : "reach-L";
                    }
                }
            }
        }

        // Body and legs both from their targets, not the legs from the smoothed body: the legs
        // neither lag the body nor keep turning after it has settled. A foot goes up onto a step
        // quickly, so it does not sink into it, and comes back down gently.
        boolean striding = decided.equals("stride");
        boolean level = striding && Math.abs(footRight - footLeft) < MIN_STEP;
        float k = Smoothing.snapFirst(dt, level ? LEVEL_LOWER_SECONDS : striding ? STRIDE_LOWER_SECONDS : LOWER_SECONDS);
        state.lower += (targetLower - state.lower) * k;
        if (striding) {
            plantRight = Math.max(0f, Math.min(MAX_STEP, state.lower - footRight));
            plantLeft = Math.max(0f, Math.min(MAX_STEP, state.lower - footLeft));
            float kLeg = Smoothing.snapFirst(dt, STRIDE_LEG_SECONDS);
            state.rightBend += (plantRight - state.rightBend) * kLeg;
            state.leftBend += (plantLeft - state.leftBend) * kLeg;
            if (state.lower < 0.05f && plantRight < 0.05f && plantLeft < 0.05f) decided = "flat";
        } else {
            state.rightBend += (plantRight - state.rightBend)
                    * Smoothing.snapFirst(dt, plantRight > state.rightBend ? RAISE_SECONDS : SETTLE_SECONDS);
            state.leftBend += (plantLeft - state.leftBend)
                    * Smoothing.snapFirst(dt, plantLeft > state.leftBend ? RAISE_SECONDS : SETTLE_SECONDS);
        }
        for (int i = 0; i < 2; i++) {
            state.rightReach[i] += (reachRight[i] - state.rightReach[i])
                    * Smoothing.snapFirst(dt, reachRight[i] != 0f ? RAISE_SECONDS : SETTLE_SECONDS);
            state.leftReach[i] += (reachLeft[i] - state.leftReach[i])
                    * Smoothing.snapFirst(dt, reachLeft[i] != 0f ? RAISE_SECONDS : SETTLE_SECONDS);
        }

        // Per-frame trace while the feet do anything; debug only.
        if (FootGroundingFeature.isTrace() && why == null && (state.lower > 0.05f || state.rightBend > 0.05f || state.leftBend > 0.05f
                || plantRight > 0.05f || plantLeft > 0.05f)) {
            LOGGER.info("[FootTrace] x={} y={} z={} R={} L={} fR={} fL={} w={} low={} tl={} pr={} pl={} rb={} lb={} rp={} lp={}",
                    String.format("%.3f", player.getX()), String.format("%.3f", player.getY()),
                    String.format("%.3f", player.getZ()),
                    String.format("%.2f", right), String.format("%.2f", left),
                    String.format("%.2f", footRight), String.format("%.2f", footLeft),
                    String.format("%.2f", state.support),
                    String.format("%.2f", state.lower), String.format("%.2f", targetLower),
                    String.format("%.2f", plantRight), String.format("%.2f", plantLeft),
                    String.format("%.2f", state.rightBend), String.format("%.2f", state.leftBend),
                    state.rightPose == null ? "-" : String.format("%.2f", state.rightPose[0]),
                    state.leftPose == null ? "-" : String.format("%.2f", state.leftPose[0]));
        }

        // One line per change of what was decided, not per frame.
        if (!decided.equals(state.logged)) {
            LOGGER.info("[FootGrounding] {} {} (R={} L={})", player.getName().getString(), decided,
                    String.format("%.2f", right), String.format("%.2f", left));
            state.logged = decided;
        }
    }

    /**
     * How far a foot rises against the body lowered by {@code lower}: onto its own floor, and a
     * foot the animation swings forward over a higher step goes up onto the step, in time with the
     * stride.
     */
    private static float plant(AbstractClientPlayer player, IKFrame frame, Vector3f hip, float[] pose,
                               float lower, float hipDrop) {
        float under = Math.max(0f, hipDrop);
        float plant = Math.max(0f, Math.min(MAX_STEP, lower - under));
        if (pose != null && pose[0] < -SWING_FORWARD) {
            // Measured from the floor under the hip, so a model shifted down cancels out.
            float ahead = drop(player, frame, hip, pose) - Math.min(0f, hipDrop);
            if (ahead < under - MIN_STEP) plant = Math.max(plant, Math.max(0f, Math.min(MAX_STEP, lower - ahead)));
        }
        return plant;
    }

    /** A foot reaching for a step: which, how the leg turns, the step's floor (model pixels) and how far it reaches. */
    private record Reach(boolean right, float pitch, float roll, float floor, float distance) {
    }

    /**
     * Which foot goes up: the one already up keeps the step while it can still reach it, and the
     * other takes over only when clearly nearer and not before {@link #REACH_HOLD_NANOS} - else a
     * player settling on the spot put one foot up, then the other at once, and ended with both up.
     */
    private static Reach keep(State state, Reach right, Reach left, long now) {
        Boolean was = state.reachFoot;
        boolean held = was != null && now - state.reachChosenAt < REACH_HOLD_NANOS;
        Reach mine = was == null ? null : was ? right : left;
        Reach other = was == null ? null : was ? left : right;
        Reach pick;
        if (mine != null) {
            boolean switchOver = !held && other != null && other.distance < mine.distance - REACH_KEEP;
            pick = switchOver ? other : mine;
        } else if (held) {
            // The foot that went up has just lost the step: the other one waits all the same.
            pick = null;
        } else {
            pick = right == null ? left : left == null ? right : right.distance <= left.distance ? right : left;
        }
        if (pick != null && (was == null || pick.right != was)) {
            state.reachFoot = pick.right;
            state.reachChosenAt = now;
        }
        if (pick == null && !held) state.reachFoot = null;
        return pick;
    }

    /**
     * The nearest step this foot can put itself on by turning the leg, forwards or out to its own
     * side - never behind, never across the other leg - or {@code null}. The leg is one straight
     * bone from the hip, so a sole moved {@code d} pixels means the leg turned by asin(d / leg).
     */
    private static Reach reach(AbstractClientPlayer player, IKFrame frame, float rawRight, float rawLeft, boolean isRight) {
        Reach best = null;
        float bestDistance = Float.MAX_VALUE;
        {
            Vector3f hip = isRight ? RIGHT_HIP : LEFT_HIP;
            float hipDrop = isRight ? rawRight : rawLeft;
            float shift = Math.min(0f, hipDrop);
            float mirror = isRight ? 1f : -1f;
            for (float[] way : REACH_WAYS) {
                for (float d = REACH_MIN; d <= REACH_MAX && d < bestDistance; d += REACH_STEP) {
                    float dx = way[0] * mirror * d, dz = way[1] * d;
                    float floor = dropAt(player, frame, hip, dx, dz) - shift;
                    if (floor > Math.max(0f, hipDrop) - REACH_MIN_RISE || floor < -MAX_STEP) continue;
                    float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, dz / LEG)));
                    // Roll: +z rotation carries the sole towards -x.
                    float roll = (float) Math.asin(Math.max(-1f, Math.min(1f, -dx / LEG)));
                    best = new Reach(isRight, pitch, roll, floor, d);
                    bestDistance = d;
                    break;
                }
            }
        }
        return best;
    }

    /** {@link #drop} for a sole moved {dx, dz} model pixels from under the straight leg. */
    private static float dropAt(AbstractClientPlayer player, IKFrame frame, Vector3f hip, float dx, float dz) {
        // A hip moved along with the sole keeps its height, so the drop is from the same ground.
        return drop(player, frame, new Vector3f(hip).add(dx, 0f, dz), null);
    }

    /** Where each foot's floor is while walking, model pixels below the ground level. */
    private record Stride(float right, float left) {
    }

    /**
     * Walking: finds which foot carries the weight and moves {@code state.support} towards it, and
     * returns the floor under each foot where the animation puts it. {@code null} standing still,
     * or before the animation has been seen.
     *
     * <p>The foot on the ground is the one the leg sweeps backwards (walking forwards: its pitch
     * grows); the other swings through the air. Read off the pack's own animation, so the weight
     * shifts in time with whatever stride the pack draws.</p>
     */
    private static Stride stride(AbstractClientPlayer player, IKFrame frame, State state,
                                 float rawRight, float rawLeft, double dt) {
        float[] r = state.rightPose, l = state.leftPose;
        Vec3 motion = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        if (r == null || l == null || motion.length() < WALKING) {
            state.lastRightX = Float.NaN;
            state.lastLeftX = Float.NaN;
            state.support = 0.5f;
            state.right.reset();
            state.left.reset();
            return null;
        }
        float swingRight = Float.isNaN(state.lastRightX) ? 0f : r[0] - state.lastRightX;
        float swingLeft = Float.isNaN(state.lastLeftX) ? 0f : l[0] - state.lastLeftX;
        state.lastRightX = r[0];
        state.lastLeftX = l[0];
        // Backwards, the foot on the ground sweeps forwards instead.
        Vec3 facing = Vec3.directionFromRotation(0, player.yBodyRot);
        float way = motion.dot(facing) >= 0 ? 1f : -1f;
        float difference = (swingRight - swingLeft) * way;
        if (Math.abs(difference) > SWING_EPSILON) {
            float target = difference > 0 ? 1f : 0f;
            state.support += (target - state.support) * Smoothing.snapFirst(dt, SUPPORT_SECONDS);
        }
        // Blocks a second, for where a swinging foot will come down.
        Vec3 velocity = motion.scale(20);
        float right = step(player, frame, state.right, RIGHT_HIP, r, rawRight, way, velocity);
        float left = step(player, frame, state.left, LEFT_HIP, l, rawLeft, way, velocity);
        return new Stride(right, left);
    }

    /**
     * Moves one foot's step on by this frame and returns its floor, model pixels below the ground
     * level, as {@link #footFloor}.
     *
     * <p>Read off the pack's leg: going forwards it swings, going back it stands. At take-off the
     * landing is foreseen - the hip carried on by the player's speed for what is left of the swing,
     * the foot out in front by the swing's reach - and the floor there is the one it comes down on;
     * the guess is kept up to date for the first half of the swing, then held. In the air the foot
     * goes from the floor it left to that one along the swing, up in an arc over a rise. Down, it
     * stands on the floor that is really under it and keeps it until it lifts again: an edge the
     * hitbox wobbles over no longer moves a planted foot.</p>
     */
    private static float step(AbstractClientPlayer player, IKFrame frame, Leg leg, Vector3f hip, float[] pose,
                              float hipDrop, float way, Vec3 velocity) {
        float pitch = pose[0] * way;
        long now = System.nanoTime();
        if (!leg.known) {
            leg.plantedY = floorY(player, frame, hip, pose, Vec3.ZERO, hipDrop);
            leg.known = true;
        }
        float turn = Float.isNaN(leg.lastPitch) ? 0f : pitch - leg.lastPitch;
        leg.lastPitch = pitch;
        if (!leg.swinging && turn < -SWING_EPSILON) {
            // Take-off.
            leg.swinging = true;
            leg.liftAt = now;
            leg.liftPitch = pitch;
            leg.minPitch = pitch;
            leg.progress = 0f;
            leg.landingY = landing(player, frame, leg, hip, pose, hipDrop, velocity);
        } else if (leg.swinging && turn > SWING_EPSILON) {
            // Touch-down: what is really under the foot now.
            leg.swinging = false;
            double took = (now - leg.liftAt) / 1e9;
            leg.swingSeconds = Math.max(MIN_SWING, Math.min(MAX_SWING, took));
            leg.amp = Math.max(MIN_AMP, -leg.minPitch);
            leg.plantedY = floorY(player, frame, hip, pose, Vec3.ZERO, hipDrop);
        }
        double y;
        if (leg.swinging) {
            leg.minPitch = Math.min(leg.minPitch, pitch);
            float span = leg.liftPitch + leg.amp;
            leg.progress = span < 1e-3f ? 1f : Math.max(leg.progress, Math.min(1f, (leg.liftPitch - pitch) / span));
            if (leg.progress < HOLD_LANDING_AT) leg.landingY = landing(player, frame, leg, hip, pose, hipDrop, velocity);
            float t = leg.progress * leg.progress * (3f - 2f * leg.progress);
            y = leg.plantedY + (leg.landingY - leg.plantedY) * t;
            // Over a rise the foot goes up in an arc, clearing the edge before it gets there.
            double rise = leg.landingY - leg.plantedY;
            if (rise > 0) y += rise * ARC * Math.sin(Math.PI * leg.progress);
            y = Math.max(y, Math.min(leg.plantedY, leg.landingY));
        } else {
            y = leg.plantedY;
        }
        return floorBelow(frame, hip, pose, y, hipDrop);
    }

    /** The floor under where the swinging foot will come down, world y. */
    private static double landing(AbstractClientPlayer player, IKFrame frame, Leg leg, Vector3f hip, float[] pose,
                                  float hipDrop, Vec3 velocity) {
        double left = leg.swingSeconds * (1f - leg.progress);
        // The foot at the front of the swing, where it lands, against where it is now.
        Vector3f now = sole(pose);
        Vector3f front = new Vector3f(0f, LEG, 0f);
        new Quaternionf().rotationX(-leg.amp).transform(front);
        Vec3 from = frame.jointWorld(new Vector3f(hip).add(now.x, LEG, now.z));
        Vec3 to = frame.jointWorld(new Vector3f(hip).add(front.x, LEG, front.z));
        Vec3 ahead = new Vec3(to.x - from.x, 0, to.z - from.z).add(velocity.scale(left));
        double y = floorY(player, frame, hip, pose, ahead, hipDrop);
        // More than a step up or down is a wall or a drop, not a place to land: the foot stays level.
        if (Double.isNaN(y) || Math.abs(y - leg.plantedY) > MAX_STEP * SCALE / 16) return leg.plantedY;
        return y;
    }

    /** Where the bottom of the leg is across the ground, model pixels from the hip. */
    private static Vector3f sole(float[] pose) {
        Vector3f sole = new Vector3f(0f, LEG, 0f);
        if (pose != null) new Quaternionf().rotationZYX(pose[2], pose[1], pose[0]).transform(sole);
        return sole;
    }

    /**
     * The world y of the floor under the foot, moved {@code offset} blocks across the ground; the
     * floor under the hip when there is none within a step.
     */
    private static double floorY(AbstractClientPlayer player, IKFrame frame, Vector3f hip, float[] pose,
                                 Vec3 offset, float hipDrop) {
        Vector3f sole = sole(pose);
        Vector3f base = new Vector3f(hip).add(sole.x, LEG, sole.z);
        Vec3 best = null;
        for (float[] probe : PROBES) {
            Vec3 foot = frame.jointWorld(new Vector3f(base).add(probe[0], 0f, probe[1])).add(offset);
            BlockHitResult hit = player.level().clip(new ClipContext(
                    foot.add(0, RAY_UP, 0), foot.add(0, -RAY_DOWN, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) continue;
            if (hit.isInside() || hit.getLocation().y >= foot.y + RAY_UP - 0.01) continue;
            if (best == null || hit.getLocation().y > best.y) best = hit.getLocation();
        }
        Vec3 ground = frame.jointWorld(new Vector3f(hip).add(0f, LEG, 0f));
        if (best == null) return ground.y - standing(hipDrop) * SCALE / 16;
        return best.y;
    }

    /** A floor at world y under the foot as {@link #footFloor} gives it: model pixels below the ground level. */
    private static float floorBelow(IKFrame frame, Vector3f hip, float[] pose, double y, float hipDrop) {
        Vector3f sole = sole(pose);
        Vec3 centre = frame.jointWorld(new Vector3f(hip).add(sole.x, LEG, sole.z));
        float drop = frame.relativeToJoint(new Vec3(centre.x, y, centre.z), hip).y - LEG;
        float foot = drop - Math.min(0f, hipDrop);
        if (foot > MAX_STEP || foot < -MAX_STEP) foot = standing(hipDrop);
        return foot;
    }

    /** Pushes {@code value} into the last three and returns their median (fewer at the start). */
    private static float median(float[] last, float value) {
        last[0] = last[1];
        last[1] = last[2];
        last[2] = value;
        if (Float.isNaN(last[0])) return Float.isNaN(last[1]) ? value : Math.max(last[1], value);
        float a = last[0], b = last[1], c = last[2];
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    private static float footFloor(AbstractClientPlayer player, IKFrame frame, Vector3f hip, float[] pose,
                                   float hipDrop) {
        float shift = Math.min(0f, hipDrop);
        float foot = drop(player, frame, hip, pose) - shift;
        if (foot > MAX_STEP || foot < -MAX_STEP) foot = standing(hipDrop);
        return foot;
    }

    /**
     * The floor under the hip when a foot has none of its own, pixels below the ground level: none
     * lower than a step - over a drop-off (the edge of a block, crouching on it) the foot stays at the
     * ground level, as in vanilla, instead of the body sinking into the air after it.
     */
    private static float standing(float hipDrop) {
        return hipDrop > MAX_STEP ? 0f : Math.max(0f, hipDrop);
    }

    /** Why the feet are left alone this frame, or {@code null} when they are grounded. */
    private static String ineligible(AbstractClientPlayer player) {
        if (!FootGroundingFeature.isEnabled() || !EMFCompatCore.isCompatEnabled()) return "disabled";
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return "first-person";
        // Whatever animation plays, the offset goes on top of it; only a pose with no floor under
        // the feet is left alone.
        if (!player.onGround()) return "airborne";
        if (player.isPassenger() || player.isInWaterOrBubble() || player.isFallFlying()
                || player.isSleeping()) return "state";
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) return "pose";
        return null;
    }

    /**
     * How far below the bottom of the straight leg its floor is, model pixels; the ray length if
     * there is none. The highest hit of the probes wins: a sole half over an edge still stands.
     */
    private static float drop(AbstractClientPlayer player, IKFrame frame, Vector3f hip, float[] pose) {
        // Where the foot is across the ground; its height is the straight leg's, the ground level.
        Vector3f sole = new Vector3f(0f, LEG, 0f);
        if (pose != null) new Quaternionf().rotationZYX(pose[2], pose[1], pose[0]).transform(sole);
        Vector3f base = new Vector3f(hip).add(sole.x, LEG, sole.z);
        Vec3 best = null;
        for (float[] probe : PROBES) {
            Vec3 foot = frame.jointWorld(new Vector3f(base).add(probe[0], 0f, probe[1]));
            BlockHitResult hit = player.level().clip(new ClipContext(
                    foot.add(0, RAY_UP, 0), foot.add(0, -RAY_DOWN, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) continue;
            // A ray that starts inside a block (a wall ahead) hits right where it starts.
            if (hit.isInside() || hit.getLocation().y >= foot.y + RAY_UP - 0.01) continue;
            if (best == null || hit.getLocation().y > best.y) best = hit.getLocation();
        }
        if (best == null) return (float) (RAY_DOWN * 16);
        Vec3 centre = frame.jointWorld(base);
        float drop = frame.relativeToJoint(new Vec3(centre.x, best.y, centre.z), hip).y - LEG;
        // Higher than a step: a wall, the foot stays where it is.
        return drop < -MAX_STEP ? 0f : drop;
    }

    /**
     * Keeps the legs as the pack animated them, before any offset of ours, so the next frame looks
     * under the feet where they really are. Called from the main model's animation only.
     */
    public static void recordAnimated(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        state.rightPose = pose(parts.apply("right_leg"));
        state.leftPose = pose(parts.apply("left_leg"));
    }

    private static float[] pose(ModelPart leg) {
        return leg == null ? null : new float[]{leg.xRot, leg.yRot, leg.zRot};
    }

    /**
     * What to add to a leg on top of its animation: {pitch, lift}. Pitch forward in radians
     * (negative xRot), lift = how far the hip pivot moves up, pixels. {@code null} when the leg
     * is left as animated.
     */
    public static float[] legOffset(UUID uuid, boolean right) {
        State state = STATES.fresh(uuid);
        if (state == null) return null;
        float bend = right ? state.rightBend : state.leftBend;
        float[] reach = right ? state.rightReach : state.leftReach;
        boolean reaching = Math.abs(reach[0]) > 1e-3f || Math.abs(reach[1]) > 1e-3f;
        if (bend < 0.05f && !reaching) return null;
        double theta;
        float lift;
        if (reaching) {
            // Turned out to the step: the turn raises the sole a little, the hip takes the rest.
            double cos = Math.cos(reach[0]) * Math.cos(reach[1]);
            theta = 0;
            lift = Math.max(0f, bend - LEG * (1f - (float) cos));
        } else {
            theta = Math.min(MAX_BEND, Math.acos(Math.max(0f, (LEG - bend) / LEG)));
            // What the slight bend leaves, the hip takes: the leg slides up into the torso.
            lift = Math.max(0f, bend - LEG * (1f - (float) Math.cos(theta)));
        }
        return new float[]{-(float) theta + reach[0], lift, reach[1]};
    }

    /**
     * What the feet ask of the torso: {side, climb, reach}. {@code side} is where the weight is,
     * -1 on the right foot to +1 on the left (model x); {@code climb} 0..1 how far a foot is up on
     * a step; {@code reach} 0..1 how far a foot reaches forwards for one. {@code null} when the
     * feet are left alone.
     */
    public static float[] torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null) return null;
        float up = Math.max(state.rightBend, state.leftBend);
        float side;
        if (state.walking) {
            side = 1f - 2f * state.support;
        } else {
            // Standing with one foot up, the weight is on the straight leg.
            side = Math.max(-1f, Math.min(1f, (state.rightBend - state.leftBend) / MAX_STEP));
            if (up < MIN_STEP) side = 0f;
        }
        float climb = Math.min(1f, up / MAX_STEP);
        float reach = Math.min(1f, Math.max(-state.rightReach[0], -state.leftReach[0]) / 0.5f);
        if (up < MIN_STEP && reach <= 0f && !state.walking) return null;
        // Walking on the flat, the stride sways nothing: only a step makes the weight count.
        if (state.walking && climb <= 0f) side = 0f;
        return new float[]{side, climb, Math.max(0f, reach)};
    }

    /** Adds the leg offsets on top of the animated legs. Called after the pack has animated. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        offset(parts.apply("right_leg"), legOffset(uuid, true), -1f);
        offset(parts.apply("left_leg"), legOffset(uuid, false), 1f);
    }

    /**
     * A raised leg also steps forward a little and out from the body, so the top
     * of the leg sliding up does not go into the torso. {@code side} is -1 right, +1 left (model x).
     */
    private static void offset(ModelPart leg, float[] offset, float side) {
        if (leg == null || offset == null) return;
        float step = Math.min(1f, offset[1] / STEP_FULL_AT);
        leg.xRot += offset[0];
        leg.zRot += offset[2];
        leg.y -= offset[1];
        leg.z -= STEP_FORWARD * step;
        leg.x += side * STEP_OUT * step;
    }
}
