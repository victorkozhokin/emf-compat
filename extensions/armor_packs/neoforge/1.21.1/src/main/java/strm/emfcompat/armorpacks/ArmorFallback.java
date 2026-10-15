package strm.emfcompat.armorpacks;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.armorpacks.mixin.ModelPartAccessor;
import traben.entity_model_features.models.parts.EMFModelPart;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * An armour pack (Armory Conglomery, 3D armour packs...) gives the armour models its own boxes and
 * its own texture layout, and redraws the textures it knows for that layout. Every other armour -
 * a mod's, or a vanilla one the pack skipped - keeps a texture laid out for the vanilla boxes, and
 * on the pack's boxes it comes out scrambled.
 *
 * The pack's layout is told by the {@code textureSize} of its model: a texture of another shape was
 * not drawn for it (Conglomery: 64x64 against the vanilla 64x32). Such a piece is drawn with the
 * vanilla geometry EMF keeps beside its own ({@link EMFModelPartRoot#vanillaRoot}), posed like the
 * pack's model, so it looks the way its author drew it.
 */
public final class ArmorFallback {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMF Compat: Armor Packs");

    /** The vanilla layout: every vanilla armour layer is baked at 64x32. */
    public static final int[] VANILLA_SIZE = {64, 32};

    /** The {@code textureSize} of each jem a root was given, by EMF variant. */
    private static final Map<EMFModelPartRoot, Map<Integer, int[]>> JEM_SIZES = new WeakHashMap<>();
    /** A vanilla-geometry model per EMF armour root; null when the vanilla tree is not humanoid. */
    private static final Map<EMFModelPartRoot, HumanoidModel<?>> FALLBACKS = new WeakHashMap<>();
    private static final Set<Model> FALLBACK_MODELS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<String> LOGGED = new HashSet<>();

    private ArmorFallback() {
    }

    /** Called as EMF puts a jem on a root. A jem with a texture of its own ignores the armour's. */
    public static void recordJem(EMFModelPartRoot root, int variant, int[] textureSize, boolean ownTexture) {
        int[] size = !ownTexture && textureSize != null && textureSize.length >= 2
                && textureSize[0] > 0 && textureSize[1] > 0 ? textureSize.clone() : null;
        JEM_SIZES.computeIfAbsent(root, r -> new HashMap<>()).put(variant, size);
        FALLBACKS.remove(root);
    }

    /** The texture layout a model was built for, or null when it is not one this addon knows. */
    public static int[] layoutOf(Model model) {
        if (FALLBACK_MODELS.contains(model)) return VANILLA_SIZE;
        EMFModelPartRoot root = emfRoot(model);
        return root == null ? null : jemSize(root);
    }

    /**
     * The model to draw one armour piece with: the given one, or the vanilla one when the piece's
     * texture was not drawn for the pack's layout.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Model choose(Model model, LivingEntity entity, EquipmentSlot slot) {
        if (!ArmorPacksEMFCompat.fallbackEnabled()) return model;
        EMFModelPartRoot root = emfRoot(model);
        if (root == null) return model;
        int[] layout = jemSize(root);
        if (layout == null) return model;

        ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem armor)) return model;
        List<ArmorMaterial.Layer> layers = armor.getMaterial().value().layers();
        if (layers.isEmpty()) return model;
        // The first layer decides for all: leather's overlay is drawn for the same layout as its base.
        ResourceLocation texture = ClientHooks.getArmorTexture(entity, stack, layers.get(0), slot == EquipmentSlot.LEGS, slot);
        int[] size = TextureSizes.of(texture);
        if (size == null || TextureSizes.sameShape(size, layout)) return model;

        HumanoidModel fallback = FALLBACKS.computeIfAbsent(root, ArmorFallback::buildFallback);
        if (fallback == null) return model;
        HumanoidModel posed = (HumanoidModel) model;
        // The pack's model already carries the wearer's pose and the slot's visibility.
        posed.copyPropertiesTo(fallback);
        copyVisibility(posed, fallback);

        if (LOGGED.add(root.modelName + "|" + texture)) {
            LOGGER.info("[ArmorPacks] {} ({}x{}) does not fit {} ({}x{}): drawn with the vanilla model",
                    texture, size[0], size[1], root.modelName, layout[0], layout[1]);
        }
        return fallback;
    }

    private static EMFModelPartRoot emfRoot(Model model) {
        if (!(model instanceof HumanoidModel<?> humanoid)) return null;
        return humanoid.head instanceof EMFModelPart part ? part.getRoot() : null;
    }

    private static int[] jemSize(EMFModelPartRoot root) {
        Map<Integer, int[]> byVariant = JEM_SIZES.get(root);
        return byVariant == null ? null : byVariant.get(root.currentModelVariant);
    }

    private static HumanoidModel<?> buildFallback(EMFModelPartRoot root) {
        try {
            HumanoidModel<?> model = new HumanoidModel<>(copyTree(root.vanillaRoot));
            FALLBACK_MODELS.add(model);
            return model;
        } catch (RuntimeException e) {
            // A vanilla tree without the humanoid parts: leave this model to the pack.
            LOGGER.warn("[ArmorPacks] no vanilla armour model for {}: {}", root.modelName, e.toString());
            return null;
        }
    }

    /** A copy of the vanilla tree, so posing it never disturbs the parts EMF keeps. */
    private static ModelPart copyTree(ModelPart part) {
        ModelPartAccessor access = (ModelPartAccessor) (Object) part;
        Map<String, ModelPart> children = new HashMap<>();
        access.emfCompat$getChildren().forEach((name, child) -> children.put(name, copyTree(child)));
        ModelPart copy = new ModelPart(access.emfCompat$getCubes(), children);
        copy.setInitialPose(part.getInitialPose());
        copy.loadPose(part.getInitialPose());
        return copy;
    }

    private static void copyVisibility(HumanoidModel<?> from, HumanoidModel<?> to) {
        to.head.visible = from.head.visible;
        to.hat.visible = from.hat.visible;
        to.body.visible = from.body.visible;
        to.rightArm.visible = from.rightArm.visible;
        to.leftArm.visible = from.leftArm.visible;
        to.rightLeg.visible = from.rightLeg.visible;
        to.leftLeg.visible = from.leftLeg.visible;
    }
}
