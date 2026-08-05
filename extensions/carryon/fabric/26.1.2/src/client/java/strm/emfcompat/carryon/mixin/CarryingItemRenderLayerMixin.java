package strm.emfcompat.carryon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.compat.CarryOnCompat;
import tschipp.carryon.client.render.CarryRenderHelper;
import tschipp.carryon.client.render.CarriedObjectRender;
import tschipp.carryon.client.render.CarryingItemRenderLayer;
import tschipp.carryon.client.render.ICarryOnRenderState;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;

/**
 * Renders Carry On's carried object in third person when FirstPersonModel is present.
 * <p>
 * Carry On's {@link CarriedObjectRender#draw} returns early when the FirstPersonModel mod
 * is loaded, so the {@link CarryingItemRenderLayer} never draws the carried block/entity.
 * This mixin runs after the layer's own render call and draws the object directly,
 * restoring third-person visibility without touching first-person behaviour.
 */
@Mixin(CarryingItemRenderLayer.class)
public class CarryingItemRenderLayerMixin {

    private static final boolean FIRST_PERSON_MOD_LOADED =
            FabricLoader.getInstance().isModLoaded("firstperson");

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("RETURN")
    )
    private void emfcompat$renderCarriedObjectForFirstPersonMod(PoseStack poseStack, SubmitNodeCollector buffers,
                                                                 int light, AvatarRenderState renderState,
                                                                 float partialTick, float unused,
                                                                 CallbackInfo ci) {
        if (!FIRST_PERSON_MOD_LOADED) return;
        if (!(renderState instanceof ICarryOnRenderState carryState)) return;

        Player player = carryState.getPlayer();
        if (player == null || !CarryOnCompat.isCarrying(player)) return;

        CarryOnData data = CarryOnDataManager.getCarryData(player);
        if (data.isCarrying(CarryOnData.CarryType.BLOCK)) {
            CarriedObjectRenderAccessor.invokeDrawBlock(player, poseStack, light,
                    CarryRenderHelper.getRenderState(player), buffers, false, partialTick);
        } else if (data.isCarrying(CarryOnData.CarryType.ENTITY)) {
            CarriedObjectRenderAccessor.invokeDrawEntity(player, poseStack, light, partialTick, buffers, false);
        }
    }
}
