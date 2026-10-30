package strm.touchnmotion.ride;

import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.DebugLog;
import strm.touchnmotion.ride.shape.HitboxShape;
import strm.touchnmotion.ride.shape.PartShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

/**
 * The measure of a mount, once per model: a mod's own adapter first (for a model not made of the
 * game's parts), then the model's parts, then the hitbox. Kept by the model object, so a resource
 * reload that builds new models measures again.
 */
public final class MountShapes {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatMount");
    /** Adapters for mods whose models are not built of model parts: (mount, model) to a measure or {@code null}. */
    public static final List<BiFunction<LivingEntity, Object, MountShape>> ADAPTERS = new ArrayList<>(List.of(
            strm.touchnmotion.ride.shape.CitadelShape::measure));
    private static final Map<Object, MountShape> BY_MODEL = new WeakHashMap<>();
    private static final Map<Object, MountShape> BY_TYPE = new WeakHashMap<>();
    private static boolean failed;

    private MountShapes() {
    }

    public static MountShape of(LivingEntity mount) {
        Object model = failed ? null : safeModel(mount);
        Map<Object, MountShape> cache = model != null ? BY_MODEL : BY_TYPE;
        Object key = model != null ? model : mount.getType();
        MountShape shape = cache.get(key);
        if (shape == null) {
            shape = plausible(mount, measure(mount, model));
            cache.put(key, shape);
            if (DebugLog.decisions()) {
                LOGGER.info("[MountShape] {} {} halfWidth={} riderHalfWidth={} scale={} hooves={} ground={}",
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()), shape.source(),
                        DebugLog.r2(shape.halfWidth()), DebugLog.r2(shape.riderHalfWidth()), DebugLog.r2(shape.scale()),
                        shape.hooves() == null ? "none" : java.util.Arrays.deepToString(shape.hooves()), DebugLog.r2(shape.ground()));
            }
        }
        return shape;
    }

    /** Model pixels: a model's soles stand on its ground, y 24 (the renderer puts that on the floor); within this of it. */
    private static final float GROUND = 24f, GROUND_TOLERANCE = 2f;

    /**
     * Hooves that do not reach the model's ground are not hooves - a resource pack's EMF model keeps its legs as
     * boxes hung off parts this measure does not turn, and its lowest boxes end at the hips. A vanilla horse, donkey
     * or mule then keeps the vanilla legs (packs keep where they stand); any other mount stands on no hooves.
     */
    private static MountShape plausible(LivingEntity mount, MountShape shape) {
        if (shape.hooves() != null && Math.abs(shape.ground() - GROUND) <= GROUND_TOLERANCE) return shape;
        boolean vanilla = mount instanceof net.minecraft.world.entity.animal.horse.AbstractHorse
                && !(mount instanceof net.minecraft.world.entity.animal.camel.Camel)
                && !(mount instanceof net.minecraft.world.entity.animal.horse.Llama)
                && !strm.touchnmotion.compat.IcysHorses.isBreed(mount)
                && "minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()).getNamespace());
        float[][] hooves = vanilla ? MountShape.VANILLA_HORSE.hooves() : null;
        return new MountShape(shape.halfWidth(), hooves, GROUND, shape.scale(), shape.source() + (vanilla ? "+vanilla-hooves" : "+no-hooves"));
    }

    private static MountShape measure(LivingEntity mount, Object model) {
        try {
            for (var adapter : ADAPTERS) {
                MountShape shape = adapter.apply(mount, model);
                if (shape != null) return shape;
            }
            MountShape parts = model == null ? null : PartShape.measure(mount, model);
            if (parts != null) return parts;
        } catch (Throwable t) {
            if (!failed) LOGGER.warn("[MountShape] measuring {} failed; going by hitboxes from now on", mount.getType(), t);
            failed = true;
        }
        return HitboxShape.measure(mount);
    }

    private static Object safeModel(LivingEntity mount) {
        try {
            return PartShape.model(mount);
        } catch (Throwable t) {
            return null;
        }
    }
}
