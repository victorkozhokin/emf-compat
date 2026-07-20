package strm.emfcompat.ironspells.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.ironspells.compat.IronSpellsCompat;

/**
 * Captures the arms after Iron's Spells (via Player Animator) has applied its casting
 * animation in {@link PlayerModel#setupAnim}. Core mixins then restore only the arms after
 * EMF animation runs, leaving the head, body and legs under EMF's control — the same approach
 * used by the Better Combat and Not Enough Animations addons.
 *
 * <p>Player Animator applies its animation at {@code PlayerModel.setupAnim} RETURN with priority 2000,
 * so this mixin runs at priority 2500 to capture the pose <em>after</em> Iron's Spells has modified the model.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
@SuppressWarnings("unchecked")
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "iron_spells";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureIronSpellsCastingPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                        float ageInTicks, float netHeadYaw, float headPitch,
                                                        CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        if (!IronSpellsCompat.isCasting(player)) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        PoseManager.savePoses(
                player.getUUID(), SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm)
        );
    }
}
