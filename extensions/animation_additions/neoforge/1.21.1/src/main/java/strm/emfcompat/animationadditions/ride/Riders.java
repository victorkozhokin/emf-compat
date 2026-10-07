package strm.emfcompat.animationadditions.ride;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** What the one who rows, a boat's passenger and a minecart's rider do alike: sit, hold on with both hands, keep hold. */
final class Riders {

    /** Below anything a hand is used for: eating in a boat takes the arm. */
    private static final int PRIORITY = 2;
    private static final float ARM = Skeleton.ARM_TO_FINGERTIPS;
    /** Every part of the body, for moving the rider whole. */
    private static final String[] WHOLE = {"body", "head", "hat", "right_arm", "left_arm", "right_leg", "left_leg"};

    private Riders() {
    }

    /** Carried along, a rider has no stride: the game counts one for another player all the same, and the pack bobs to it. */
    static void carried(AbstractClientPlayer player) {
        player.walkAnimation.setSpeed(0f);
    }

    /**
     * Both hands sent to their places, the right one's and the left one's, in the world; a place
     * further than {@code maxReach} of the arm is let go of. Whether either hand has one.
     */
    static boolean hold(List<Candidate> out, String id, Candidate.Timing timing, IKFrame frame, Vec3 right, Vec3 left, float maxReach) {
        boolean any = false;
        for (int hand = 0; hand < 2; hand++) {
            IKResult aim = OneBoneIK.solveXY(frame, hand == 0 ? RIGHT_SHOULDER : LEFT_SHOULDER, hand == 0 ? right : left, ARM, 0f, 0f);
            if (aim == null || aim.reach() > maxReach) continue;
            out.add(Candidate.single(id, Category.PASSIVE, PRIORITY, 1f, timing, hand == 0 ? Effector.RIGHT_ARM : Effector.LEFT_ARM,
                    new float[]{aim.x(), aim.y()}));
            any = true;
        }
        return any;
    }

    /** The rider moved whole, model pixels: back is +z, up is -y. Before anything that works from where the parts are. */
    static void shift(Function<String, ModelPart> parts, float back, float up) {
        for (String name : WHOLE) {
            ModelPart part = parts.apply(name);
            if (part == null) continue;
            part.z += back;
            part.y -= up;
        }
    }

    /**
     * The last word on the hands {@code id} holds: each fist brought onto its place, model space,
     * from where the shoulder has ended up ({@link BoatRide#settle}); what was left to take up at
     * the shoulder goes into {@code gaps}. After everything else has posed the arms.
     */
    static void grip(UUID uuid, Function<String, ModelPart> parts, String id, Vector3f[] places, float[] gaps) {
        for (int hand = 0; hand < 2; hand++) {
            float weight = InteractionRuntime.weight(uuid, hand == 0 ? Effector.RIGHT_ARM : Effector.LEFT_ARM, id);
            ModelPart arm = parts.apply(hand == 0 ? "right_arm" : "left_arm");
            if (arm != null && weight >= 0.02f) gaps[hand] = BoatRide.settle(arm, places[hand], weight);
        }
    }
}
