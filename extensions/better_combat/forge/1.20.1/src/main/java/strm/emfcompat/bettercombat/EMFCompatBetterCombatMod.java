package strm.emfcompat.bettercombat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.utils.EMFEntity;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;

import java.util.UUID;

@Mod(EMFCompatBetterCombatMod.MOD_ID)
public class EMFCompatBetterCombatMod {
    public static final String MOD_ID = "emf_compat_better_combat";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatBetterCombat");
    private static final String SOURCE = "better_combat";

    /** Master switch: apply EMF compatibility to Better Combat at all. */
    public static final String KEY_ENABLED = "bettercombat.enabled";
    /** Arm-sync mode: body-follow (new) vs the legacy rotation-only system. */
    public static final String KEY_BODY_FOLLOW_ARMS = "bettercombat.bodyFollowArms";
    /** Also restore the legs during an attack (experimental — keeps a step/lunge over EMF). */
    public static final String KEY_ATTACK_LEGS = "bettercombat.attackLegs";
    /**
     * Keep Better Combat's weapon stances (spear, trident, claymore…) over EMF. Off by default:
     * unlike an attack this is a permanent override of both arms for as long as the weapon is
     * held, and on this loader nothing is broken without it — Better Combat drives the stance
     * through kosmx Player Animator, which EMF does not pause for, so EMF just animates over it.
     */
    public static final String KEY_WEAPON_STANCES = "bettercombat.weaponStances";

    /**
     * Pose source for the weapon stances, kept apart from the attack source: a stance lasts for as
     * long as the weapon is held, so it must lose the arms to an actual attack, and it must not
     * drag the first-person vanilla-model override along with it for that whole time.
     */
    public static final String POSE_SOURCE = "better_combat_pose";

    public EMFCompatBetterCombatMod() {
        // Below the attack capture (default 0) so an attack takes the arms from the stance.
        PoseManager.setSourcePriority(POSE_SOURCE, -10);

        ConfigRegistry.section(MOD_ID, "Better Combat")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Better Combat (attack arm poses, torso-tilt fixes).",
                        "Off", "Disable all EMF compatibility for Better Combat (plain Better Combat behaviour).")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)",
                        "Attack arm poses keep their exact shape and follow the moving torso.",
                        "Rotation-only (legacy)",
                        "Attack arm poses keep only their rotation.")
                .addBoolean(KEY_ATTACK_LEGS, "Attack legs", true,
                        "On", "Restore the legs during attacks while standing still (rotation only, stays attached); moving keeps EMF's walk cycle.",
                        "Off", "Leave the legs to EMF (arms only).")
                .addBoolean(KEY_WEAPON_STANCES, "Weapon stances", false,
                        "On", "Keep Better Combat's two-handed weapon stances (spear, trident, claymore...) over EMF. Overrides both arms for as long as the weapon is held.",
                        "Off", "Leave the stance to EMF — the resource pack's idle arm animation plays instead.");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::clientSetup);
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }

    public static boolean isAttackLegs() {
        return EMFCompatConfig.getBoolean(KEY_ATTACK_LEGS, true);
    }

    public static boolean isWeaponStances() {
        return EMFCompatConfig.getBoolean(KEY_WEAPON_STANCES, false);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                EMFAnimationApi.registerVanillaModelCondition(EMFCompatBetterCombatMod::shouldForceVanillaModelInFirstPerson);
                LOGGER.info("Registered EMF first-person vanilla-model condition for Better Combat");
            } catch (Exception e) {
                LOGGER.error("Failed to register EMF vanilla-model condition", e);
            }
        });
    }

    private static boolean shouldForceVanillaModelInFirstPerson(EMFEntity entity) {
        if (!isEnabled()) {
            return false;
        }
        Entity mcEntity = (Entity) entity;
        if (!(mcEntity instanceof Player player)) {
            return false;
        }
        if (!player.isLocalPlayer()) {
            return false;
        }
        UUID uuid = player.getUUID();
        if (uuid == null) {
            return false;
        }
        if (!EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            return false;
        }
        return AttackPauseOverride.isUnpaused(uuid) || PoseManager.getSavedPoses(player.getUUID(), SOURCE) != null;
    }
}
