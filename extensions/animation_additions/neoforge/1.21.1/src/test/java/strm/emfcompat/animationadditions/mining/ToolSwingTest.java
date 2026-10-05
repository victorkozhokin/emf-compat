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

    /** A strike to wind up from, for the tools whose wound-up pose does not depend on it. */
    private static final Quaternionf AHEAD = new Quaternionf().rotateX((float) Math.toRadians(-60));

    private static final ToolSwing.Tool[] TOOLS = {ToolSwing.PICKAXE, ToolSwing.AXE, ToolSwing.SHOVEL, ToolSwing.HOE};

    private static Vector3f tip(ToolSwing.Tool tool, boolean right, int blow) {
        float turn = (float) Math.toRadians(ToolSwing.turned(tool, blow)) * (right ? 1 : -1);
        return new Quaternionf().rotateY(turn).transform(new Vector3f(0f, tool.tipY(), tool.tipZ()));
    }

    @Test
    void everyToolInEitherHandLandsItsWorkingPartOnThePointAtEveryBlow() {
        float[][] targets = {{0, 2, -14}, {-4, 6, -12}, {5, -3, -13}, {0, 9, -11.5f}, {-6, 0, -12}};
        for (ToolSwing.Tool tool : TOOLS) for (boolean right : new boolean[]{true, false})
            for (int blow = 0; blow < 3; blow++) for (float[] t : targets) {
                Vector3f to = new Vector3f(t).normalize((float) Math.hypot(tool.tipY(), tool.tipZ()));
                // The strike holds the tool as it is turned in the hand, so its turn is taken off again.
                float turn = (float) Math.toRadians(ToolSwing.turned(tool, blow)) * (right ? 1 : -1);
                Quaternionf arm = ToolSwing.strike(to, tool, right, blow).rotateY(-turn);
                assertEquals(0f, arm.transform(tip(tool, right, blow)).distance(to), 1e-3f, tool.name() + " " + right + " " + blow + " " + to);
            }
    }

    @Test
    void thePickaxeComesFromTheUpperRightAtAnotherAngleEachBlow() {
        float[] leans = new float[3];
        for (int blow = 0; blow < 3; blow++) {
            Vector3f arm = ToolSwing.wound(ToolSwing.PICKAXE, true, blow, AHEAD).transform(new Vector3f(0, 1, 0));
            // Up is -y, the right side -x, forward -z.
            org.junit.jupiter.api.Assertions.assertTrue(arm.y < 0 && arm.x < 0 && arm.z < 0, "blow " + blow + ": " + arm);
            leans[blow] = (float) Math.toDegrees(Math.atan2(-arm.x, -arm.y));
        }
        assertEquals(45f, leans[0], .5f);
        assertEquals(60f, leans[1], .5f);
        assertEquals(30f, leans[2], .5f);
        // The fourth is the first again, and the left arm is the mirror.
        Vector3f again = ToolSwing.wound(ToolSwing.PICKAXE, true, 3, AHEAD).transform(new Vector3f(0, 1, 0));
        Vector3f left = ToolSwing.wound(ToolSwing.PICKAXE, false, 0, AHEAD).transform(new Vector3f(0, 1, 0));
        Vector3f first = ToolSwing.wound(ToolSwing.PICKAXE, true, 0, AHEAD).transform(new Vector3f(0, 1, 0));
        assertEquals(0f, again.distance(first), 1e-5f);
        assertEquals(0f, left.distance(new Vector3f(-first.x, first.y, first.z)), 1e-5f);
    }

    @Test
    void theAxeComesFromBehindTheRightShoulderAcrossToTheLeftEdgeFirst() {
        Vector3f to = new Vector3f(0, 2, -14);
        for (int blow = 0; blow < 3; blow++) {
            Quaternionf wound = ToolSwing.wound(ToolSwing.AXE, true, blow, AHEAD), strike = ToolSwing.strike(to, ToolSwing.AXE, true, blow);
            Vector3f from = wound.transform(new Vector3f(0, 1, 0)), at = strike.transform(new Vector3f(0, 1, 0));
            // Up is -y, the right side -x, behind +z: the hand up over the right shoulder, not out in front.
            org.junit.jupiter.api.Assertions.assertTrue(from.y < -.75f && from.x <= .01f && from.z > -.3f, "blow " + blow + " from " + from);
            org.junit.jupiter.api.Assertions.assertTrue(at.z < -.5f && at.y > from.y + .5f, "blow " + blow + ": " + from + " -> " + at);
            // Edge first: the head trails the hand, so at the blow it lies to the right of the arm.
            Vector3f head = strike.transform(new Vector3f(0, 0, ToolSwing.AXE.tipZ()));
            org.junit.jupiter.api.Assertions.assertTrue(head.x < -1.5f, "blow " + blow + " head " + head);
        }
    }

    @Test
    void atTheBlowTheHandWithTheAxeIsAtTheChestNotBeforeTheFace() {
        // Points looked at straight ahead and a little up or down, from the right shoulder; the head is above y 0.
        Vector3f shoulder = new Vector3f(-5, 2, 0);
        for (float[] point : new float[][]{{0, -3, -15}, {0, 0, -16}, {0, -6, -14}, {2, -2, -15}})
            for (int blow = 0; blow < 3; blow++) {
                Vector3f to = new Vector3f(point).sub(shoulder);
                Vector3f hand = ToolSwing.strike(to, ToolSwing.AXE, true, blow).transform(new Vector3f(0, 10, 0)).add(shoulder);
                // The hand is a 4 px box: its top stays under the chin, and it stays about the middle of the chest, not across it.
                org.junit.jupiter.api.Assertions.assertTrue(hand.y > 2.5f, "blow " + blow + " at " + new Vector3f(point) + ": hand " + hand);
                org.junit.jupiter.api.Assertions.assertTrue(hand.x < 2f, "blow " + blow + " at " + new Vector3f(point) + ": hand " + hand);
            }
    }

    @Test
    void theShovelIsDrawnBackFromThePointAndTheHoeRaisedShortOfAPickaxe() {
        // How far off the line ahead an arm is raised, whichever way it leans.
        Vector3f pick = ToolSwing.wound(ToolSwing.PICKAXE, true, 0, AHEAD).transform(new Vector3f(0, 1, 0));
        double pickaxe = Math.hypot(pick.x, pick.y);
        for (int blow = 0; blow < 3; blow++) {
            Vector3f hoe = ToolSwing.wound(ToolSwing.HOE, true, blow, AHEAD).transform(new Vector3f(0, 1, 0));
            org.junit.jupiter.api.Assertions.assertTrue(hoe.z < -.5f && hoe.y < -.2f && Math.hypot(hoe.x, hoe.y) < pickaxe && hoe.x < -.1f, "hoe " + blow + ": " + hoe);
            // The shovel, by the feet and at the chest alike: the hand back from where it strikes, the blade off the point.
            Vector3f tip = new Vector3f(0, ToolSwing.SHOVEL.tipY(), ToolSwing.SHOVEL.tipZ());
            for (float[] point : new float[][]{{3, 16, -12}, {4, 2, -15}, {5, 20, -6}}) for (boolean right : new boolean[]{true, false}) {
                Vector3f to = new Vector3f(point).normalize(tip.length());
                Quaternionf strike = ToolSwing.strike(to, ToolSwing.SHOVEL, right, blow), wound = ToolSwing.wound(ToolSwing.SHOVEL, right, blow, strike);
                Vector3f hand = strike.transform(new Vector3f(0, 10, 0)), drawn = wound.transform(new Vector3f(0, 10, 0));
                org.junit.jupiter.api.Assertions.assertTrue(drawn.z > hand.z + 2 || drawn.y < hand.y - 2, "shovel " + blow + " at " + to + ": " + hand + " -> " + drawn);
                float off = wound.transform(new Vector3f(tip)).distance(to);
                org.junit.jupiter.api.Assertions.assertTrue(off > 6 && off < 20, "shovel " + blow + " at " + to + ": off the point by " + off);
            }
        }
        // Each blow is another: the three wound-up poses of a tool are apart.
        for (ToolSwing.Tool tool : TOOLS) for (int a = 0; a < 3; a++) for (int b = a + 1; b < 3; b++) {
            Vector3f one = ToolSwing.wound(tool, true, a, AHEAD).transform(new Vector3f(0, 1, 0));
            Vector3f other = ToolSwing.wound(tool, true, b, AHEAD).transform(new Vector3f(0, 1, 0));
            org.junit.jupiter.api.Assertions.assertTrue(one.distance(other) > .1f, tool.name() + " " + a + "/" + b);
        }
    }

    @Test
    void theShovelWorksOutToTheRightOfTheBody() {
        // Points before the middle of the body, from the right shoulder at x -5: by the feet, in the floor, at the chest.
        Vector3f shoulder = new Vector3f(-5, 2, 0);
        for (float[] point : new float[][]{{0, 14, -16}, {0, 22, -10}, {0, 2, -17}, {1, 18, -13}})
            for (int blow = 0; blow < 3; blow++) {
                Vector3f to = new Vector3f(point).sub(shoulder);
                Quaternionf strike = ToolSwing.strike(to, ToolSwing.SHOVEL, true, blow);
                Vector3f hand = strike.transform(new Vector3f(0, 10, 0)).add(shoulder);
                Vector3f drawn = ToolSwing.wound(ToolSwing.SHOVEL, true, blow, strike).transform(new Vector3f(0, 10, 0)).add(shoulder);
                // And the blade is still on the point.
                Vector3f blade = strike.transform(new Vector3f(0, ToolSwing.SHOVEL.tipY(), ToolSwing.SHOVEL.tipZ())).normalize();
                assertEquals(0f, blade.distance(new Vector3f(to).normalize()), 1e-3f);
                // The arm is 4 px wide and the body's side is at x -4: the hand stays outside the shoulder's own line.
                org.junit.jupiter.api.Assertions.assertTrue(hand.x < -5.5f && drawn.x < -5f, "blow " + blow + " at " + new Vector3f(point) + ": " + hand + " / " + drawn);
            }
    }

    @Test
    void aSwingWindsUpFromThePointAndIsBackOnItBeforeTheNextStarts() {
        assertEquals(0f, ToolSwing.curve(0f), 1e-6f);
        assertEquals(1f, ToolSwing.curve(ToolSwing.TOP), 1e-6f);
        assertEquals(0f, ToolSwing.curve(ToolSwing.IMPACT), 1e-6f);
        assertEquals(0f, ToolSwing.curve(1f), 1e-6f);
        // Breaking a block: three ticks to the half of the game's value, the rest in the fourth.
        assertEquals(0f, ToolSwing.time(0f), 1e-6f);
        assertEquals(.75f, ToolSwing.time(.5f), 1e-6f);
        assertEquals(1f, ToolSwing.time(1f), 1e-6f);
        // So tick by tick, at ten frames a tick, no frame takes more than a tenth of the way.
        float before = 0f;
        for (int frame = 1; frame <= 40; frame++) {
            float ticks = frame / 10f, phase = ticks <= 3 ? ticks / 6f : .5f + (ticks - 3) * .5f;
            float now = ToolSwing.windUp(phase);
            org.junit.jupiter.api.Assertions.assertTrue(Math.abs(now - before) < .1f, "frame " + frame + ": " + before + " -> " + now);
            before = now;
        }
        assertEquals(0f, before, 1e-6f);
    }
}
