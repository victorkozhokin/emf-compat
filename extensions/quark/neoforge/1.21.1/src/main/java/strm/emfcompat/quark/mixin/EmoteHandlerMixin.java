package strm.emfcompat.quark.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.violetmoon.quark.content.tweaks.client.emote.EmoteBase;
import org.violetmoon.quark.content.tweaks.client.emote.EmoteHandler;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;

/**
 * Clears saved Quark poses once the emote has finished or the player no longer has an active emote.
 */
@Mixin(EmoteHandler.class)
public class EmoteHandlerMixin {

    @Unique
    private static final String SOURCE = "quark";

    @Inject(
            method = "updateEmotes(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void emfcompat$skipCorpseDummy(Entity e, CallbackInfo ci) {
        if (EMFCompatCore.isCorpseDummy(e)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "updateEmotes(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("RETURN"),
            remap = false
    )
    private static void emfcompat$clearPosesWhenEmoteEnds(Entity e, CallbackInfo ci) {
        if (!(e instanceof AbstractClientPlayer player)) {
            return;
        }

        if (EMFCompatCore.isCorpseDummy(player)) {
            return;
        }

        EmoteBase emote = EmoteHandler.getPlayerEmote(player);
        if (emote == null || emote.isDone()) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
        }
    }
}
