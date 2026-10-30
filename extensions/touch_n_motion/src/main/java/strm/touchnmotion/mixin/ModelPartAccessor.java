package strm.touchnmotion.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** A model part's own boxes and its named children, to measure a mount's body ({@code ride/shape/PartShape}). */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {

    @Accessor("cubes")
    List<ModelPart.Cube> touchnmotion$cubes();

    @Accessor("children")
    Map<String, ModelPart> touchnmotion$children();
}
