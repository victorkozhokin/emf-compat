package strm.emfcompat.parcool;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.parcool.compat.ParCoolPose;

/**
 * ParCool extension entry point. All rendering work is done by the mixins: one capture path
 * per ParCool animation system (3.4.x and 4.x, picked by {@code EMFCompatParCoolMixinPlugin}),
 * both writing into the core {@code PoseManager}, which restores them onto the EMF model.
 */
@Mod(EMFCompatParCoolMod.MOD_ID)
public class EMFCompatParCoolMod {

    public static final String MOD_ID = "emf_compat_parcool";

    /** Master switch: apply EMF compatibility to ParCool at all. */
    public static final String KEY_ENABLED = "parcool.enabled";
    /** Whether the head and torso are held too, or left playing the resource pack's animation. */
    public static final String KEY_WHOLE_POSE = "parcool.wholePose";

    public EMFCompatParCoolMod(IEventBus modEventBus) {
        ConfigRegistry.section(MOD_ID, "ParCool")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to ParCool (vaults, wall runs, rolls, climbing).",
                        "Off", "Disable all EMF compatibility for ParCool (plain ParCool behaviour).")
                .addBoolean(KEY_WHOLE_POSE, "Pose scope", true,
                        "Whole pose",
                        "Hold every part ParCool animates, head and torso included.",
                        "Limbs only",
                        "Hold arms and legs only - the head and torso keep the resource pack's animation.");

        PoseManager.setSourcePriority(ParCoolPose.SOURCE, ParCoolPose.SOURCE_PRIORITY);
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isWholePose() {
        return EMFCompatConfig.getBoolean(KEY_WHOLE_POSE, true);
    }
}
