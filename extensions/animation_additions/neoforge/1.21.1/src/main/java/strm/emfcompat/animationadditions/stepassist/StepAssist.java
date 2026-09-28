package strm.emfcompat.animationadditions.stepassist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
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
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Step matching: on stairs and slabs every step lands on a step of its own, on its middle.
 *
 * <p>The legs are not drawn from time but from the distance walked: the walk's phase
 * ({@code WalkAnimationState.position}, the pack's {@code limb_swing}) grows with every block
 * gone, so a foot comes down every ~1.2 blocks whatever the speed, and slowing the player down
 * slows the legs as much - the foot would land on the same spot. So the phase is what is tuned:
 * while a foot is in the air its swing is slowed down or sped up (at most {@link #MIN_RATE} ..
 * {@link #MAX_RATE} of its own pace) so that, at the player's speed, it comes down on the middle
 * of the step the foot steps ({@link FootGrounding}) foresee it on. Only the drawing changes: the
 * player moves as in vanilla, for everyone watching too.</p>
 */
public final class StepAssist {

    public static final String KEY_ENABLED = "stepassist.enabled";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatStepAssist");

    /** How much slower or quicker a swing may go than its own pace. */
    private static final float MIN_RATE = 0.6f;
    private static final float MAX_RATE = 1.5f;
    /** A landing this much higher or lower than the take-off is a step, blocks. */
    private static final double MIN_RISE = 0.2;
    /** How far along the way the step is looked for from the landing, blocks, and how finely. */
    private static final double SEARCH = 0.7;
    private static final double SAMPLE = 1 / 16.0;
    /** Nearer the middle than this, blocks, is on it. */
    private static final double ON_MIDDLE = 0.04;
    /** Players this far away and more are left alone, blocks. */
    private static final double RANGE = 48;

    private static final Map<UUID, Float> LAST = new HashMap<>();
    private static final Map<UUID, String> LOGGED = new HashMap<>();

    private StepAssist() {
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Step matching", true,
                "On", "On stairs and slabs the stride is timed so each foot lands on a step of its own, on its middle.",
                "Off", "The stride as the pack draws it.");
    }

    /** Every client tick, after the players have moved and their walk has gone on. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            LAST.clear();
            return;
        }
        boolean on = EMFCompatConfig.getBoolean(KEY_ENABLED, true);
        Map<UUID, Float> seen = new HashMap<>();
        for (Player player : level.players()) {
            if (player.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) player.walkAnimation;
            float position = walk.emfcompat$position();
            UUID uuid = player.getUUID();
            Float last = LAST.get(uuid);
            if (on && last != null) {
                float gone = position - last;
                float rate = rate(player);
                if (gone > 0 && rate != 1f) {
                    position = last + gone * rate;
                    walk.emfcompat$setPosition(position);
                }
                log(player, rate);
            }
            seen.put(uuid, position);
        }
        LAST.clear();
        LAST.putAll(seen);
    }

    /**
     * How fast the swing goes this tick against its own pace: what brings the foot down on the
     * middle of its step in the swing's time at the player's speed; 1 off steps.
     */
    private static float rate(Player player) {
        if (!player.onGround()) return 1f;
        float[] landing = FootGrounding.landing(player.getUUID());
        if (landing == null || Math.abs(landing[2] - landing[3]) < MIN_RISE) return 1f;
        Vec3 move = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        double v = move.length() * 20;
        if (v < 0.5) return 1f;
        Vec3 dir = move.normalize();
        double landY = landing[2];
        // The run of floor at the landing's height along the way, around where the foot lands.
        double back = Double.NaN, ahead = Double.NaN;
        for (double t = 0; t <= SEARCH; t += SAMPLE) {
            if (!onStep(player, landing, dir, t, landY)) break;
            ahead = t;
        }
        for (double t = 0; t >= -SEARCH; t -= SAMPLE) {
            if (!onStep(player, landing, dir, t, landY)) break;
            back = t;
        }
        if (Double.isNaN(ahead) || Double.isNaN(back)) return 1f;
        double middle = (ahead + back) / 2;
        if (Math.abs(middle) < ON_MIDDLE) return 1f;
        // The foot comes down after going {left * v} more; it should go {left * v + middle}.
        double planned = Math.max(0.05, landing[4] * v);
        float rate = (float) (planned / Math.max(0.05, planned + middle));
        return Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
    }

    private static boolean onStep(Player player, float[] landing, Vec3 dir, double t, double y) {
        Vec3 at = new Vec3(landing[0] + dir.x * t, y, landing[1] + dir.z * t);
        BlockHitResult hit = player.level().clip(new ClipContext(at.add(0, 0.3, 0), at.add(0, -0.3, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP
                && Math.abs(hit.getLocation().y - y) < 0.05;
    }

    private static void log(Player player, float rate) {
        String now = rate == 1f ? "free" : "matching";
        if (FootGroundingFeature.isTrace() ? rate != 1f : !now.equals(LOGGED.get(player.getUUID()))) {
            LOGGER.info("[StepAssist] {} {} stride x{}", player.getName().getString(), now, String.format("%.2f", rate));
        }
        LOGGED.put(player.getUUID(), now);
    }
}
