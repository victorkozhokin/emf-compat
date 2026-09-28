package strm.emfcompat.animationadditions.stepassist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;
import strm.emfcompat.animationadditions.mixin.WalkAnimationStateAccessor;
import strm.emfcompat.animationadditions.motion.Spring;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stair climbing as a gait of its own: on stairs and slab steps every step lands on a step of its
 * own, slowly enough to be seen.
 *
 * <p>Stairs are found ahead along the way: rises (or drops) one after another, each a step's length
 * {@code L} apart - half a block for stairs, a block for slabs. On them:</p>
 * <ul>
 *   <li><b>The stride is one step long.</b> The legs are drawn from the distance walked (the walk's
 *   phase, the pack's {@code limb_swing}, grows with every block), so a pack's step is always the
 *   same length, ~1.2 blocks. On stairs the phase goes that much quicker - a steady rate, the pack's
 *   step over {@code L} - so one step covers one step. A slow nudge of at most
 *   {@link #NUDGE} brings the foot in the air onto the middle of its step.</li>
 *   <li><b>The local player slows down</b> to {@link #CADENCE} steps a second (a transient
 *   modifier on its movement speed): at the pack's pace a step every half block would be a blur.
 *   Slower is never a problem for a server, so none is needed.</li>
 * </ul>
 * <p>Everyone watched gets the stride; their own game slows them down if they have the mod.</p>
 */
public final class StepAssist {

    public static final String KEY_ENABLED = "stepassist.enabled";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatStepAssist");
    private static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("emf_compat_animation_additions", "stairs");

    /** Steps a second on stairs, walking and sprinting. */
    private static final double CADENCE = 2.4;
    private static final double SPRINT_CADENCE = 3.6;
    /** The speed on stairs as a share of the usual, at least and at most. */
    private static final double MIN_SPEED = 0.2;
    private static final double MAX_SPEED = 1.0;
    /** At most this much quicker or slower, for the foot to land on a step's middle. */
    private static final float NUDGE = 0.15f;
    /** A rise or a drop of the floor, blocks. */
    private static final double MIN_RISE = 0.2;
    /** Steps this long at least and at most, blocks. */
    private static final double MIN_TREAD = 0.3;
    private static final double MAX_TREAD = 1.3;
    /** The floor is read this far behind and ahead of the player, blocks, this finely. */
    private static final double BEHIND = 0.4;
    private static final double AHEAD = 2.2;
    private static final double SAMPLE = 1 / 16.0;
    /** Half-lives, seconds: the stride's rate and the speed settle into stairs this smoothly. */
    private static final double RATE_HALFLIFE = 0.12;
    private static final double SPEED_HALFLIFE = 0.15;
    private static final double TICK = 0.05;
    private static final double RANGE = 48;
    /** Stairs lost sight of are kept this long, seconds: going down, the hitbox leaves the ground on every step. */
    private static final double KEEP_SECONDS = 0.4;

    private static final class Walker {
        float lastWalk = Float.NaN;
        Stairs stairs;
        long stairsAt;
        final Spring rate = new Spring();
        final Spring speed = new Spring();
        String logged = "";

        Walker() {
            rate.set(1f);
            speed.set(1f);
        }
    }

    private static final Map<UUID, Walker> WALKERS = new HashMap<>();

    private StepAssist() {
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Stair climbing", true,
                "On", "On stairs and slab steps each foot lands on a step of its own, and you slow down enough to see it.",
                "Off", "Stairs are walked like flat ground.");
    }

    /** Stairs ahead: how long a step is and where the next one's middle is, blocks along the way. */
    private record Stairs(double tread, double nextMiddle, double nextY) {
    }

    /** Every client tick, after the players have moved and their walk has gone on. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            WALKERS.clear();
            return;
        }
        boolean on = EMFCompatConfig.getBoolean(KEY_ENABLED, true);
        Map<UUID, Walker> seen = new HashMap<>();
        for (Player player : level.players()) {
            if (player.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            Walker w = WALKERS.computeIfAbsent(player.getUUID(), k -> new Walker());
            seen.put(player.getUUID(), w);
            Vec3 move = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
            Stairs stairs = on && player.onGround() && move.length() > 0.01 ? stairs(player, move.normalize()) : null;
            // Going down the hitbox drops off every step, off the ground for a moment: the stairs stay.
            long now = System.nanoTime();
            if (stairs != null) {
                w.stairs = stairs;
                w.stairsAt = now;
            } else if (on && w.stairs != null && move.length() > 0.01 && (now - w.stairsAt) / 1e9 < KEEP_SECONDS) {
                stairs = w.stairs;
            }

            float rateTarget = 1f, speedTarget = 1f;
            if (stairs != null) {
                // The pack's step in the walk's units, over the step's length: units a block wanted.
                float wanted = (float) (FootGrounding.walkPerStep(player.getUUID()) / stairs.tread);
                float natural = naturalPerBlock(player, move.length());
                rateTarget = wanted / natural;
                rateTarget *= nudge(player, move.normalize(), stairs);
                double cadence = player.isSprinting() ? SPRINT_CADENCE : CADENCE;
                double usual = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 43.17
                        * (player.isSprinting() ? 1.3 : 1.0);
                speedTarget = (float) Math.max(MIN_SPEED, Math.min(MAX_SPEED, stairs.tread * cadence / usual));
            }
            w.rate.update(rateTarget, RATE_HALFLIFE, TICK);
            w.speed.update(speedTarget, SPEED_HALFLIFE, TICK);

            WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) player.walkAnimation;
            float position = walk.emfcompat$position();
            if (!Float.isNaN(w.lastWalk) && Math.abs(w.rate.value - 1f) > 1e-3f) {
                float gone = position - w.lastWalk;
                if (gone > 0) {
                    position = w.lastWalk + gone * w.rate.value;
                    walk.emfcompat$setPosition(position);
                }
            }
            w.lastWalk = position;
            if (player == mc.player) speed(mc.player, w.speed.value);
            log(player, w, stairs);
        }
        WALKERS.keySet().retainAll(seen.keySet());
    }

    /** The walk's phase per block the vanilla walk gives at this speed (blocks a tick): 4, capped at 1 a tick. */
    private static float naturalPerBlock(Player player, double perTick) {
        if (perTick < 1e-3) return 4f;
        return (float) (Math.min(perTick * 4, 1.0) / perTick);
    }

    /** A little quicker or slower so the foot in the air lands on the next step's middle. */
    private static float nudge(Player player, Vec3 dir, Stairs stairs) {
        float[] landing = FootGrounding.landing(player.getUUID());
        if (landing == null) return 1f;
        double land = (landing[0] - player.getX()) * dir.x + (landing[1] - player.getZ()) * dir.z;
        double error = stairs.nextMiddle - land;
        // Landing short of the middle: a longer step, the legs slower; past it, quicker.
        double share = error / stairs.tread;
        return (float) Math.max(1 - NUDGE, Math.min(1 + NUDGE, 1 - share * NUDGE * 2));
    }

    /**
     * Stairs along {@code dir}: two rises (or two drops) in a row ahead, a step's length apart;
     * {@code null} on the flat or at a single step.
     */
    private static Stairs stairs(Player player, Vec3 dir) {
        double base = player.getY();
        List<double[]> edges = new ArrayList<>();
        double last = Double.NaN;
        for (double t = -BEHIND; t <= AHEAD; t += SAMPLE) {
            double y = floor(player, dir, t, base);
            if (Double.isNaN(y)) break;
            if (!Double.isNaN(last) && Math.abs(y - last) > MIN_RISE) edges.add(new double[]{t, y - last, y});
            last = y;
        }
        if (edges.size() < 2) return null;
        double[] a = edges.get(0), b = edges.get(1);
        if (Math.signum(a[1]) != Math.signum(b[1])) return null;
        double tread = b[0] - a[0];
        if (tread < MIN_TREAD || tread > MAX_TREAD) return null;
        // The next step: the first edge ahead of the player's feet.
        double[] next = a[0] > 0.05 ? a : b;
        return new Stairs(tread, next[0] + tread / 2, next[2]);
    }

    /** The floor under {@code t} blocks along the way, within two steps of {@code near}; NaN when none. */
    private static double floor(Player player, Vec3 dir, double t, double near) {
        Vec3 at = new Vec3(player.getX() + dir.x * t, near, player.getZ() + dir.z * t);
        BlockHitResult hit = player.level().clip(new ClipContext(at.add(0, 1.1, 0), at.add(0, -1.1, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) return Double.NaN;
        if (hit.getLocation().y >= at.y + 1.1 - 1e-3) return Double.NaN;
        return hit.getLocation().y;
    }

    private static void speed(LocalPlayer player, float factor) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (Math.abs(factor - 1f) < 1e-3f) speed.removeModifier(SPEED_ID);
        else speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, factor - 1f,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void log(Player player, Walker w, Stairs stairs) {
        String now = stairs == null ? "flat" : String.format("stairs %.2f", stairs.tread);
        if (!now.equals(w.logged) || FootGroundingFeature.isTrace() && stairs != null) {
            LOGGER.info("[StepAssist] {} {} stride x{} speed x{}", player.getName().getString(), now,
                    String.format("%.2f", w.rate.value), String.format("%.2f", w.speed.value));
            w.logged = now;
        }
    }
}
