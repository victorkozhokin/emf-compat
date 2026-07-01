package strm.emfcompat.carryon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import tschipp.carryon.client.render.CarriedObjectRender;

/**
 * Accessor for {@link CarriedObjectRender}'s private rendering helpers.
 * <p>
 * Carry On's public {@link CarriedObjectRender#draw} method returns early when the
 * FirstPersonModel mod is loaded, so we need direct access to the block/entity
 * render paths to draw the carried object in first person.
 */
@Mixin(CarriedObjectRender.class)
public interface CarriedObjectRenderAccessor {

    @Invoker("drawBlock")
    static void invokeDrawBlock(Player player, PoseStack poseStack, int light, BlockState state,
                                SubmitNodeCollector buffers, boolean leftHanded, float partialTick) {
        throw new AssertionError();
    }

    @Invoker("drawEntity")
    static void invokeDrawEntity(Player player, PoseStack poseStack, int light, float partialTick,
                                 SubmitNodeCollector buffers, boolean leftHanded) {
        throw new AssertionError();
    }
}
