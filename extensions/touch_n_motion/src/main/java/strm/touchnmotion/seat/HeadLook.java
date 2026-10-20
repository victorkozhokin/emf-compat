package strm.touchnmotion.seat;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.InteractionRuntime;

import java.util.UUID;
import java.util.function.Function;

/**
 * The head of a body something has turned - to a craft's wheel, a train's controls, a cart, a horse,
 * a fence - still looking where the player looks, as far as a neck turns. One source of the look
 * for all of them: the camera's own way for the player at this screen, so with Shoulder Surfing the
 * head goes after the camera, not after the body the game turns; another player's look, between ticks.
 */
public final class HeadLook {

    private HeadLook() {
    }

    /** Where the player looks this frame, in the world. */
    public static Vec3 view(AbstractClientPlayer player) {
        return strm.touchnmotion.compat.ShoulderCamera.worldView(player);
    }

    /** {yaw, pitch} of where the player looks, against the body as {@code frame} has it drawn. */
    public static float[] against(IKFrame frame, AbstractClientPlayer player) {
        Vec3 origin = frame.jointWorld(new Vector3f());
        return CockpitFacing.look(Body.model(frame, origin.add(view(player))));
    }

    /**
     * Turns the head, and the hat with it, to {@code yaw} and {@code pitch} by {@code weight} - unless the
     * player sees through their own eyes, or the head is another's to aim (a look at a creature).
     */
    public static void turn(UUID uuid, Function<String, ModelPart> parts, float yaw, float pitch, float weight) {
        if (strm.emfcompat.core.EMFCompatCore.isLocalPlayerInFirstPerson(uuid) || InteractionRuntime.aim(uuid, Effector.HEAD) != null) return;
        SeatedBody.turnHead(parts.apply("head"), parts.apply("hat"), yaw, pitch, weight);
    }
}
