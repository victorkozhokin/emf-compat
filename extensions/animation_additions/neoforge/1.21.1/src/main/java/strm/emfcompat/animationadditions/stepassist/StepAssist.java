package strm.emfcompat.animationadditions.stepassist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;
import strm.emfcompat.animationadditions.motion.Spring;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Step assist: on stairs and slabs the local player's speed is tuned a little, so the foot in the
 * air comes down on the middle of the next step instead of on its edge or short of it.
 *
 * <p>The foot steps ({@link FootGrounding}) foresee where the swinging foot lands. Here the step
 * it lands on is measured along the way the player goes, and the speed changed by what makes up
 * the difference to its middle in the time the swing has left - at most {@link #MAX_CHANGE} either
 * way, and smoothly. Only the height of the landing step matters, so nothing else about moving
 * changes: going up and down stays vanilla.</p>
 *
 * <p>The player's own game moves the player, the server only checks it; so the tuning is done
 * here, as a transient modifier on the movement speed, and only on a server that has said it
 * allows it ({@link StepAssistNetwork}). Everyone else sees ordinary movement.</p>
 */
public final class StepAssist {

    public static final String KEY_ENABLED = "stepassist.enabled";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatStepAssist");
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("emf_compat_animation_additions", "step_assist");

    /** The most the speed changes, as a share. */
    private static final float MAX_CHANGE = 0.15f;
    /** A landing this much higher or lower than the take-off is a step, blocks. */
    private static final double MIN_RISE = 0.2;
    /** How far along the way the step is looked for from the landing, blocks, and how finely. */
    private static final double SEARCH = 0.7;
    private static final double SAMPLE = 1 / 16.0;
    /** The swing has at least this long left, seconds, when making up the difference. */
    private static final double MIN_LEFT = 0.08;
    private static final double HALFLIFE = 0.06;
    private static final double TICK = 0.05;

    private static final Spring FACTOR = new Spring();
    private static String logged = "";

    static {
        FACTOR.set(1f);
    }

    private StepAssist() {
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Step assist", true,
                "On", "On a server with this mod, the speed on stairs and slabs is tuned a little so each foot lands on the middle of a step.",
                "Off", "Vanilla speed everywhere.");
    }

    /** Every client tick, before the player moves. */
    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        float target = 1f;
        String why = "off";
        if (StepAssistNetwork.allowed() && EMFCompatConfig.getBoolean(KEY_ENABLED, true) && player.onGround()) {
            float[] landing = FootGrounding.landing(player.getUUID());
            if (landing == null) {
                why = "no-step";
            } else {
                target = factor(player, landing);
                why = target == 1f ? "on-step" : "tuning";
            }
        } else if (!StepAssistNetwork.allowed()) {
            why = "not-allowed";
        }
        FACTOR.update(target, HALFLIFE, TICK);
        float change = FACTOR.value - 1f;
        if (Math.abs(change) < 1e-3f) {
            speed.removeModifier(ID);
        } else {
            speed.addOrUpdateTransientModifier(new AttributeModifier(ID, change, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (FootGroundingFeature.isTrace() || !why.equals(logged)) {
            if (!why.equals(logged) || Math.abs(change) > 1e-3f) {
                LOGGER.info("[StepAssist] {} speed x{}", why, String.format("%.3f", FACTOR.value));
            }
            logged = why;
        }
    }

    /**
     * The speed factor bringing the landing {x, z, landing y, take-off y, seconds left} onto the
     * middle of its step; 1 when it is not on a step, or already on its middle.
     */
    private static float factor(LocalPlayer player, float[] landing) {
        double landY = landing[2];
        if (Math.abs(landY - landing[3]) < MIN_RISE) return 1f;
        Vec3 move = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        double v = move.length() * 20;
        if (v < 0.5) return 1f;
        Vec3 dir = move.normalize();
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
        double left = Math.max(MIN_LEFT, landing[4]);
        float f = (float) (1 + middle / left / v);
        return Math.max(1f - MAX_CHANGE, Math.min(1f + MAX_CHANGE, f));
    }

    private static boolean onStep(LocalPlayer player, float[] landing, Vec3 dir, double t, double y) {
        Vec3 at = new Vec3(landing[0] + dir.x * t, y, landing[1] + dir.z * t);
        BlockHitResult hit = player.level().clip(new ClipContext(at.add(0, 0.3, 0), at.add(0, -0.3, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP
                && Math.abs(hit.getLocation().y - y) < 0.05;
    }
}
