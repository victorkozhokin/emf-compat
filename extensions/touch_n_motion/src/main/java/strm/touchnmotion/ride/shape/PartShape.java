package strm.touchnmotion.ride.shape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import strm.touchnmotion.mixin.ModelPartAccessor;
import strm.touchnmotion.ride.MountShape;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A mount measured from its model, when the model is made of the game's own {@link ModelPart}s - the
 * vanilla horses, a resource pack's EMF models, Icy's Better Horses and most mods' animals.
 *
 * <p>Every box is put into the model's space by adding up the pivots from the root (turns left out:
 * a resting body is near level). The barrel is the largest box; the hooves are the lowest narrow box
 * in each quarter around the middle - right/left by x, hind/front by z - all four or none.</p>
 */
public final class PartShape {

    /** Pixels: a hoof's box is no wider than this and at least this tall, its sole within this of the lowest. */
    private static final float HOOF_WIDEST = 7f, HOOF_SHORTEST = 3f, SOLE_TOLERANCE = 1.5f;
    private static final Map<Class<?>, List<Field>> PART_FIELDS = new IdentityHashMap<>();

    private PartShape() {
    }

    /** The model's measure, or {@code null} when it is not built of model parts or has no barrel. */
    public static MountShape measure(LivingEntity mount, Object model) {
        List<ModelPart> roots = roots(model);
        if (roots.isEmpty()) return null;
        List<float[]> boxes = new ArrayList<>();
        Set<ModelPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart root : roots) collect(root, 0f, 0f, 0f, boxes, seen);
        if (boxes.isEmpty()) return null;
        float[] barrel = null;
        float most = 0f;
        float lowest = -Float.MAX_VALUE;
        for (float[] b : boxes) {
            float volume = (b[3] - b[0]) * (b[4] - b[1]) * (b[5] - b[2]);
            if (volume > most) { most = volume; barrel = b; }
            lowest = Math.max(lowest, b[4]);
        }
        if (barrel == null) return null;
        float halfWidth = (barrel[3] - barrel[0]) / 2f;
        float middleX = (barrel[0] + barrel[3]) / 2f, middleZ = (barrel[2] + barrel[5]) / 2f;
        float[][] hooves = new float[4][];
        float[] soles = new float[4];
        for (float[] b : boxes) {
            if (b == barrel || b[4] < lowest - SOLE_TOLERANCE) continue;
            if (b[3] - b[0] > HOOF_WIDEST || b[5] - b[2] > HOOF_WIDEST || b[4] - b[1] < HOOF_SHORTEST) continue;
            float x = (b[0] + b[3]) / 2f, z = (b[2] + b[5]) / 2f;
            if (Math.abs(x - middleX) < 0.5f) continue;
            int quarter = (x < middleX ? 0 : 1) + (z > middleZ ? 0 : 2);
            if (hooves[quarter] == null || b[4] > soles[quarter]) {
                hooves[quarter] = new float[]{x, z};
                soles[quarter] = b[4];
            }
        }
        boolean four = hooves[0] != null && hooves[1] != null && hooves[2] != null && hooves[3] != null;
        float scale = strm.touchnmotion.platform.Platform.rendererScale(mount);
        return new MountShape(halfWidth, four ? hooves : null, lowest, scale, "parts");
    }

    /** The renderer's model for this mount; {@code null} for a renderer without one. */
    public static Object model(LivingEntity mount) {
        Object renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mount);
        //? if >=1.21.11 {
        /*return renderer instanceof LivingEntityRenderer<?, ?, ?> living ? living.getModel() : null;
        *///?} else {
        return renderer instanceof LivingEntityRenderer<?, ?> living ? living.getModel() : null;
        //?}
    }

    private static void collect(ModelPart part, float ox, float oy, float oz, List<float[]> boxes, Set<ModelPart> seen) {
        if (!seen.add(part)) return;
        float x = ox + part.x, y = oy + part.y, z = oz + part.z;
        for (ModelPart.Cube c : ((ModelPartAccessor) (Object) part).touchnmotion$cubes()) {
            boxes.add(new float[]{x + c.minX, y + c.minY, z + c.minZ, x + c.maxX, y + c.maxY, z + c.maxZ});
        }
        for (ModelPart child : ((ModelPartAccessor) (Object) part).touchnmotion$children().values()) collect(child, x, y, z, boxes, seen);
    }

    /** The model's top parts: its root where it has one, else its part fields that are no other part's child. */
    private static List<ModelPart> roots(Object model) {
        List<ModelPart> roots = new ArrayList<>();
        if (model == null) return roots;
        //? if >=1.21.11 {
        /*if (model instanceof net.minecraft.client.model.Model<?> m) { roots.add(m.root()); return roots; }
        *///?}
        List<ModelPart> fields = new ArrayList<>();
        for (Field f : partFields(model.getClass())) {
            try {
                if (f.get(model) instanceof ModelPart p) fields.add(p);
            } catch (IllegalAccessException ignored) {
            }
        }
        Set<ModelPart> children = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart p : fields) descendants(p, children);
        for (ModelPart p : fields) if (!children.contains(p) && !roots.contains(p)) roots.add(p);
        return roots;
    }

    private static void descendants(ModelPart part, Set<ModelPart> into) {
        for (ModelPart child : ((ModelPartAccessor) (Object) part).touchnmotion$children().values()) {
            if (into.add(child)) descendants(child, into);
        }
    }

    private static List<Field> partFields(Class<?> type) {
        return PART_FIELDS.computeIfAbsent(type, t -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || !ModelPart.class.isAssignableFrom(f.getType())) continue;
                    try {
                        f.setAccessible(true);
                        out.add(f);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return out;
        });
    }
}
