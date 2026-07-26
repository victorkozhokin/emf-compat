package strm.emfcompat.create.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.create.EMFCompatCreateMod;
import strm.emfcompat.create.flight.CreateSaCompat;

/**
 * Captures the arm poses that Create Stuff 'N Additions sets for the Grappling Whisk and the
 * Block Picker (via Create's CustomArmPoseItem) at the end of {@link PlayerModel#setupAnim}.
 * The core mixins restore them after EMF applies its resource-pack animation, so the arms keep
 * holding the hook / carried block instead of playing pack animations.
 *
 * <p>Data-driven via {@link CreateSaCompat} (item ids / synced NBT), so it is a harmless no-op
 * when Create Stuff 'N Additions is not installed. Only the arms are captured; body, head and
 * legs stay under EMF's control.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class CreateSaItemPoseMixin {

    @Unique
    private static final String HOOK_SOURCE = "create_sa_hook";
    @Unique
    private static final String PICKER_SOURCE = "create_sa_picker";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureItemPoses(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return;

        if (!EMFCompatCreateMod.isEnabled() || !EMFCompatCreateMod.isCreateSa()) {
            PoseManager.clearPoses(player.getUUID(), HOOK_SOURCE);
            PoseManager.clearPoses(player.getUUID(), PICKER_SOURCE);
            return;
        }

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        Vector3f bodyBase = EMFCompatCreateMod.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;

        InteractionHand hookHand = CreateSaCompat.getHookedWhiskHand(player);
        if (hookHand != null) {
            PoseManager.savePoses(player.getUUID(), HOOK_SOURCE,
                    new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm), null, bodyBase);
        } else {
            PoseManager.clearPoses(player.getUUID(), HOOK_SOURCE);
        }

        if (CreateSaCompat.isCarryingBlock(player)) {
            PoseManager.savePoses(player.getUUID(), PICKER_SOURCE,
                    new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm), null, bodyBase);
        } else {
            PoseManager.clearPoses(player.getUUID(), PICKER_SOURCE);
        }
    }
}
