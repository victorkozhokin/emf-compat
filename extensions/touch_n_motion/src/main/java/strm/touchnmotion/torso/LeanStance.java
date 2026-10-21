package strm.touchnmotion.torso;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import org.slf4j.Logger;
import strm.emfcompat.core.ik.IKFrame;

import java.util.function.Function;

/**
 * The feet and the hips of a body leaning forward on its hands - on a fence's top, on a table: the
 * weight goes onto the hands, so one foot is set back and a little out, the other stays all but
 * under the body - one short step, held through the lean, and back under the body by a step when it
 * ends - and the hips go back as the chest goes over. Before the torso is leant.
 */
public final class LeanStance {
    /** Model pixels from where the pack has the soles (back is +z, the right is -x), for the right side leading. */
    static final Vector3f FRONT_FOOT = new Vector3f(-0.4f, 0f, -0.6f), BACK_FOOT = new Vector3f(0.9f, 0f, 3.2f);
    private static final Vector3f HOME = new Vector3f();
    /** Model pixels the hips go back, all of the lean on. */
    public static final float HIPS_BACK = 1.4f;

    private LeanStance() {
    }

    /** The steps of one lean: where each foot is, and the step under way. */
    public static final class State {
        final BraceSteps.State steps = new BraceSteps.State();

        /** Both feet back under the body and no step under way: nothing to draw. */
        public boolean resting() {
            return steps.resting();
        }
    }

    /**
     * @param leaning    whether the hands bear the weight now: the feet go apart, or home again
     * @param hips       how much of the lean is on, 0..1: the hips go back by this much of {@link #HIPS_BACK}
     * @param rightFront whether the right foot stays under the body and the left goes back; the other way round otherwise
     */
    public static void apply(State s, AbstractClientPlayer player, IKFrame frame, Function<String, ModelPart> parts,
                             boolean leaning, float hips, boolean rightFront, Logger trace, String label) {
        Vector3f right = HOME, left = HOME;
        if (leaning) {
            right = rightFront ? FRONT_FOOT : mirrored(BACK_FOOT);
            left = rightFront ? BACK_FOOT : mirrored(FRONT_FOOT);
        }
        // Home again by a step each, as they went out, not by a slide: the effort is kept up and the steps go to where the pack has the feet.
        BraceSteps.apply(s.steps, player, frame, parts, right, left, 1f, 0f, trace, label);
        PelvisFollow.shift(parts, 0f, HIPS_BACK * hips);
    }

    private static Vector3f mirrored(Vector3f foot) {
        return new Vector3f(-foot.x, foot.y, foot.z);
    }
}
