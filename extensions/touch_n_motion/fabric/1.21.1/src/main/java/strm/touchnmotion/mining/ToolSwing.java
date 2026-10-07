package strm.touchnmotion.mining;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.Ease;

/** The tools and the swing's own arithmetic for {@link Mining}: no game classes, so it is unit-tested. */
final class ToolSwing {

    private ToolSwing() {
    }

    /**
     * A swing starts with the tool on the point, where the last one left it: the arm winds up to
     * {@link #TOP}, comes down and is on the point again at {@link #IMPACT}, and rests there. These
     * are shares of the swing's time ({@link #time}), not of the game's swing value.
     */
    static final float TOP = 0.4f, IMPACT = 0.8f;

    /**
     * How far through its time a swing is, 0..1, from the game's swing value. Breaking a block the
     * game starts the next swing when this one's value is at a half - three ticks in - and runs
     * the value through its whole second half in the one tick left. Read as it stands, whatever
     * is drawn after the half would go by in a frame.
     */
    static float time(float phase) {
        phase = Ease.unit(phase);
        return phase < .5f ? phase * 1.5f : .75f + (phase - .5f) * .5f;
    }
    /**
     * A tool: where it is on the arm, pixels, in the arm's own space (the arm along +y from the
     * shoulder, -z forward) - the handle in the hand and the working part, from the handheld item
     * transform and the tool's sprite - and how it is swung (documentation.md §15.6). The swing goes
     * from the wound-up pose to the strike and back.
     */
    record Tool(String name, float gripY, float gripZ, float tipY, float tipZ) {
    }

    static final Tool PICKAXE = new Tool("pickaxe", 8.7f, -1.3f, 11.3f, -9.3f);
    static final Tool AXE = new Tool("axe", 8.7f, -1.3f, 12f, -9.8f);
    static final Tool SHOVEL = new Tool("shovel", 8.7f, -1.3f, 10.5f, -11.9f);
    static final Tool HOE = new Tool("hoe", 8.7f, -1.3f, 11.3f, -9.3f);

    /**
     * A pickaxe comes down from the upper right, each blow from another angle: how far the raised
     * arm leans out to the right of straight up, degrees, blow after blow.
     */
    private static final float[] PICKAXE_LEAN = {45f, 60f, 30f};
    /** How high the pickaxe arm is raised: forward and up, short of overhead. */
    private static final float PICKAXE_RAISED = -135f;
    /**
     * An axe is swung from behind the right shoulder down and across to the left, edge first,
     * each blow its own way: how far the raised arm leans out to the right, how far it is raised
     * (-90 is level ahead, -180 straight up, past that the hand is behind the shoulder), and how
     * the axe is turned in the hand about the arm's own length. Degrees.
     *
     * <p>The turn decides where the hand is at the blow: the head lies off the arm by its own
     * length, to the side as far as it is turned and above the hand for the rest. Turned by a
     * quarter, for a flat blow, the arm has to cross to the left of the point and the hand
     * comes up in front of the face; turned by a third of that the hand is under the point, at
     * the chest, and the blow comes down more than across.</p>
     *
     * <p>Which way to turn it: a held tool's edge leads when the arm turns so that the tool trails
     * the hand - as it does coming down in the game's own chop. Turned this way the head lies to
     * the right of the arm and follows it across; the other way it would be the back of the axe
     * that met the block.</p>
     */
    private static final float[][] AXE_BLOWS = {{20f, -190f, 28f}, {35f, -175f, 33f}, {12f, -200f, 20f}};

