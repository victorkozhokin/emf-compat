package strm.emfcompat.animationadditions.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.animationadditions.pocket.PocketStash;

/** The server's word that a player took an item off the ground, for any player in view. */
@Mixin(ClientPacketListener.class)
public class ItemPickupMixin {
    @Inject(method="handleTakeItemEntity",at=@At("HEAD"))
    private void emfcompat$picked(ClientboundTakeItemEntityPacket packet,CallbackInfo ci) {
        Minecraft mc=Minecraft.getInstance();
        // The handler runs twice: on the network thread it only hands itself over to this one.
        if(!mc.isSameThread() || mc.level==null) return;
        Entity taken=mc.level.getEntity(packet.getItemId());
        if(taken==null || taken instanceof ExperienceOrb) return;
        if(mc.level.getEntity(packet.getPlayerId()) instanceof AbstractClientPlayer player) PocketStash.picked(player);
    }
}
