package strm.touchnmotion;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Touch'n Motion on Fabric 26.1.2: the frame for the port. Nothing of the mod is here yet but its
 * place in the settings - the tab and the switch for the whole of it - and its own entry in the
 * mod list (through Mod Menu, {@link ModMenuIntegration}).
 *
 * <p>The mod itself is in {@code extensions/touch_n_motion/neoforge/1.21.1}. To bring over, in
 * this order: the frame (the providers, the arbiter and the runtime under {@code interaction},
 * the hook after EMF's animation, the render mixin), then foot grounding and the torso's lean,
 * then one interaction at a time. The option keys stay as they are there, so a settings file
 * serves every version.</p>
 */
public class TouchNMotionClient implements ClientModInitializer {

    public static final String MOD_ID = "touch_n_motion";
    /** The mod's master option; the core's global switch covers it by its suffix. */
    public static final String KEY_ENABLED = "touchnmotion.enabled";
    private static final Logger LOGGER = LoggerFactory.getLogger("TouchNMotion");

    @Override
    public void onInitializeClient() {
        ConfigRegistry.section(MOD_ID, "Touch'n Motion").master(KEY_ENABLED, "Touch'n Motion", true,
                "On", "Every animation below works as it is set.",
                "Off", "Turn off every animation of this mod at once; the settings below are kept.");
        LOGGER.info("[TouchNMotion] the frame for Fabric 26.1.2 is loaded: the animations are not ported to this version yet");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }
}
