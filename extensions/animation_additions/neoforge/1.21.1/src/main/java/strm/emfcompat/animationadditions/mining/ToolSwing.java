package strm.emfcompat.animationadditions.mining;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The tools and the swing's own arithmetic for {@link Mining}: no game classes, so it is unit-tested. */
final class ToolSwing {

    private ToolSwing() {
    }

    /** When in the swing, 0..1, the item is on the point. */
    static final float IMPACT = 0.4f;
    /**
     * A tool: where it is on the arm, pixels, in the arm's own space (the arm along +y from the
     * shoulder, -z forward) - the handle in the hand and the working part, from the handheld item
     * transform and the tool's sprite - and the right arm's wound-up pose, degrees, as rotations
     * (documentation.md §15.6). The swing goes from the wound-up pose to the strike and back.
     */
    record Tool(String name, float gripY, float gripZ, float tipY, float tipZ,
                        float windX, float windY, float windZ) {
        Quaternionf wound(boolean right) {
            float side = right ? 1f : -1f;
            // Mirrored for the left arm: yRot and zRot change sign, xRot does not.
            return new Quaternionf().rotationZYX(side * (float) Math.toRadians(windZ),
                    side * (float) Math.toRadians(windY), (float) Math.toRadians(windX));
        }
    }

    /** Overhead: the arm high over the shoulder, a little out, the pick back over it clear of the head. */
    static final Tool PICKAXE = new Tool("pickaxe", 8.7f, -1.3f, 11.3f, -9.3f, -145f, -12f, -20f);
    /** A chop from the side: the arm out to its side at the shoulder, the axe's head up and back. */
    static final Tool AXE = new Tool("axe", 8.7f, -1.3f, 12f, -9.8f, -180f, 55f, -90f);
    /** A thrust: the hand drawn back low by the hip, the blade forward and down, driven in. */
    static final Tool SHOVEL = new Tool("shovel", 8.7f, -1.3f, 10.5f, -11.9f, 35f, -5f, 10f);
    /** A short chop down: the arm up in front, lower than a pick, the blade over it. */
    static final Tool HOE = new Tool("hoe", 8.7f, -1.3f, 11.3f, -9.3f, -150f, 20f, -15f);

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
                : Math.max(0f, Math.min(1f, (distance - gripReach) / (tipReach - gripReach)));
        float y = tool.gripY + s * (tool.tipY - tool.gripY);
        float z = tool.gripZ + s * (tool.tipZ - tool.gripZ);
        // The arm's rotation about x turns that part round by the same angle from where it hangs.
        float offset = (float) Math.atan2(z, y);
        float dy = Math.max(-1f, Math.min(1f, to.y / distance));
        float x = -(float) Math.acos(dy) - offset;
        float yaw = (float) Math.atan2(-to.x, -to.z);
        return new float[]{x, yaw, distance / tipReach};
    }

    /** How far wound up the arm is, 0..1, at this point of the swing. */
    static float windUp(float phase) {
        if (phase < IMPACT) {
            float k = (float) Math.cos(Math.PI / 2 * phase / IMPACT);
            return k * k;
        }
        float k = (float) Math.sin(Math.PI / 2 * (phase - IMPACT) / (1f - IMPACT));
        return k * k;
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
