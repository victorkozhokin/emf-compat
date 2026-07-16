package strm.emfcompat.immersivemelodies.mixin;

import immersive_melodies.client.animation.accessors.ModelAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.immersivemelodies.compat.ImmersiveMelodiesCompat;

import java.util.Optional;

/**
 * Captures the arm poses set by Immersive Melodies after it has animated the player model.
 * The Core mixins later restore these poses after EMF applies its resource-pack animation,
 * so instrument-playing arm poses are not overwritten by Entity Model Features.
 */
@Mixin(immersive_melodies.client.animation.EntityModelAnimator.class)
public class EntityModelAnimatorMixin {

    private static final String SOURCE = "immersive_melodies";

    @Inject(method = "setAngles", at = @At("RETURN"), remap = false)
    private static <T extends Entity> void emfcompat$onSetAnglesReturn(ModelAccessor<T> accessor, CallbackInfo ci) {
        T entity = accessor.getEntity();
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        if (!ImmersiveMelodiesCompat.hasInstrument(player)) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        Optional<ModelPart> leftArm = accessor.getLeftArm();
        Optional<ModelPart> rightArm = accessor.getRightArm();
        if (leftArm.isEmpty() && rightArm.isEmpty()) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        PoseManager.savePoses(
                player.getUUID(),
                SOURCE,
                leftArm.map(PoseSnapshot::new).orElse(null),
                rightArm.map(PoseSnapshot::new).orElse(null)
        );
    }
}
