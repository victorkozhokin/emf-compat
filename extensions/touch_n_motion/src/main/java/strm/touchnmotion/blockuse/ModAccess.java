package strm.touchnmotion.blockuse;

import org.slf4j.LoggerFactory;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A value of an optional mod's object, by name: a no-argument method, or else a field, of the
 * object's class or one it extends, public or not - or a method taking the arguments given
 * ({@link #call}). Found once for a class; a failure is logged once and {@code null} from then on.
 *
 * <p>A class that names another mod's types in its members (a ComputerCraft peripheral) cannot be
 * looked through without that mod - a {@code LinkageError}: the class it extends is tried next.</p>
 */
public final class ModAccess {

    private final String name;
    /** The method's parameters; {@code null} for a no-argument method or a field. */
    private final Class<?>[] parameters;
    private final Map<Class<?>, Optional<AccessibleObject>> members = new HashMap<>();

    public ModAccess(String name) {
        this.name = name;
        this.parameters = null;
    }

    /** A method taking these parameters, called with {@link #call}. */
    public ModAccess(String name, Class<?>... parameters) {
        this.name = name;
        this.parameters = parameters;
    }

    public Object read(Object target) {
        return call(target);
    }

    /** The method called on {@code target} with {@code arguments}, or the field read; {@code null} when there is none. */
    public Object call(Object target, Object... arguments) {
        if (target == null) return null;
        Optional<AccessibleObject> member = members.computeIfAbsent(target.getClass(), this::find);
        if (member.isEmpty()) return null;
        try {
            return member.get() instanceof Method method ? method.invoke(target, arguments) : ((Field) member.get()).get(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            members.put(target.getClass(), Optional.empty());
            LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read {} on {}", name, target.getClass().getName(), e);
            return null;
        }
    }

    private Optional<AccessibleObject> find(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name, parameters == null ? new Class<?>[0] : parameters);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            if (parameters != null) continue;
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
        return IS.computeIfAbsent(className, k -> new HashMap<>()).computeIfAbsent(type, t -> walk(t, className));
    }

    /** Asked for every block a foot's ray lands on: the walk up the class is done once for each. */
    private static final Map<String, Map<Class<?>, Boolean>> IS = new HashMap<>();

    private static boolean walk(Class<?> type, String className) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) return true;
            for (Class<?> i : c.getInterfaces()) if (walk(i, className)) return true;
        }
        return false;
    }
}
