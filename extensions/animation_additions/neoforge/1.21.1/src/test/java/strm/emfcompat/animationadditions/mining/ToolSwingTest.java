package strm.emfcompat.animationadditions.mining;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolSwingTest {

    /** The arm turned by the solved angles puts the tool's point on the line from the shoulder to the target. */
    @Test
    void theStrikeBringsTheToolOntoThePointHit() {
        ToolSwing.Tool tool = ToolSwing.PICKAXE;
        float tipReach = (float) Math.hypot(tool.tipY(), tool.tipZ());
        float[][] targets = {{0, 2, -14}, {-4, 6, -12}, {5, -3, -13}, {0, 9, -11.5f}};
        for (float[] t : targets) {
            Vector3f to = new Vector3f(t).normalize(tipReach);
            float[] aim = ToolSwing.solve(to, tool);
            Vector3f tip = new Quaternionf().rotationZYX(0f, aim[1], aim[0])
                    .transform(new Vector3f(0f, tool.tipY(), tool.tipZ()));
            assertEquals(0f, tip.distance(to), 1e-3f, "target " + to);
            assertEquals(1f, aim[2], 1e-4f);
        }
    }

    @Test
    void theEulerAnglesReadBackWhatTheRendererTurnsBy() {
        float[][] angles = {{0.3f, -0.4f, 0.2f}, {-2.5f, 0.2f, -0.35f}, {0.6f, 1.0f, -1.2f}, {-3.1f, 0.9f, -1.5f}};
        for (float[] a : angles) {
            Quaternionf q = new Quaternionf().rotationZYX(a[2], a[1], a[0]);
            Vector3f e = ToolSwing.zyx(q);
            Quaternionf back = new Quaternionf().rotationZYX(e.z, e.y, e.x);
            for (Vector3f axis : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
                assertEquals(0f, q.transform(new Vector3f(axis)).distance(back.transform(new Vector3f(axis))), 1e-4f);
            }
        }
    }
}
