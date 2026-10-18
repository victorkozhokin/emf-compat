package strm.touchnmotion.blockuse.create;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;
import strm.touchnmotion.DebugLog;
import strm.touchnmotion.blockuse.BlockUse;
import strm.touchnmotion.blockuse.aeronautics.CockpitFacing;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.interaction.Seated;
import strm.touchnmotion.interaction.Smoothing;
import strm.touchnmotion.platform.Platform;
import strm.touchnmotion.torso.TorsoLean;
import strm.touchnmotion.transport.TransportMotion;

import java.util.UUID;
import java.util.function.Function;

/**
 * A train's driver on a seat of it: the body turned to the controls whichever way the camera looks,
 * upright to the train, and swaying against it speeding up and braking; the head looks where the
 * driver does, as far as a neck goes. The hands are {@link BlockUse}'s, on the levers
 * ({@link TrainControls}). A seat of Create's has no front of its own, so the controls are the
 * front. Standing at the controls the body is left as it is.
 */
public final class TrainCockpit {
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    /** Seconds the body takes to come round to the controls, and back when driving ends. */
    private static final double TURN_SECONDS = .15;
    /** The sway against the train's acceleration, as at a craft's wheel: radians per block/s² and at most. */
    private static final float SWAY = .025f, MAX_SWAY = .14f;

    private static final class State {
        /** Towards the controls and the train's upright, in the world: kept to turn back from when driving ends. */
        Vec3 face, up;
        float weight;
        float headYaw, headPitch;
        TransportMotion transport = new TransportMotion();
        final Vector3f sway = new Vector3f();
        boolean driving;
        long at;
    }

    private TrainCockpit() {
    }

    /** Turns this draw of the model to the controls, before the pack animates it. */
    public static void orient(AbstractClientPlayer player, PoseStack stack) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        State state = STATES.seen(uuid, now).value;
        double dt = state.at == 0 ? 0 : Math.min(.1, (now - state.at) / 1e9);
        state.at = now;
        if (!Seated.seated(player) || !BlockUse.INSTANCE.isEnabled() || !EMFCompatCore.isCompatEnabled()
                || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            // Off the seat at once: a standing body is not turned back round.
            drive(player, state, false);
            state.weight = 0;
            return;
        }
        float partial = Platform.partialTick(false);
        Vec3[] controls = TrainControls.middleAndUp(player);
        drive(player, state, controls != null);
        if (controls != null) {
            state.face = controls[0].subtract(player.getPosition(partial));
            state.up = controls[1];
            state.transport.sample(player.tickCount, new Vector3d(player.getX(), player.getY(), player.getZ()));
        }
        state.weight += ((controls != null ? 1 : 0) - state.weight) * Smoothing.follow(dt, TURN_SECONDS);
        if (state.weight < 1e-3f || state.face == null) {
            state.weight = 0;
            return;
        }
        IKFrame frame = IKFrame.capture(stack.last().pose(), Platform.cameraPosition());
        Vec3 origin = frame.jointWorld(new Vector3f());
        Vector3f toward = Body.model(frame, origin.add(state.face));
        Vector3f normal = Body.model(frame, origin.add(state.up));
        Platform.rotate(stack, new Quaternionf().slerp(CockpitFacing.orientation(toward, normal), state.weight));

        // Against the body as it is now turned: where the driver looks, and how the train pulls.
        IKFrame turned = IKFrame.capture(stack.last().pose(), Platform.cameraPosition());
        Vec3 at = turned.jointWorld(new Vector3f());
        float[] look = CockpitFacing.look(Body.model(turned, at.add(strm.touchnmotion.compat.ShoulderCamera.worldView(player))));
        state.headYaw = look[0];
        state.headPitch = look[1];
        var a = state.transport.acceleration;
        Vector3f force = Body.model(turned, at.add(a.x, a.y, a.z)).div(16);
        // Speeding up forwards (-z) the body sways back (-xRot); braking, forwards; round a bend, out of it.
        Vector3f sway = state.transport.warped || controls == null ? new Vector3f()
                : new Vector3f(clamp(force.z * SWAY), 0, clamp(-force.x * SWAY));
        state.sway.lerp(sway, Smoothing.follow(dt, .2));
    }

    private static void drive(AbstractClientPlayer player, State state, boolean driving) {
        if (driving == state.driving) return;
        state.driving = driving;
        if (driving) state.transport = new TransportMotion();
        if (DebugLog.decisions())
            org.slf4j.LoggerFactory.getLogger("EMFCompatTrainCockpit").info("[TrainCockpit] {} {}", player.getName().getString(), driving ? "train-cockpit" : "off");
    }

    private static float clamp(float v) {
        return Math.max(-MAX_SWAY, Math.min(MAX_SWAY, v));
    }

    /** The sway this asks of the torso; {@code null} when none. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.weight < 1e-3f) return null;
        return TorsoLean.Hint.turn(state.sway.x * state.weight, 0, state.sway.z * state.weight);
    }

    /** The head looks where the driver does, from a body turned to the controls. After the pack has animated. */
    public static void head(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.weight < 1e-3f || InteractionRuntime.aim(uuid, Effector.HEAD) != null) return;
        ModelPart head = parts.apply("head"), hat = parts.apply("hat");
        if (head == null) return;
        head.yRot += IKMath.wrap(state.headYaw - head.yRot) * state.weight;
        head.xRot += IKMath.wrap(state.headPitch - head.xRot) * state.weight;
        if (hat != null) {
            hat.yRot = head.yRot;
            hat.xRot = head.xRot;
        }
    }
}
