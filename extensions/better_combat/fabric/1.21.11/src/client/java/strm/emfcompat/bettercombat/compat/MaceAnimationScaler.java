package strm.emfcompat.bettercombat.compat;

import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.keyframe.BoneAnimation;
import com.zigythebird.playeranimcore.animation.keyframe.Keyframe;
import com.zigythebird.playeranimcore.animation.keyframe.KeyframeStack;
import team.unnamed.mocha.parser.ast.BinaryExpression;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.parser.ast.FloatExpression;
import team.unnamed.mocha.parser.ast.UnaryExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scales down the torso/body bone keyframes of a Better Combat animation.
 *
 * <p>Player Animation Library stores keyframe values as Mocha expressions, so this scaler
 * recursively scales {@link FloatExpression} constants and walks through binary/unary
 * operations. Non-constant expressions are left unchanged.</p>
 */
public final class MaceAnimationScaler {

    private MaceAnimationScaler() {
    }

    /**
     * Returns a copy of the given animation with the {@code torso} and {@code body} bone
     * rotations and positions scaled by the given factor.
     */
    public static Animation scaleTorsoAndBody(Animation animation, float scale) {
        Map<String, BoneAnimation> scaledBones = new HashMap<>(animation.boneAnimations());
        scaleBone(scaledBones, "torso", scale);
        scaleBone(scaledBones, "body", scale);

        return new Animation(
                animation.data().copy(),
                animation.length(),
                animation.loopType(),
                scaledBones,
                animation.keyFrames(),
                animation.bones(),
                animation.parents()
        );
    }

    private static void scaleBone(Map<String, BoneAnimation> bones, String name, float scale) {
        BoneAnimation original = bones.get(name);
        if (original == null) {
            return;
        }

        bones.put(name, new BoneAnimation(
                scaleStack(original.rotationKeyFrames(), scale),
                scaleStack(original.positionKeyFrames(), scale),
                original.scaleKeyFrames(),
                original.bendKeyFrames()
        ));
    }

    private static KeyframeStack scaleStack(KeyframeStack stack, float scale) {
        return new KeyframeStack(
                scaleFrames(stack.xKeyframes(), scale),
                scaleFrames(stack.yKeyframes(), scale),
                scaleFrames(stack.zKeyframes(), scale)
        );
    }

    private static List<Keyframe> scaleFrames(List<Keyframe> frames, float scale) {
        List<Keyframe> scaled = new ArrayList<>(frames.size());
        for (Keyframe frame : frames) {
            scaled.add(new Keyframe(
                    frame.length(),
                    scaleExpressions(frame.startValue(), scale),
                    scaleExpressions(frame.endValue(), scale),
                    frame.easingType(),
                    frame.easingArgs()
            ));
        }
        return scaled;
    }

    private static List<Expression> scaleExpressions(List<Expression> expressions, float scale) {
        List<Expression> scaled = new ArrayList<>(expressions.size());
        for (Expression expression : expressions) {
            scaled.add(scaleExpression(expression, scale));
        }
        return scaled;
    }

    private static Expression scaleExpression(Expression expression, float scale) {
        return switch (expression) {
            case FloatExpression f -> FloatExpression.of(f.value() * scale);
            case BinaryExpression b -> new BinaryExpression(
                    b.op(),
                    scaleExpression(b.left(), scale),
                    scaleExpression(b.right(), scale)
            );
            case UnaryExpression u -> new UnaryExpression(
                    u.op(),
                    scaleExpression(u.expression(), scale)
            );
            default -> expression;
        };
    }
}
