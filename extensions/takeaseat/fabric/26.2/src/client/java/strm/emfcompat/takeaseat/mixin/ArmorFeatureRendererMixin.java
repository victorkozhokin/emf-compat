package strm.emfcompat.takeaseat.mixin;

import com.takeaseat.client.TakeASeatClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import strm.emfcompat.takeaseat.client.SittingArmorModel;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.utils.EMFEntity;
import traben.entity_texture_features.features.state.HoldsETFRenderState;

import java.util.UUID;
import java.util.function.Function;

@Mixin(HumanoidArmorLayer.class)
public class ArmorFeatureRendererMixin {

    @ModifyVariable(
            method = "renderArmorPiece",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private HumanoidModel<?> takeaseat$wrapArmorModel(
            HumanoidModel<?> model,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            ItemStack stack,
            EquipmentSlot slot,
            int light,
            HumanoidRenderState state
    ) {
        if (!(state instanceof HoldsETFRenderState)) return model;

        EMFEntityRenderState emfState = EMFEntityRenderState.from(state);
        if (emfState == null) return model;

        EMFEntity emfEntity = emfState.emfEntity();
        if (!(emfEntity instanceof Entity entity) || !(entity instanceof AbstractClientPlayer player)) return model;

        IAnimation layer;
        try {
            layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, TakeASeatClient.SIT_LAYER);
        } catch (Exception e) {
            return model;
        }
        if (layer == null || !layer.isActive()) return model;

        UUID uuid = player.getUUID();
        Function<Identifier, RenderType> layerFactory = model::renderType;
        return new SittingArmorModel<>(model.root(), layerFactory, uuid);
    }
}
