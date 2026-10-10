package strm.touchnmotion.compat;

import org.joml.Matrix4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A shader mod (Iris, Oculus) draws every entity once more, for the shadows, as the sun sees it:
 * the pose stack it is drawn on then starts from the sun's view and not from the world moved to
 * the camera, which is what everything here reads a stack as. {@link #view} is that view, to be
 * taken off the stack and put back.
 *
 * <p>Looked up by name, once: none of it is there without a shader mod, and the view is not in
 * the mod's public interface.</p>
 */
public final class ShadowPass {
    private ShadowPass() {
    }

    private static final String[] PACKAGES = {"net.irisshaders.iris", "net.coderbot.iris"};
    private static MethodHandle drawing, view;
    private static boolean looked;

    /** Whether the shadows are being drawn right now. */
    public static boolean drawing() {
        look();
        if (drawing == null) return false;
        try {
            return (boolean) drawing.invokeExact();
        } catch (Throwable failure) {
            drawing = null;
            return false;
        }
    }

    /** The view the shadows are drawn from, under everything on an entity's stack; {@code null} when it cannot be had. */
    public static Matrix4f view() {
        if (view == null) return null;
        try {
            return (Matrix4f) view.invokeExact();
        } catch (Throwable failure) {
            view = null;
            return null;
        }
    }

    private static void look() {
        if (looked) return;
        looked = true;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (String name : PACKAGES) {
            try {
                drawing = lookup.findStatic(Class.forName(name + ".shadows.ShadowRenderingState"),
                        "areShadowsCurrentlyBeingRendered", MethodType.methodType(boolean.class));
                view = lookup.findStaticGetter(Class.forName(name + ".shadows.ShadowRenderer"), "MODELVIEW", Matrix4f.class);
                return;
            } catch (ReflectiveOperationException | LinkageError absent) {
                // no shader mod, or this one is under the other name
            }
        }
    }
}
