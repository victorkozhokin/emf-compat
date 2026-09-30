package strm.mctest.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.mctest.Bot;

/**
 * A test bot's connection goes nowhere: what the server sends it is dropped before NeoForge checks
 * mods' payloads against channels it never negotiated, and it is never timed out. Test driver only.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class BotListenerMixin {
    @Shadow @Final protected Connection connection;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void mctest$dropForBot(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (Bot.isBot(connection)) ci.cancel();
    }

    @Inject(method = "keepConnectionAlive", at = @At("HEAD"), cancellable = true)
    private void mctest$keepBotAlive(CallbackInfo ci) {
        if (Bot.isBot(connection)) ci.cancel();
    }
}
