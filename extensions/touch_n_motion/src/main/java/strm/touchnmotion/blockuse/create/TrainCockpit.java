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
import strm.touchnmotion.seat.CockpitContact;
import strm.touchnmotion.seat.CockpitFacing;
import strm.touchnmotion.seat.HeadLook;
import strm.touchnmotion.seat.SeatedBody;
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

    private static final class State {
        /** Towards the controls and the train's upright, in the world: kept to turn back from when driving ends. */
        Vec3 face, up;
        float weight;
        float headYaw, headPitch;
        TransportMotion transport = new TransportMotion();
        final Vector3f sway = new Vector3f();
        boolean driving;
        long at;
        /** The upper body's turn about the waist that puts each shoulder an arm's length from its lever. */
        final Quaternionf contact = new Quaternionf();
        final strm.touchnmotion.interaction.FrameClock contactClock = new strm.touchnmotion.interaction.FrameClock();
    }

    private TrainCockpit() {
    }

    /**
     * The end of a client tick: a seated driver's body is turned to the controls, as a boat turns its
     * rider's. The game keeps a body within 50 degrees of the look, and a train turning under its driver
     * would leave it lagging there - the pack then turns the torso after the head, and the cape swings
     * as from the lagging body. The cape is carried along, as in a cab. Every player in sight, not only ours.
     */
    public static void tick() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || !BlockUse.INSTANCE.isEnabled() || !EMFCompatCore.isCompatEnabled()) return;
        for (var player : level.players()) {
            if (!Seated.seated(player)) continue;
            Vec3[] controls = TrainControls.middleAndUp(player);
            if (controls == null) continue;
            Vec3 toward = controls[0].subtract(player.position());
            if (toward.horizontalDistanceSqr() < 1e-4) continue;
            float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
            player.yBodyRot += net.minecraft.util.Mth.wrapDegrees(yaw - player.yBodyRot);
            // The cape trails behind where the player was; in a cab the train's speed is no wind, so it is
            // carried with the player and hangs.
            //? if <1.21.11 {
            player.xCloakO = player.xo;
            player.yCloakO = player.yo;
            player.zCloakO = player.zo;
            player.xCloak = player.getX();
            player.yCloak = player.getY();
            player.zCloak = player.getZ();
            //?}
        }
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
        float[] look = HeadLook.against(turned, player);
        state.headYaw = look[0];
        state.headPitch = look[1];
        Vector3f sway = state.transport.warped || controls == null ? new Vector3f() : SeatedBody.sway(turned, state.transport.acceleration);
        state.sway.lerp(sway, Smoothing.follow(dt, .2));
    }

    private static void drive(AbstractClientPlayer player, State state, boolean driving) {
        if (driving == state.driving) return;
        state.driving = driving;
        if (driving) {
            state.transport = new TransportMotion();
            state.contact.identity();
            state.contactClock.restart();
        }
        if (DebugLog.decisions())
            org.slf4j.LoggerFactory.getLogger("EMFCompatTrainCockpit").info("[TrainCockpit] {} {}", player.getName().getString(), driving ? "train-cockpit" : "off");
    }

    /** The sway this asks of the torso; {@code null} when none. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.weight < 1e-3f) return null;
        return TorsoLean.Hint.turn(state.sway.x * state.weight, 0, state.sway.z * state.weight);
    }

    /**
     * Each hand onto its own lever, as at a craft's wheel: the upper body turned about the waist - leant
     * in, twisted towards the far lever - just so that each shoulder is an arm's length from its lever, and
     * eased into it. Before the arms are aimed, which then aim from where the shoulders have gone.
     */
    public static void reach(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.weight < 1e-3f) return;
        Vector3f[] grips = BlockUse.trainGrips(uuid);
        ModelPart right = parts.apply("right_arm"), left = parts.apply("left_arm"), rightLeg = parts.apply("right_leg"), leftLeg = parts.apply("left_leg");
        if (grips == null || right == null || left == null || rightLeg == null || leftLeg == null) return;
        float weight = state.weight * Math.min(InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, BlockUse.INSTANCE.id()),
                InteractionRuntime.weight(uuid, Effector.LEFT_ARM, BlockUse.INSTANCE.id()));
        if (weight < 1e-3f) return;
        Vector3f waist = new Vector3f(rightLeg.x + leftLeg.x, rightLeg.y + leftLeg.y, rightLeg.z + leftLeg.z).mul(.5f);
        Vector3f rightShoulder = new Vector3f(right.x, right.y, right.z).sub(waist), leftShoulder = new Vector3f(left.x, left.y, left.z).sub(waist);
        Vector3f rightGrip = new Vector3f(grips[0]).sub(waist), leftGrip = new Vector3f(grips[1]).sub(waist);
        double dt = state.contactClock.tick();
        if (dt >= 0) {
            Quaternionf wanted = CockpitContact.fit(state.contact, rightShoulder, leftShoulder, rightGrip, leftGrip, true);
            state.contact.slerp(wanted, Smoothing.follow(dt, .06));
        }
        SeatedBody.turnUpperBody(parts, new Quaternionf().slerp(state.contact, weight), waist);
    }

    /** The head looks where the driver does, from a body turned to the controls. After the pack has animated. */
    public static void head(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.weight < 1e-3f) return;
        HeadLook.turn(uuid, parts, state.headYaw, state.headPitch, state.weight);
    }
}
