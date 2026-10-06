package strm.emfcompat.animationadditions.leash;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.torso.BraceSteps;
import strm.emfcompat.core.ik.IKFrame;

import java.util.function.Function;

/** The feet split along the pull; the stepping itself is {@link BraceSteps}. */
final class LeashStance {
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatLeash");

    static final class State extends BraceSteps.State {
    }

    static void apply(State s, AbstractClientPlayer player, IKFrame frame, Function<String, ModelPart> parts,
                      Vector3f direction, float effort) {
        BraceSteps.apply(s, player, frame, parts, LeashPose.foot(true, direction, effort),
                LeashPose.foot(false, direction, effort), effort, effort * (float) Math.toRadians(5), LOGGER, "LeashStance");
    }
}
