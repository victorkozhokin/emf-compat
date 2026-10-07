package strm.emfcompat.animationadditions.mining;

import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Ease;

/**
 * What the rest of the body does while {@link Mining} swings the arm: the feet set apart for the
 * tool, the torso held turned and bent to the point, and a short pulse with every swing. No game
 * classes, so it is unit-tested. Model space: +xRot bends forward, +yRot turns to the right, the
 * right side is -x, forward is -z, down is +y.
 */
final class MiningBody {

    private MiningBody() {
    }

    /**
     * How a tool is stood to, for the right hand: the tool-side foot back and the other forward,
     * pixels; the working shoulder held back, degrees; and what each swing adds from wound up to
     * struck - the torso bending into it and the shoulder coming round.
     */
    record Stance(float back, float forward, float apart, float turned, float pulsePitch, float pulseTurn) {
    }

    /** Overhead blows: a deep split, the body driving down into each. */
    private static final Stance PICKAXE = new Stance(1.4f, 1.6f, .5f, 6f, 9f, 2f);
    /** A chop from the side: side-on, the swing coming round with the shoulder. */
    private static final Stance AXE = new Stance(1.8f, 1.0f, .8f, 12f, 3f, 6f);
    /** A thrust: the front foot well up to the work, the weight going onto it. */
    private static final Stance SHOVEL = new Stance(.8f, 2.0f, .3f, 4f, 8f, 1.5f);
    /** A light chop: hardly a stance at all. */
    private static final Stance HOE = new Stance(.6f, .8f, .3f, 5f, 5f, 1.5f);

    static Stance stance(ToolSwing.Tool tool) {
        return tool == ToolSwing.AXE ? AXE : tool == ToolSwing.SHOVEL ? SHOVEL : tool == ToolSwing.HOE ? HOE : PICKAXE;
    }

    /** Where the soles stand from where the pack has them, {right foot, left foot}. */
    static Vector3f[] feet(ToolSwing.Tool tool, boolean right) {
        Stance s = stance(tool);
        Vector3f toolSide = new Vector3f(-s.apart, 0, s.back), other = new Vector3f(s.apart * .5f, 0, -s.forward);
        if (right) return new Vector3f[]{toolSide, other};
        return new Vector3f[]{new Vector3f(-other.x, 0, other.z), new Vector3f(-toolSide.x, 0, toolSide.z)};
    }

    /** The point's height where the torso stays upright, and where it is bent to the full, pixels (the shoulder is at 2, the soles at 24). */
    private static final float LEVEL = 4f, LOW = 22f, HIGH = -12f;
    private static final float LOW_PITCH = (float) Math.toRadians(13), HIGH_PITCH = (float) Math.toRadians(-7);

    /**
     * {pitch, yaw, roll} asked of the torso. {@code wound} is how far the arm is wound up, 0..1:
     * wound up the body is back and upright, struck it has come round and down. The torso follows
     * a hint late and softened (a swing is 0.3 s): measured in game, about a third of the pulse
     * asked for is seen, so it is asked for larger, and by the caller a little ahead.
     */
    static float[] lean(ToolSwing.Tool tool, boolean right, float pointY, float wound) {
        Stance s = stance(tool);
        float side = right ? 1f : -1f;
        float height = pointY > LEVEL ? Ease.smooth((pointY - LEVEL) / (LOW - LEVEL)) * LOW_PITCH
                : Ease.smooth((LEVEL - pointY) / (LEVEL - HIGH)) * HIGH_PITCH;
        float struck = 1 - 2 * Ease.unit(wound);
        return new float[]{height + (float) Math.toRadians(s.pulsePitch) * struck * .5f,
                side * (float) Math.toRadians(s.turned - s.pulseTurn * struck * .5f),
                side * (float) Math.toRadians(1.5f) * struck};
    }

    /**
     * How much of the reach with the pelvis a point asks for, 0..1: nothing while the tool gets to
     * it from where the shoulder is, all of it a quarter of the tool's reach further.
     */
    static float shortBy(float distance, float reach) {
        return Ease.smooth((distance / reach - 1f) / .25f);
    }

    /** How far down by the legs a point is, 0..1: nothing above the waist (12), all of it at the knees. */
    static float low(float pointY) {
        return Ease.smooth((pointY - 12f) / 6f);
    }
}
