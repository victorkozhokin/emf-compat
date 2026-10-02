package strm.emfcompat.animationadditions.torso;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Both grips contribute equally; the torso cannot chase one hand around the rim. */
final class TwoHandReachMath {
    record Fit(Quaternionf turn, Vector3f shift) {}

    static Fit fit(Vector3f shoulder, Vector3f target, Vector3f otherShoulder,
                   Vector3f otherTarget, float arm, float limit) {
        Quaternionf turn = LowReachMath.turn(shoulder, target, arm, limit)
                .slerp(LowReachMath.turn(otherShoulder, otherTarget, arm, limit), .5f).normalize();
        Vector3f fitted = turn.transform(new Vector3f(shoulder));
        Vector3f otherFitted = turn.transform(new Vector3f(otherShoulder));
        Vector3f shift = LowReachMath.shift(fitted, target, arm, 3.5f)
                .add(LowReachMath.shift(otherFitted, otherTarget, arm, 3.5f)).mul(.5f);
        return new Fit(turn, shift);
    }
}
