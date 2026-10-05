package strm.emfcompat.animationadditions.ejector;

import strm.emfcompat.animationadditions.torso.TorsoLean;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.EntityModel;
import strm.emfcompat.animationadditions.DebugLog;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.blockuse.ModAccess;
import strm.emfcompat.animationadditions.buttonpress.ReachPose;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.motion.MotionRuntime;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;

import java.util.UUID;
import java.util.function.Function;

/**
 * Create's weighted ejector under a player. Standing on its lid - it is winding up, or the player
 * sneaks, which keeps it from firing - the body braces: it is drawn crouching, sneaking or not,
 * the arms back, the feet a little apart. Fired,
 * it throws the player: going up the arms are flung overhead and the legs trail, coming down the
 * arms go out to the sides and the legs apart, until the player lands.
 *
 * <p>Optional, by name. Thrown is told by the ejector itself: its state is {@code LAUNCHING} just
 * as the player who was on it leaves upwards - a jump off a lid that stays shut is not a throw.
 * Every client has both, so this is any player's. The legs have no knees: the brace is the torso's
 * and the arms' only.</p>
 */
public final class EjectorLaunch {

    public static final String KEY_ENABLED = "ejector.enabled";

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatEjector");
    private static final String BLOCK = "com.simibubi.create.content.logistics.depot.EjectorBlock";
    private static final ModAccess STATE = new ModAccess("getState");
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    /** After leaving the lid, how long a throw can still be told, and the least speed up, blocks a second. */
    private static final double LEFT_SECONDS = 0.5;
    private static final float THROWN_UP = 2f;
    /** A throw is not over by touching the ground it started from; and never lasts longer than this. */
    private static final double TAKE_OFF_SECONDS = 0.25, LONGEST_SECONDS = 12;
    /** Seconds: the brace, the thrown pose coming and going, going up turning into coming down. */
    private static final double BRACE_SECONDS = 0.15, FLIGHT_IN = 0.08, FLIGHT_OUT = 0.2, RISE_SECONDS = 0.25;
    /** The speed up or down, blocks a second, at which the pose is all going up or all coming down. */
    private static final float FULL_RISE = 6f;

    /** How far the drawn crouch is straightened, of {@code ReachPose.upright}'s all. */
    private static final float DRAWN_UPRIGHT = 0.6f;
    private static final float BRACE_ARM_BACK = rad(35), BRACE_ARM_OUT = rad(10), BRACE_LEG_OUT = rad(9);
    private static final float UP_TORSO = rad(-6), UP_ARM = rad(-165), UP_ARM_OUT = rad(12), UP_LEG = rad(12);
    private static final float DOWN_TORSO = rad(10), DOWN_ARM = rad(-30), DOWN_ARM_OUT = rad(75);
    private static final float DOWN_LEG = rad(15), DOWN_LEG_OUT = rad(8);

    private EjectorLaunch() {
    }

