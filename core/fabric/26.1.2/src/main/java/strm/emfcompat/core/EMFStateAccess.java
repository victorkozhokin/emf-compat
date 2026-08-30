package strm.emfcompat.core;

import traben.entity_model_features.models.animation.state.EMFEntityRenderState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the render state EMF is currently animating, across both EMF generations.
 *
 * <p>EMF 3.3 moved the state off {@code EMFAnimationEntityContext} onto {@code EMFState}. The old
 * {@code getEmfState()} is simply gone, so a build that calls it directly dies mid-frame with a
 * {@link NoSuchMethodError} the moment a player is drawn. Both entry points are static, take no
 * arguments and return the same {@link EMFEntityRenderState}, so the one that exists is resolved
 * once into a {@link MethodHandle} and the rest of the core never has to know which EMF is
 * installed.</p>
 *
 * <p>A handle rather than reflection because this sits on the per-model-part render path: after
 * the JIT sees the constant field it costs the same as a direct call, while
 * {@code Method.invoke} would box and check on every part of every entity.</p>
 */
public final class EMFStateAccess {

    /** EMF 3.3+: the state lives here. */
    private static final String EMF_STATE = "traben.entity_model_features.models.animation.state.EMFState";
    /** EMF 3.2.x and older: the state was reached through the animation context. */
    private static final String EMF_CONTEXT = "traben.entity_model_features.models.animation.EMFAnimationEntityContext";

    private EMFStateAccess() {
    }

    /**
     * Holds the resolved accessor, or {@code null} when neither entry point is present.
     *
     * <p>Resolved lazily through a holder class so class initialisation provides the thread
     * safety, and so the lookup happens on the first render rather than during mod construction.</p>
     */
    private static final class Handle {
        static final MethodHandle STATE = resolve();

        private static MethodHandle resolve() {
            MethodType signature = MethodType.methodType(EMFEntityRenderState.class);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                return lookup.findStatic(Class.forName(EMF_STATE), "state", signature);
            } catch (Throwable ignored) {
                // Not EMF 3.3+; fall through to the older entry point.
            }
            try {
                return lookup.findStatic(Class.forName(EMF_CONTEXT), "getEmfState", signature);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /**
     * The state EMF is animating right now, or {@code null} when there is none — which also
     * covers EMF being absent or having moved the accessor again.
     */
    public static EMFEntityRenderState current() {
        MethodHandle state = Handle.STATE;
        if (state == null) {
            return null;
        }
        try {
            return (EMFEntityRenderState) state.invokeExact();
        } catch (Throwable t) {
            return null;
        }
    }

    /** EMF 3.3+ moved the explicit per-entity pause set to its own handler. */
    private static final String EMF_PAUSE_HANDLER = "traben.entity_model_features.utils.EMFAnimationPauseHandler";

    /**
     * Holds a getter for EMF's set of entities another mod has explicitly paused, wherever that
     * set currently lives. Read rarely - only once a pause is already in effect - so this one
     * goes through {@code invoke} rather than an exact call.
     */
    private static final class PausedEntities {
        static final MethodHandle GETTER = resolve();

        private static MethodHandle resolve() {
            for (String owner : new String[]{EMF_PAUSE_HANDLER, EMF_CONTEXT}) {
                try {
                    return MethodHandles.lookup().findStaticGetter(Class.forName(owner), "entitiesPaused", Set.class);
                } catch (Throwable ignored) {
                    // Try the other owner.
                }
            }
            return null;
        }
    }

    /**
     * Whether some mod explicitly asked EMF to pause this entity, as opposed to a pause coming
     * from a global condition. Addons use it to avoid lifting a pause that was meant to hold.
     */
    public static boolean isPausedExplicitly(UUID uuid) {
        MethodHandle getter = PausedEntities.GETTER;
        if (getter == null || uuid == null) {
            return false;
        }
        try {
            Object paused = getter.invoke();
            return paused instanceof Set<?> set && set.contains(uuid);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Holds EMF 3.2.x's arm-attachment override setters. EMF caches where a held item attaches to
     * the arm; once the core overwrites an arm pose that cache is stale, and clearing it makes EMF
     * recompute the attachment so the item stays in the hand.
     *
     * <p>EMF 3.3 dropped these setters while reworking attachment points, and offers nothing to
     * clear in their place, so on 3.3 this is a no-op. The parameter type is only ever named
     * reflectively: it is being renamed upstream, and the core must keep compiling against both.</p>
     */
    private static final class ArmOverrides {
        static final MethodHandle LEFT = resolve("setLeftArmOverride");
        static final MethodHandle RIGHT = resolve("setRightArmOverride");

        private static MethodHandle resolve(String name) {
            try {
                Class<?> attachment = Class.forName("traben.entity_model_features.models.animation.EMFAttachments");
                return MethodHandles.lookup().findVirtual(EMFEntityRenderState.class, name,
                        MethodType.methodType(void.class, attachment));
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /**
     * Clears EMF's cached arm attachment for one arm, where that concept still exists.
     * Call it after overwriting an arm pose.
     */
    public static void clearArmOverride(EMFEntityRenderState state, boolean leftArm) {
        MethodHandle setter = leftArm ? ArmOverrides.LEFT : ArmOverrides.RIGHT;
        if (setter == null || state == null) {
            return;
        }
        try {
            setter.invoke(state, null);
        } catch (Throwable ignored) {
            // An arm attachment that cannot be cleared is a cosmetic issue, never a crash.
        }
    }
}
