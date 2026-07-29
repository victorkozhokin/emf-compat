package strm.emfcompat.bettercombat;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

/**
 * Fabric client entry point for the Better Combat EMF compatibility addon.
 *
 * <p>EMF pauses its own animation whenever Player Animation Library is playing something, and
 * Better Combat 3.x drives its attacks through PAL — so during an attack EMF would freeze the
 * whole model, not just the arms we want to override. That pause is lifted selectively in
 * {@code EMFAnimationEntityContextMixin}, per entity and only while this addon actually has a
 * pose to restore. Earlier this was done by stripping EMF's pause listener at startup, which
 * lifted it globally and permanently — that also disabled the pause for unrelated PAL mods
 * (Take a Seat) and could not be undone by the config switch.</p>
 */
public class EMFCompatBetterCombatClient implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_better_combat";

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatBetterCombat");
    private static final String SOURCE = "better_combat";

    /** Master switch: apply EMF compatibility to Better Combat at all. */
    public static final String KEY_ENABLED = "bettercombat.enabled";
    /** Arm-sync mode: body-follow (new) vs the legacy rotation-only system. */
    public static final String KEY_BODY_FOLLOW_ARMS = "bettercombat.bodyFollowArms";
    /** Also restore the legs during an attack (experimental — keeps a step/lunge over EMF). */
    public static final String KEY_ATTACK_LEGS = "bettercombat.attackLegs";

    // NB: the NeoForge/Forge builds also carry a generic Player Animator capture for Spell Engine /
    // RPG Series casts. It is deliberately absent here — Spell Engine has no 1.21.11 release, so
    // the feature would have nothing to capture.

    /**
     * Pose source for Better Combat's weapon stances (spear, trident, claymore…), kept apart from
     * the attack source: a stance lasts for as long as the weapon is held, so it must lose the arms
     * to an actual attack, and it must not drag the first-person vanilla-model override along with
     * it for that whole time.
     */
    public static final String POSE_SOURCE = "better_combat_pose";

    @Override
    public void onInitializeClient() {
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
                        "Off", "Leave the legs to EMF (arms only).");

        // Force EMF to use the vanilla player model for the local player in first person while a
        // Better Combat attack is active. This prevents EMF player animation resource packs from
        // overriding the first-person arm pose and breaking the held item position.
        try {
            EMFAnimationApi.registerVanillaModelCondition(EMFCompatBetterCombatClient::shouldForceVanillaModelInFirstPerson);
            LOGGER.info("Registered EMF first-person vanilla-model condition for Better Combat");
        } catch (Exception e) {
            LOGGER.error("Failed to register EMF vanilla-model condition", e);
        }
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
        return AttackPauseOverride.isUnpaused(uuid) || PoseManager.getSavedPoses(uuid, SOURCE) != null;
    }
}
