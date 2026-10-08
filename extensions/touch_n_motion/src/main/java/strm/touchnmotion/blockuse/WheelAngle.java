package strm.touchnmotion.blockuse;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Optional mod access, cached by concrete block entity class; one failure cannot disable another wheel. */
public final class WheelAngle {
    private final String name;
    private final Map<Class<?>, Method> methods = new HashMap<>();
    private final Set<Class<?>> failed = new HashSet<>();
    public WheelAngle(String name) { this.name = name; }

    public Float read(Level level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        if (entity == null || failed.contains(entity.getClass())) return null;
        try {
            Method method = methods.get(entity.getClass());
            if (method == null) {
                method = entity.getClass().getMethod(name, float.class);
                methods.put(entity.getClass(), method);
            }
            float angle = ((Number) method.invoke(entity,
                    Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))).floatValue();
            return Float.isFinite(angle) ? angle : null;
        } catch (ReflectiveOperationException | ClassCastException e) {
            failed.add(entity.getClass());
            LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read {} on {}", name, entity.getClass().getName(), e);
            return null;
        }
    }
}
