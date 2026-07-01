package strm.emfcompat.horsesync.mixin;

import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractHorseRenderer.class)
public interface AbstractHorseRendererAccessor {

    @Accessor("scale")
    float emfhorsesync$getScale();
}
