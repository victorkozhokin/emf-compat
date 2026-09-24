package strm.emfcompat.animationadditions.footgrounding;

import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGroundingHook;

/**
 * Foot IK (experimental).
 *
 * <p>Minecraft stands the player's hitbox on the highest block under it, so on the edge of a step
 * or a slab one foot stands on it and the other hangs in the air above the lower floor. This looks
 * under each foot, lowers the model onto the lower floor and raises the leg on the step so its
 * foot stays planted. See {@link FootGrounding}.</p>
 *
 * <p>Pack legs have no knee (FA+Player: {@code left_leg}/{@code right_leg} are one bone each), so
 * the leg on the step only pitches forward a little and its hip moves up into the torso.</p>
 */
public final class FootGroundingFeature {

    public static final String KEY_ENABLED = "footgrounding.enabled";

    private FootGroundingFeature() {
    }

    /** Client only: the hook is an EMF animation hook. */
    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Foot IK (experimental)", true,
                "On", "On uneven ground (steps, slabs) lower the body and raise the leg on the step, so both feet stand.",
                "Off", "Leave the legs to EMF; one foot may hang in the air.");
        FootGroundingHook.register();
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }
}