    /**
     * A shovel is thrust: from where it will be on the point the arm is drawn straight back, the
     * blade still towards the point, and driven in again - a full draw, a longer one from a
     * little out to the side, a short jab. Wherever the point is, by the feet or at the chest, it
     * is the same draw. {how far back, how far round to the right}, degrees.
     */
    private static final float[][] SHOVEL_BLOWS = {{52f, 0f}, {66f, 8f}, {40f, -4f}};
    /**
     * The shovel's arm is swung out to the right of the body by this, degrees, about the line from
     * the shoulder to the point: the blade stays on the point, the hand comes out from over the hip.
     */
    private static final float SHOVEL_OUT = 38f;
    /**
     * A hoe is a short chop: the arm up in front, well short of a pickaxe's, and leant out to the
     * right a little or a little more - enough that the hand comes up beside the face, not before it. {lean, raised} as the pickaxe's, and how far round to the right, degrees.
     */
    private static final float[][] HOE_BLOWS = {{16f, -118f, 10f}, {30f, -126f, 6f}, {9f, -110f, 16f}};

    /** How a tool is turned in the hand about the arm's own length for this blow, degrees, for the right arm. */
    static float turned(Tool tool, int blow) {
        return tool == AXE ? AXE_BLOWS[Math.floorMod(blow, AXE_BLOWS.length)][2] : 0f;
    }

    /**
     * The wound-up pose of the right arm, mirrored for the left: each tool its own way, and the
     * all of them another way with every {@code blow}. {@code strike} is the arm on the point,
     * which a shovel is drawn back from.
     */
    static Quaternionf wound(Tool tool, boolean right, int blow, Quaternionf strike) {
        Quaternionf q;
        if (tool == PICKAXE) {
            // Raised, then the whole arm leant out about the line ahead: a raised right arm goes out with -z.
            float lean = PICKAXE_LEAN[Math.floorMod(blow, PICKAXE_LEAN.length)];
            q = new Quaternionf().rotationZYX(rad(-lean), 0f, rad(PICKAXE_RAISED));
        } else if (tool == AXE) {
            float[] axe = AXE_BLOWS[Math.floorMod(blow, AXE_BLOWS.length)];
            q = new Quaternionf().rotateZ(rad(-axe[0])).rotateX(rad(axe[1])).rotateY(rad(axe[2]));
        } else if (tool == SHOVEL) {
            float[] shovel = SHOVEL_BLOWS[Math.floorMod(blow, SHOVEL_BLOWS.length)];
            // Back about the arm's own side-to-side line, from the strike; then the whole of it round.
            q = new Quaternionf().rotateY(rad(shovel[1])).mul(right ? strike : mirror(strike)).rotateX(rad(shovel[0]));
        } else {
            float[] hoe = HOE_BLOWS[Math.floorMod(blow, HOE_BLOWS.length)];
            q = new Quaternionf().rotationZYX(rad(-hoe[0]), rad(hoe[2]), rad(hoe[1]));
        }
        return right ? q : mirror(q);
    }

    /**
     * The arm's turn putting the tool on a point {@code to} pixels from the shoulder - the part of
     * it as far from the shoulder as the point is, the tip when the point is further - with the
     * tool turned in the hand as {@link #turned} says for this blow.
     */
    static Quaternionf strike(Vector3f to, Tool tool, boolean right, int blow) {
        float turn = rad(turned(tool, blow));
        float tx = right ? to.x : -to.x, distance = to.length();
        if (distance < 1e-3f) return new Quaternionf();
        float gripReach = (float) Math.hypot(tool.gripY, tool.gripZ);
        float tipReach = (float) Math.hypot(tool.tipY, tool.tipZ);
        float s = tipReach - gripReach < 1e-3f ? 1f
                : Ease.unit((distance - gripReach) / (tipReach - gripReach));
        float y = tool.gripY + s * (tool.tipY - tool.gripY), z = tool.gripZ + s * (tool.tipZ - tool.gripZ);
        // That part of the tool, turned in the hand, in the arm's space.
        float lx = z * (float) Math.sin(turn), lz = z * (float) Math.cos(turn);
        float length = (float) Math.sqrt(lx * lx + y * y + lz * lz), scale = length / distance;
        float ty = to.y * scale, r = (float) Math.hypot(y, lz);
        // About x first, to bring it to the point's height, raising forward; then about y, round to the point.
        float pitch = -(float) Math.acos(Math.max(-1f, Math.min(1f, ty / r))) - (float) Math.atan2(lz, y);
        float vz = y * (float) Math.sin(pitch) + lz * (float) Math.cos(pitch);
        float yaw = (float) (Math.atan2(tx, to.z) - Math.atan2(lx, vz));
        Quaternionf q = new Quaternionf().rotateY(yaw).rotateX(pitch).rotateY(turn);
        if (tool == SHOVEL) {
            // Whichever way round the line takes the hand to the right (-x), and less of it where
            // neither way does much, so the pose never flips between two points close together.
            Vector3f line = new Vector3f(tx, to.y, to.z).normalize();
            float gain = new Vector3f(line).cross(q.transform(new Vector3f(0f, 10f, 0f))).x;
            float out = -rad(SHOVEL_OUT) * Math.max(-1f, Math.min(1f, gain / 3f));
            q = new Quaternionf().rotateAxis(out, line).mul(q);
        }
        return right ? q : mirror(q);
    }

