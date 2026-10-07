package strm.touchnmotion.blockuse;

import strm.touchnmotion.interaction.Ease;

/** Alternating regrips between six equally spaced rim anchors; at least one hand remains on the rim. */
public final class SteeringGripMotion {
    private static final float SECONDS = .28f;
    private final float[] offsets = new float[2];
    private final float[] centres;
    public SteeringGripMotion() { this(0); }
    public SteeringGripMotion(float upperRight) { centres = new float[]{upperRight, -upperRight}; }
    private float angle, previous, elapsed, from, to;
    private boolean initialized;
    private int moving = -1, next;
    public int transfers;

    void advance(float radians, float dt) {
        float degrees = (float) Math.toDegrees(radians);
        if (!initialized) {
            initialized = true;
            previous = degrees;
            angle = CrankStanceMath.delta(0, degrees);
            // Acquire a comfortable point on each half, including an already turned wheel.
            for (int hand = 0; hand < 2; hand++)
                offsets[hand] = Math.round((centres[hand] - angle) / 60) * 60;
        } else {
            angle += CrankStanceMath.delta(previous, degrees);
            previous = degrees;
        }
        if (moving >= 0) {
            elapsed = Math.min(SECONDS, elapsed + Math.max(0, Math.min(.1f, dt)));
            offsets[moving] = from + (to - from) * Ease.smooth(elapsed / SECONDS);
            if (elapsed >= SECONDS) { next = 1 - moving; moving = -1; }
        }
        if (moving < 0) {
            for (int n = 0; n < 2; n++) {
                int hand = (next + n) % 2;
                float phase = angle + offsets[hand];
                float error = phase - centres[hand];
                if (Math.abs(error) > (centres[hand] == 0 ? (hand == 0 ? 35 : 55) : 30)) {
                    moving = hand;
                    elapsed = 0;
                    from = offsets[hand];
                    to = from - Math.copySign(60, error);
                    transfers++;
                    break;
                }
            }
        }
        // A very fast turn can outrun a transfer. The planted hand slides on its own half,
        // rather than crossing the arms or teleporting to the other side.
        for (int hand = 0; hand < 2; hand++) {
            float phase = angle + offsets[hand];
            if (hand != moving) offsets[hand] += bounded(hand, phase) - phase;
        }
    }

    public float radians(int hand) {
        return (float) Math.toRadians(bounded(hand, angle + offsets[hand]));
    }

    private float bounded(int hand, float phase) {
        float lo = centres[hand] == 0 ? -70 : centres[hand] - 40;
        float hi = centres[hand] == 0 ? 70 : centres[hand] + 40;
        return Math.max(lo, Math.min(hi, phase));
    }

    public float lift(int hand) {
        return hand == moving ? CrankStanceMath.lift(elapsed / SECONDS) / 16 : 0;
    }

    public int slot(int hand) { return Math.floorMod(Math.round(offsets[hand] / 60) + (hand == 0 ? 0 : 3), 6); }
    public int moving() { return moving; }
}
