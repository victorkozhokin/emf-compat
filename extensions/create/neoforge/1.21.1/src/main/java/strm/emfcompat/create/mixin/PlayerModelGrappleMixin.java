package strm.emfcompat.create.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.create.EMFCompatCreateMod;
import strm.emfcompat.create.GrappleHookHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps Create: Grappling Hooks' hang pose on the player while EMF is active.
 *
 * <p>{@code addon_gancho} poses the whole model (head, body, both arms, both legs, with sway)
 * from its own {@code HumanoidModel.setupAnim} RETURN injector. EMF would then re-animate the
 * model from the resource pack and slide the parts it touches back toward the pack's idle,
 * leaving a half-grapple / half-idle pose. Instead of pausing EMF / forcing the vanilla model
 * (the legacy approach, which also disabled player-expression animations), we capture the final
 * hang pose here — {@link PlayerModel#setupAnim} runs {@code super.setupAnim} (where gancho
 * poses) first — and let the Core mixins restore it after EMF runs. Same capture-and-restore
 * path as the skyhook and the Aeronautics handle.</p>
 *
 * <p>Only applied when {@code addon_gancho} is present (see the mixin config plugin); it is the
 * sole reason this class references {@link GrappleHookHelper}, so the gancho classes never load
 * without the mod.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelGrappleMixin {

    private static final String SOURCE = "create_grapple";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureGrapplePose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (Minecraft.getInstance().isPaused()) return;
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return;

        if (!EMFCompatCreateMod.isEnabled() || !EMFCompatCreateMod.isGrappling()
                || !GrappleHookHelper.isGrappling(player)) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        // Grapple rigidly poses the whole body (with its own sway), so capture every part each
        // frame and restore it absolutely. Hat / sleeves / pants / jacket follow their parents.
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("head", new PoseSnapshot(model.head));
        parts.put("body", new PoseSnapshot(model.body));
        parts.put("left_arm", new PoseSnapshot(model.leftArm));
        parts.put("right_arm", new PoseSnapshot(model.rightArm));
        parts.put("left_leg", new PoseSnapshot(model.leftLeg));
        parts.put("right_leg", new PoseSnapshot(model.rightLeg));

        PoseManager.savePoses(player.getUUID(), SOURCE, null, null, parts);
    }
}
