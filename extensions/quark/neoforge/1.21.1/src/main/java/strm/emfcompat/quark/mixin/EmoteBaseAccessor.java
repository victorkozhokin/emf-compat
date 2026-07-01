package strm.emfcompat.quark.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.violetmoon.quark.content.tweaks.client.emote.EmoteBase;

/**
 * Accessor for reading Quark emote internal state.
 */
@Mixin(EmoteBase.class)
public interface EmoteBaseAccessor {

    @Accessor("model")
    HumanoidModel<?> emfcompat$getModel();

    @Accessor("player")
    Player emfcompat$getPlayer();
}