    /** The same pose for the other arm: yRot and zRot change sign, xRot does not. */
    private static Quaternionf mirror(Quaternionf q) {
        return new Quaternionf(q.x, -q.y, -q.z, q.w);
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    /**
     * The arm's {xRot, yRot} putting the item on a point {@code to} pixels from the shoulder, and how
     * far the point is as a share of the item's tip. The part of the item as far from the shoulder
     * as the point is aimed at it - the tip, when the point is further.
     */
    static float[] solve(Vector3f to, Tool tool) {
        float distance = to.length();
        if (distance < 1e-3f) return new float[]{0f, 0f, 0f};
        float gripReach = (float) Math.hypot(tool.gripY, tool.gripZ);
        float tipReach = (float) Math.hypot(tool.tipY, tool.tipZ);
        float s = tipReach - gripReach < 1e-3f ? 1f
                : Ease.unit((distance - gripReach) / (tipReach - gripReach));
        float y = tool.gripY + s * (tool.tipY - tool.gripY);
        float z = tool.gripZ + s * (tool.tipZ - tool.gripZ);
        // The arm's rotation about x turns that part round by the same angle from where it hangs.
        float offset = (float) Math.atan2(z, y);
        float dy = Math.max(-1f, Math.min(1f, to.y / distance));
        float x = -(float) Math.acos(dy) - offset;
        float yaw = (float) Math.atan2(-to.x, -to.z);
        return new float[]{x, yaw, distance / tipReach};
    }

    /** How far wound up the arm is, 0..1, at this value of the game's swing. */
    static float windUp(float phase) {
        return curve(time(phase));
    }

    /** The same at this share of the swing's time: up eased, down gathering speed and landing eased. */
    static float curve(float time) {
        if (time <= 0f || time >= IMPACT) return 0f;
        if (time < TOP) {
            float k = time / TOP;
            return Ease.smooth(k);
        }
        float k = (time - TOP) / (IMPACT - TOP);
        return 1 - Ease.smooth(k);
    }

    /**
     * {xRot, yRot, zRot} of a part turned by {@code q}, for the part's R = Rz Ry Rx. Worked out
     * from the rotated axes: joml's own getEulerAnglesZYX gave a wrong pose here.
     */
    static Vector3f zyx(Quaternionf q) {
        Vector3f c0 = q.transform(new Vector3f(1f, 0f, 0f));
        Vector3f c1 = q.transform(new Vector3f(0f, 1f, 0f));
        Vector3f c2 = q.transform(new Vector3f(0f, 0f, 1f));
        float y = (float) Math.asin(Math.max(-1f, Math.min(1f, -c0.z)));
        float x = (float) Math.atan2(c1.z, c2.z);
        float z = (float) Math.atan2(c0.y, c0.x);
        return new Vector3f(x, y, z);
    }
}
