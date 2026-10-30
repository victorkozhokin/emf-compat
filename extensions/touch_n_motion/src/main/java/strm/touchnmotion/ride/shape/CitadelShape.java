package strm.touchnmotion.ride.shape;

import net.minecraft.world.entity.LivingEntity;
import strm.touchnmotion.ride.MountShape;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Mounts drawn by Citadel's models - Ice and Fire's dragons, hippogryphs and the like, Alex's Mobs - whose parts
 * are Citadel's {@code AdvancedModelBox}, not the game's model parts. Read by name (Citadel's names are its own,
 * not remapped): each part's pivot ({@code rotationPointX/Y/Z}), its boxes ({@code cubeList}, corners
 * {@code posX1..posZ2}) and its children ({@code childModels}), from the parts no other part holds.
 *
 * <p>Such mounts are drawn larger as they grow, by the renderer: the scale is the renderer's own
 * ({@code Platform.rendererScale}). No hooves - a dragon is not stood on its feet here.</p>
 */
public final class CitadelShape {

    private static final class Api {
        static final Class<?> MODEL = find("com.github.alexthe666.citadel.client.model.AdvancedEntityModel");
        static Method allParts;
        static Field pivotX, pivotY, pivotZ, cubes, children;
        static Field x1, y1, z1, x2, y2, z2;
        static boolean ok;

        static {
            try {
                if (MODEL != null) {
                    allParts = MODEL.getMethod("getAllParts");
                    Class<?> part = find("com.github.alexthe666.citadel.client.model.basic.BasicModelPart");
                    Class<?> box = find("com.github.alexthe666.citadel.client.model.AdvancedModelBox");
                    Class<?> cube = find("com.github.alexthe666.citadel.client.model.TabulaModelRenderUtils$ModelBox");
                    pivotX = part.getField("rotationPointX");
                    pivotY = part.getField("rotationPointY");
                    pivotZ = part.getField("rotationPointZ");
                    cubes = box.getField("cubeList");
                    children = box.getField("childModels");
                    x1 = cube.getField("posX1"); y1 = cube.getField("posY1"); z1 = cube.getField("posZ1");
                    x2 = cube.getField("posX2"); y2 = cube.getField("posY2"); z2 = cube.getField("posZ2");
                    ok = true;
                }
            } catch (Throwable t) {
                ok = false;
            }
        }

        private static Class<?> find(String name) {
            try {
                return Class.forName(name);
            } catch (Throwable absent) {
                return null;
            }
        }
    }

    private CitadelShape() {
    }

    /** A {@code MountShapes} adapter: the measure of a Citadel model, {@code null} for any other. */
    public static MountShape measure(LivingEntity mount, Object model) {
        if (!Api.ok || model == null || !Api.MODEL.isInstance(model)) return null;
        try {
            List<Object> all = new ArrayList<>();
            for (Object p : (Iterable<?>) Api.allParts.invoke(model)) all.add(p);
            Set<Object> held = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object p : all) for (Object c : (Iterable<?>) Api.children.get(p)) held.add(c);
            List<float[]> boxes = new ArrayList<>();
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object p : all) if (!held.contains(p)) collect(p, 0f, 0f, 0f, boxes, seen);
            if (boxes.isEmpty()) return null;
            float[] barrel = null;
            float most = 0f;
            for (float[] b : boxes) {
                float volume = (b[3] - b[0]) * (b[4] - b[1]) * (b[5] - b[2]);
                if (volume > most) { most = volume; barrel = b; }
            }
            float scale = strm.touchnmotion.platform.Platform.rendererScale(mount);
            return new MountShape((barrel[3] - barrel[0]) / 2f, null, 24f, scale, "citadel");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void collect(Object part, float ox, float oy, float oz, List<float[]> boxes, Set<Object> seen) throws IllegalAccessException {
        if (!seen.add(part)) return;
        float x = ox + Api.pivotX.getFloat(part), y = oy + Api.pivotY.getFloat(part), z = oz + Api.pivotZ.getFloat(part);
        Object list = Api.cubes.get(part);
        if (list instanceof Iterable<?> cubes) {
            for (Object c : cubes) {
                float ax = Api.x1.getFloat(c), ay = Api.y1.getFloat(c), az = Api.z1.getFloat(c);
                float bx = Api.x2.getFloat(c), by = Api.y2.getFloat(c), bz = Api.z2.getFloat(c);
                boxes.add(new float[]{x + Math.min(ax, bx), y + Math.min(ay, by), z + Math.min(az, bz),
                        x + Math.max(ax, bx), y + Math.max(ay, by), z + Math.max(az, bz)});
            }
        }
        Object kids = Api.children.get(part);
        if (kids instanceof Iterable<?> list2) for (Object c : list2) if (Api.cubes.getDeclaringClass().isInstance(c)) collect(c, x, y, z, boxes, seen);
    }
}
