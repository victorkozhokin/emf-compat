package strm.emfcompat.quark.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.violetmoon.quark.content.tweaks.client.emote.EmoteBase;
import org.violetmoon.quark.content.tweaks.client.emote.EmoteHandler;
import strm.emfcompat.core.PoseManager;

import java.util.UUID;

/**
 * Watches {@link HumanoidModel#setupAnim} to detect when a Quark emote starts/ends.
 * The actual pose is saved by {@link EmoteBaseMixin} after Quark has modified the model.
 */
@Mixin(value = HumanoidModel.class, priority = 900)
public class PlayerModelMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("QuarkCompat");
    private static final String SOURCE = "quark";
    private static int emfcompat$logCounter = 0;

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$onSetupAnimReturn(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch,
                                              CallbackInfo ci) {
        boolean shouldLog = ++emfcompat$logCounter % 60 == 0;

        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        if (!((Object) this instanceof PlayerModel<?>)) {
            return;
        }

        UUID uuid = UUID.fromString(player.getUUID().toString());
        EmoteBase emote = EmoteHandler.getPlayerEmote(player);

        if (emote == null || emote.isDone()) {
            if (shouldLog) {
                LOGGER.info("[QuarkCompat] setupAnim RETURN: no active emote for {}, clearing Quark poses", player.getGameProfile().getName());
            }
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        if (shouldLog) {
            LOGGER.info("[QuarkCompat] setupAnim RETURN: active emote for {}", player.getGameProfile().getName());
        }
    }
}
