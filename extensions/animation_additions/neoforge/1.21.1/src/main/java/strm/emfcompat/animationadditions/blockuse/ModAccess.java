package strm.emfcompat.animationadditions.blockuse;

import org.slf4j.LoggerFactory;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A value of an optional mod's object, by name: a no-argument method, or else a field, of the
 * object's class or one it extends, public or not. Found once for a class; a failure is logged
 * once and {@code null} from then on.
 *
 * <p>A class that names another mod's types in its members (a ComputerCraft peripheral) cannot be
 * looked through without that mod - a {@code LinkageError}: the class it extends is tried next.</p>
 */
public final class ModAccess {

    private final String name;
    private final Map<Class<?>, Optional<AccessibleObject>> members = new HashMap<>();

    public ModAccess(String name) {
        this.name = name;
    }

    public Object read(Object target) {
        if (target == null) return null;
        Optional<AccessibleObject> member = members.computeIfAbsent(target.getClass(), this::find);
        if (member.isEmpty()) return null;
        try {
            return member.get() instanceof Method method ? method.invoke(target) : ((Field) member.get()).get(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            members.put(target.getClass(), Optional.empty());
            LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read {} on {}", name, target.getClass().getName(), e);
            return null;
        }
    }

    private Optional<AccessibleObject> find(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return Optional.empty();
    }

    /** Whether {@code type} is, extends or implements the class named. */
    public static boolean is(Class<?> type, String className) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) return true;
            for (Class<?> i : c.getInterfaces()) if (is(i, className)) return true;
        }
        return false;
    }
}
