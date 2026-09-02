package strm.emfcompat.parcool.compat;

import com.alrex.parcool.client.animation.system.AnimatableModelPart;
import com.alrex.parcool.client.animation.system.BlendingModelTransform;
import com.alrex.parcool.client.animation.system.IPlayerAnimatorHolder;
import com.alrex.parcool.client.animation.system.PlayerAnimator;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Reads which parts ParCool 4 is driving. Kept out of the mixin on purpose: a switch over
 * ParCool's enum makes javac emit a synthetic inner class, which a mixin class is a poor
 * place for. It is only ever reached from the ParCool 4 mixin, which the mixin plugin skips
 * unless ParCool 4 is installed, so ParCool 4 classes are never resolved without it.
 */
public final class ParCool4Poses {

    private ParCool4Poses() {
    }

    /**
     * Returns the parts ParCool is posing this frame, or {@code null} when it is leaving the
     * model to vanilla and the pack.
     */
    @Nullable
    public static Set<ParCoolPose.Part> ownedParts(AbstractClientPlayer player) {
        if (!(player instanceof IPlayerAnimatorHolder)) return null;
        // The two states in which ParCool 4 skips its own animation (see its PlayerModelMixin).
        if (player.isFallFlying() || player.isPassenger()) return null;

        BlendingModelTransform transform = PlayerAnimator.get(player).getCurrentTransformation();
        if (transform == null) return null;

        // Overwriting: ParCool reset the model and wrote only the parts it animates, so the
        // parts it left alone sit at their default pose on purpose - it owns the whole model.
        if (transform.isOverwriting()) return ParCoolPose.WHOLE_MODEL;

        // Blending: vanilla ran first and ParCool blended its own parts over the result. The
        // torso is always among them - it gets blended back towards neutral either way.
        Set<ParCoolPose.Part> owned = EnumSet.of(ParCoolPose.Part.BODY);
        for (AnimatableModelPart part : transform.transformation().transforms().keySet()) {
            owned.add(map(part));
        }
        return owned;
    }

    private static ParCoolPose.Part map(AnimatableModelPart part) {
        return switch (part) {
            case HEAD -> ParCoolPose.Part.HEAD;
            case BODY -> ParCoolPose.Part.BODY;
            case LEFT_ARM -> ParCoolPose.Part.LEFT_ARM;
            case RIGHT_ARM -> ParCoolPose.Part.RIGHT_ARM;
            case LEFT_LEG -> ParCoolPose.Part.LEFT_LEG;
            case RIGHT_LEG -> ParCoolPose.Part.RIGHT_LEG;
        };
    }
}