    private static final class State {
        /** The ejector last stood on, and when. */
        BlockPos lid;
        long lidAt;
        boolean thrown, braced;
        long thrownAt;
        /** As shown, smoothed: the brace, the thrown pose, going up (1) to coming down (-1). */
        float brace, flight, rise;
        /** How much of the brace is the crouch drawn for a player who does not sneak, smoothed. */
        float drawn;
        String logged = "off";
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Weighted ejector", true,
                "On", "On a Create weighted ejector the body braces, and thrown by it flies arms up, then out.",
                "Off", "Leave it to the pack's jump and fall.");
    }

    /** Called right before the model is animated, after {@link MotionRuntime} and before the torso. */
    public static void modelPose(AbstractClientPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        double dt = EntityStates.due(entry, now);
        if (dt < 0) return;
        State s = entry.value;
        boolean on = EMFCompatConfig.getBoolean(KEY_ENABLED, true) && EMFCompatCore.isCompatEnabled()
                && !EMFCompatCore.isLocalPlayerInFirstPerson(uuid);
        boolean free = !player.isPassenger() && !player.isFallFlying() && !player.isInWaterOrBubble() && !player.isSleeping();

        BlockPos lid = on && free ? lid(player) : null;
        if (lid != null) {
            s.lid = lid;
            s.lidAt = now;
        }
        MotionRuntime.Motion motion = MotionRuntime.get(uuid);
        if (!s.thrown && on && free && s.lid != null && (now - s.lidAt) / 1e9 < LEFT_SECONDS
                && !player.onGround() && motion.vertical() > THROWN_UP && launching(player, s.lid)) {
            s.thrown = true;
            s.thrownAt = now;
        }
        if (s.thrown) {
            double since = (now - s.thrownAt) / 1e9;
            if (!on || !free || since > LONGEST_SECONDS || (player.onGround() && since > TAKE_OFF_SECONDS)) s.thrown = false;
        }
        boolean braced = lid != null && player.onGround() && !s.thrown;
        s.braced = braced;

        s.brace += ((braced ? 1f : 0f) - s.brace) * Smoothing.follow(dt, BRACE_SECONDS);
        s.drawn += ((braced && !player.isCrouching() ? 1f : 0f) - s.drawn) * Smoothing.follow(dt, BRACE_SECONDS);
        s.flight += ((s.thrown ? 1f : 0f) - s.flight) * Smoothing.follow(dt, s.thrown ? FLIGHT_IN : FLIGHT_OUT);
        float rise = s.thrown ? Mth.clamp(motion.vertical() / FULL_RISE, -1f, 1f) : s.rise;
        // Thrown this frame: up at once, not from wherever the last throw ended.
        s.rise = s.flight < 0.05f && s.thrown ? 1f : s.rise + (rise - s.rise) * Smoothing.follow(dt, RISE_SECONDS);

        String phase = s.thrown ? "thrown" : braced ? "brace" : "off";
        if (!phase.equals(s.logged)) {
            s.logged = phase;
            if (DebugLog.decisions()) LOGGER.info("[Ejector] {} {}", player.getName().getString(), phase);
        }
    }

    /** The vanilla crouch drops the render by an eighth of a block, and packs count on it: model units (y down, 16 px). */
    private static final float CROUCH_DROP = 0.125f / 0.9375f;

    /**
     * Braced on the lid the player is drawn crouching, sneaking or not: the model is told it
     * crouches, so the pack plays its own crouch, and the render is dropped as a crouch drops it.
     * Called before the model is animated and before anything measures from the pose stack; goes by
     * the last frame's brace.
     */
    public static void crouch(AbstractClientPlayer player, EntityModel<?> model,
                              PoseStack stack) {
        State s = STATES.fresh(player.getUUID());
        if (s == null || !s.braced || player.isCrouching()) return;
        if (!(model instanceof HumanoidModel<?> humanoid) || humanoid.crouching) return;
        humanoid.crouching = true;
        stack.translate(0f, CROUCH_DROP, 0f);
    }

    /** The ejector the player stands in or on; {@code null} for none. Its lid is inside its block. */
    private static BlockPos lid(AbstractClientPlayer player) {
        BlockPos at = player.blockPosition();
        if (is(player.level().getBlockState(at))) return at;
        return is(player.level().getBlockState(at.below())) && player.getY() - at.getY() < 0.1 ? at.below() : null;
    }

    private static boolean is(BlockState block) {
        return ModAccess.is(block.getBlock().getClass(), BLOCK);
    }

    private static boolean launching(AbstractClientPlayer player, BlockPos lid) {
        Object state = STATE.read(player.level().getBlockEntity(lid));
        return state != null && "LAUNCHING".equals(state.toString());
    }

    /** The torso's lean for {@code TorsoLean}; {@code null} for none. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State s = STATES.fresh(uuid);
        if (s == null || s.brace < 1e-3f && s.flight < 1e-3f) return null;
        float up = s.rise * 0.5f + 0.5f;
        // Braced, the torso's lean is the crouch's own.
        return TorsoLean.Hint.turn(s.flight * Mth.lerp(up, DOWN_TORSO, UP_TORSO), 0f, 0f);
    }

    /** The arms and the legs. Called after the pack has animated, before the torso and the hands' aims. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.brace < 1e-3f && s.flight < 1e-3f) return;
        // The crouch drawn for one who does not sneak is a higher one: sneaking then still shows, the body going down.
        ReachPose.upright(parts, s.drawn * DRAWN_UPRIGHT);
        float up = s.rise * 0.5f + 0.5f;
        for (int i = 0; i < 2; i++) {
            // Out, for a hanging right limb, is +zRot; for a left one -zRot. Back is +xRot.
            float side = i == 0 ? 1f : -1f;
            ModelPart arm = parts.apply(i == 0 ? "right_arm" : "left_arm");
            if (arm != null) {
                blend(arm, BRACE_ARM_BACK, side * BRACE_ARM_OUT, s.brace);
                blend(arm, Mth.lerp(up, DOWN_ARM, UP_ARM), side * Mth.lerp(up, DOWN_ARM_OUT, UP_ARM_OUT), s.flight);
            }
            ModelPart leg = parts.apply(i == 0 ? "right_leg" : "left_leg");
            // Coming down the legs are apart, one ahead and one behind; going up both trail.
            // Braced, the feet are set a little apart, on top of whatever the pack does with the legs.
            if (leg != null) leg.zRot += side * BRACE_LEG_OUT * s.brace;
            if (leg != null) blend(leg, Mth.lerp(up, side * DOWN_LEG, UP_LEG), side * Mth.lerp(up, DOWN_LEG_OUT, 0f), s.flight);
        }
    }

    /** Takes the limb from the pack's pose to this one by {@code weight}. */
    private static void blend(ModelPart part, float xRot, float zRot, float weight) {
        if (weight < 1e-3f) return;
        part.xRot = Mth.lerp(weight, part.xRot, xRot);
        part.yRot = Mth.lerp(weight, part.yRot, 0f);
        part.zRot = Mth.lerp(weight, part.zRot, zRot);
    }

    private static float rad(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
