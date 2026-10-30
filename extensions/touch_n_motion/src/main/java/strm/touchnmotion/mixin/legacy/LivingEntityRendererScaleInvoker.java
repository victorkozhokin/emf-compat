package strm.touchnmotion.mixin.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The renderer's own scale for an entity (a horse's 1.1, a dragon's growth), up to 1.21.10: run on a fresh stack and read off. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererScaleInvoker {

    @Invoker("scale")
    void touchnmotion$scale(LivingEntity entity, PoseStack stack, float partialTick);
}
