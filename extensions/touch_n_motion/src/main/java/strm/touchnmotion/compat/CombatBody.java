package strm.touchnmotion.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.torso.BraceSteps;
import strm.touchnmotion.torso.PelvisFollow;
import strm.touchnmotion.torso.TorsoLean;

import java.util.UUID;
import java.util.function.Function;

/**
 * An experiment for the combat addons, which hold the arms of an attack animation and leave the
 * torso and the feet to the pack. While an attack plays the addon says so each frame
 * ({@link #offer}, with what the animation itself has done to the torso part - little: the big
 * turn of a swing is the whole model's, on the pose stack, and shows already). The torso then
 * leans into the blow through the usual lean, the shoulders going with it while the arms keep
 * pointing where the attack has them, and a player standing still steps into a stance and puts
 * the weight over it.
 */
public final class CombatBody {
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatCombatBody");
    /** The attack's own bend of the torso part is shown whole; and this much forward lean, radians, goes with any attack. */
    private static final float SHARE = 1f, LEAN_IN = .09f;
    /** An offer older than this is over. */
    private static final long FRESH_NANOS = 150_000_000L;
    /** The stance, model pixels: the right foot back and out, the left forward. */
    private static final Vector3f RIGHT_FOOT = new Vector3f(-1f, 0, 1.1f), LEFT_FOOT = new Vector3f(.9f, 0, -.9f), HOME = new Vector3f();

    private static final class State {
        final float[] turn = new float[3];
        long at;
        boolean planted;
        final BraceSteps.State feet = new BraceSteps.State();
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private CombatBody() {
    }

    /**
     * Called by a combat addon while one of its attacks plays: the torso's rotation as the attack
     * animation has it, radians, and whether the player stands still. Returns {@code true}: the
     * body and the feet are taken care of here, the caller holds the arms only.
     */
    public static boolean offer(UUID uuid, float pitch, float yaw, float roll, boolean planted) {
        long now = System.nanoTime();
        State state = STATES.seen(uuid, now).value;
        state.turn[0] = pitch;
        state.turn[1] = yaw;
        state.turn[2] = roll;
        state.planted = planted;
        state.at = now;
        return true;
    }

    private static State active(UUID uuid) {
        State state = STATES.fresh(uuid);
        return state != null && System.nanoTime() - state.at < FRESH_NANOS ? state : null;
    }

    /** What the attack asks of the torso; the lean's own smoothing carries it in and out. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = active(uuid);
        return state == null ? null : TorsoLean.Hint.turn(state.turn[0] * SHARE + LEAN_IN, state.turn[1] * SHARE, state.turn[2] * SHARE);
    }

    /** The feet, before the torso: a step each into the stance while the attack lasts, a step each back after. */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        State now = active(uuid);
        boolean stance = now != null && now.planted;
        if (!stance && state.feet.resting()) return;
        Player player = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getPlayerByUUID(uuid);
        if (!(player instanceof AbstractClientPlayer client)) return;
        BraceSteps.apply(state.feet, client, InteractionRuntime.frame(uuid), parts, stance ? RIGHT_FOOT : HOME, stance ? LEFT_FOOT : HOME,
                stance ? 1 : 0, 0, LOGGER, "CombatStance");
        // The weight goes with the blow: over the foot the torso turns to, and forward into a slam.
        if (stance) PelvisFollow.shift(parts, clamp(-state.turn[1] * 1.1f, 1.2f), clamp(-(state.turn[0] + LEAN_IN) * 1.6f, 1.4f));
    }

    /** The arms' rotation before the torso turns, to be put back after it; {@code null} when no attack plays. */
    public static float[] armsBefore(UUID uuid, Function<String, ModelPart> parts) {
        if (active(uuid) == null) return null;
        ModelPart right = parts.apply("right_arm"), left = parts.apply("left_arm");
        if (right == null || left == null) return null;
        return new float[]{right.xRot, right.yRot, right.zRot, left.xRot, left.yRot, left.zRot};
    }

    /** The shoulders have gone with the torso; the arms point where the attack has them, not that turn further. */
    public static void armsAfter(Function<String, ModelPart> parts, float[] before) {
        if (before == null) return;
        ModelPart right = parts.apply("right_arm"), left = parts.apply("left_arm");
        if (right == null || left == null) return;
        right.setRotation(before[0], before[1], before[2]);
        left.setRotation(before[3], before[4], before[5]);
    }

    private static float clamp(float v, float limit) {
        return Math.max(-limit, Math.min(limit, v));
    }
}
