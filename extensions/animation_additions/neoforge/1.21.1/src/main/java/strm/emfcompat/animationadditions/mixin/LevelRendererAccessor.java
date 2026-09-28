package strm.emfcompat.animationadditions.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The blocks being broken, by the id of whoever breaks each - the local player and everyone watched. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("destroyingBlocks")
    Int2ObjectMap<BlockDestructionProgress> emfcompat$destroyingBlocks();
}
