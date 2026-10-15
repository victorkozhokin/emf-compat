package strm.emfcompat.equipments.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.armortrim.ArmorTrim;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.equipments.ArmorFallback;
import strm.emfcompat.equipments.EquipmentsEMFCompat;
import strm.emfcompat.equipments.TextureSizes;
import strm.emfcompat.equipments.compat.NoMansLandWear;
import traben.entity_model_features.models.parts.EMFModelPart;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Shadow @Final private TextureAtlas armorTrimAtlas;

    /**
     * NeoForge's per-item model hook is where the piece's model is settled; every layer, the trim
     * and the glint are then drawn with whatever comes out of it.
     */
    @ModifyExpressionValue(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;getArmorModelHook(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/client/model/HumanoidModel;)Lnet/minecraft/client/model/Model;"))
    private Model emfCompat$equipmentModel(Model model,
                                         @Local(argsOnly = true) LivingEntity entity,
                                         @Local(argsOnly = true) EquipmentSlot slot,
                                         @Local(argsOnly = true) HumanoidModel<?> wearer) {
        model = ArmorFallback.choose(model, entity, slot);
        // Fixes for worn models that do not follow the body are only needed while a pack animates it.
        HumanoidModel<?> parent = (HumanoidModel<?>) ((RenderLayer<?, ?>) (Object) this).getParentModel();
        if (parent.head instanceof EMFModelPart) {
            model = NoMansLandWear.attach(model, wearer);
        }
        return model;
    }

    /** A trim is a texture too: drawn for one layout, it is noise on the other. */
    @Inject(
            method = "renderTrim(Lnet/minecraft/core/Holder;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/Model;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void emfCompat$skipUnfittingTrim(Holder<ArmorMaterial> material, PoseStack poseStack, MultiBufferSource buffers,
                                             int light, ArmorTrim trim, Model model, boolean inner, CallbackInfo ci) {
        if (!EquipmentsEMFCompat.trimCheckEnabled()) return;
        int[] layout = ArmorFallback.layoutOf(model);
        if (layout == null) return;
        SpriteContents sprite = armorTrimAtlas.getSprite(inner ? trim.innerTexture(material) : trim.outerTexture(material)).contents();
        if (sprite.name().equals(MissingTextureAtlasSprite.getLocation())) return;
        if (!TextureSizes.sameShape(new int[]{sprite.width(), sprite.height()}, layout)) ci.cancel();
    }
}
