package strm.emfcompat.animationadditions.blockuse;

import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A no-argument getter of an optional mod's object, by name; cached by class, a failure logged once and {@code null} from then on. */
final class ModAccess {

    private final String name;
    private final Map<Class<?>, Method> methods = new HashMap<>();
    private final Set<Class<?>> failed = new HashSet<>();

    ModAccess(String name) {
        this.name = name;
    }

    Object read(Object target) {
        if (target == null || failed.contains(target.getClass())) return null;
        try {
            Method method = methods.get(target.getClass());
            if (method == null) {
                method = target.getClass().getMethod(name);
                methods.put(target.getClass(), method);
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed.add(target.getClass());
            LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read {} on {}", name, target.getClass().getName(), e);
            return null;
        }
    }
}
