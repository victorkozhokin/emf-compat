package strm.emfcompat.animationadditions.mining;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningBodyTest {
    private static final ToolSwing.Tool[] TOOLS = {ToolSwing.PICKAXE, ToolSwing.AXE, ToolSwing.SHOVEL, ToolSwing.HOE};

    @Test
    void theToolSideFootIsBackAndTheOtherForwardWithinAStraightLegsReach() {
        for (ToolSwing.Tool tool : TOOLS) {
            Vector3f[] feet = MiningBody.feet(tool, true);
            assertTrue(feet[0].z > 0 && feet[1].z < 0, tool.name());
            assertTrue(feet[0].x < 0 && feet[1].x > 0, "apart, not crossed: " + tool.name());
            for (Vector3f foot : feet) assertTrue(foot.length() < 2.5f, tool.name() + " " + foot);
        }
    }

    @Test
    void theLeftHandIsTheMirror() {
        for (ToolSwing.Tool tool : TOOLS) {
            Vector3f[] r = MiningBody.feet(tool, true), l = MiningBody.feet(tool, false);
            assertEquals(-r[0].x, l[1].x, 1e-6);
            assertEquals(r[0].z, l[1].z, 1e-6);
            assertEquals(-r[1].x, l[0].x, 1e-6);
            float[] a = MiningBody.lean(tool, true, 10, .3f), b = MiningBody.lean(tool, false, 10, .3f);
            assertEquals(a[0], b[0], 1e-6);
            assertEquals(-a[1], b[1], 1e-6);
            assertEquals(-a[2], b[2], 1e-6);
        }
    }

    @Test
    void aLowPointBendsTheTorsoForwardAndAHighOneBack() {
        float wound = .5f;
        assertTrue(MiningBody.lean(ToolSwing.PICKAXE, true, 22, wound)[0] > Math.toRadians(12));
        assertEquals(0, MiningBody.lean(ToolSwing.PICKAXE, true, 4, wound)[0], 1e-6);
        assertTrue(MiningBody.lean(ToolSwing.PICKAXE, true, -12, wound)[0] < Math.toRadians(-5));
    }

    @Test
    void theStrikeBringsTheWorkingShoulderRoundAndTheBodyDown() {
        for (ToolSwing.Tool tool : TOOLS) {
            float[] wound = MiningBody.lean(tool, true, 4, 1), struck = MiningBody.lean(tool, true, 4, 0);
            assertTrue(struck[0] > wound[0], tool.name());
            // Turned right is the right shoulder back; the blow takes some of that turn out.
            assertTrue(struck[1] < wound[1] && wound[1] > 0, tool.name());
            // A swing, not a lunge: a moderate pulse.
            assertTrue(Math.toDegrees(struck[0] - wound[0]) <= 9.01 && Math.toDegrees(wound[1] - struck[1]) <= 12.01, tool.name());
        }
    }

    @Test
    void thePelvisHelpsOnlyWhenTheToolIsShort() {
        assertEquals(0, MiningBody.shortBy(10, 14), 1e-6);
        assertEquals(0, MiningBody.shortBy(14, 14), 1e-6);
        assertEquals(1, MiningBody.shortBy(17.5f, 14), 1e-6);
        // And only for what is down by the legs: a far block at chest height is not lunged at.
        assertEquals(0, MiningBody.low(4), 1e-6);
        assertEquals(0, MiningBody.low(12), 1e-6);
        assertEquals(1, MiningBody.low(20), 1e-6);
    }
}
