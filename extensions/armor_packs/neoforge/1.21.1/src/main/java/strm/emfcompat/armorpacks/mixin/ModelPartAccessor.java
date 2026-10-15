package strm.emfcompat.armorpacks.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {

    @Accessor("cubes")
    List<ModelPart.Cube> emfCompat$getCubes();

    @Accessor("children")
    Map<String, ModelPart> emfCompat$getChildren();
}
