package strm.emfcompat.hackersandslashers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.hackersandslashers.compat.HnSCompat;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.utils.EMFEntity;

import java.lang.reflect.Proxy;

/**
 * Hackers 'n Slashers addon.
 *
 * <p>Built on the same idea as the Better Combat addon, because the two mods have the same shape
 * of problem: both replace vanilla combat and drive the player through an animation library, and
 * both lose to Entity Model Features, which re-animates the model from the resource pack
 * afterwards. Hackers 'n Slashers declares Better Combat incompatible, so the two addons are
 * never installed together and can each assume they own the arms.</p>
 *
 * <p>What differs is detection. Better Combat has to be asked about its attack state through
 * reflection into its internals; Hackers 'n Slashers registers named animation layers with
 * zigythebird's library and publishes their ids, so this addon just asks which ones are playing.
 * See {@link strm.emfcompat.hackersandslashers.compat.HnSCompat}.</p>
 *
 * <p>Nothing here lifts EMF's animation pause: the core does that for any player an addon has
 * captured a pose for, which covers this one from the moment it saves anything.</p>
 */
@Mod(EMFCompatHnSMod.MOD_ID)
public class EMFCompatHnSMod {

    public static final String MOD_ID = "emf_compat_hackers_and_slashers";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatHackersAndSlashers");

    public static final String KEY_ENABLED = "hackersandslashers.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "hackersandslashers.bodyFollowArms";
    public static final String KEY_ACTION_LEGS = "hackersandslashers.actionLegs";
    public static final String KEY_STANCES = "hackersandslashers.stances";
    public static final String KEY_HEAD_LOOK = "hackersandslashers.headLook";

    /** Pose source for attacks, blocks, rolls — anything with a beginning and an end. */
    public static final String SOURCE = "hackers_and_slashers";

    /**
     * Pose source for the weapon stance, which lasts as long as the weapon is held. Below the
     * action source so a swing takes the arms from the stance rather than blending with it, and
     * below the default 0 so a more specific addon still wins.
     */
    public static final String POSE_SOURCE = "hackers_and_slashers_pose";

    private static final int POSE_SOURCE_PRIORITY = -10;

    public EMFCompatHnSMod(IEventBus modEventBus, ModContainer modContainer) {
        PoseManager.setSourcePriority(POSE_SOURCE, POSE_SOURCE_PRIORITY);

        ConfigRegistry.section(MOD_ID, "Hackers 'n Slashers")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Keep Hackers 'n Slashers poses over EMF.",
                        "Off", "Leave every pose to EMF.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)", "Arm poses keep their shape and follow the moving torso.",
                        "Rotation-only (legacy)", "Arm poses keep only their rotation.")
                .addBoolean(KEY_ACTION_LEGS, "Action legs", true,
                        "On", "Hold the legs too while standing still, so a lunge or a roll keeps its stance.",
                        "Off", "Leave the legs to EMF (arms only).")
                .addBoolean(KEY_STANCES, "Weapon stances", false,
                        "On", "Hold the stance a carried weapon puts you in. Takes both arms for as long as the weapon is held.",
                        "Off", "Leave the stance to EMF — the pack's idle arm animation plays instead.")
                .addBoolean(KEY_HEAD_LOOK, "Head during attacks", true,
                        "Follows the camera", "The head keeps looking where you look while an attack or a block twists the body.",
                        "Follows the animation", "The head turns with the body, as the attack animation keys it.");
        modEventBus.addListener(this::clientSetup);
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }

    public static boolean isActionLegs() {
        return EMFCompatConfig.getBoolean(KEY_ACTION_LEGS, true);
    }

    public static boolean isStances() {
        return EMFCompatConfig.getBoolean(KEY_STANCES, false);
    }

    public static boolean isHeadLook() {
        return EMFCompatConfig.getBoolean(KEY_HEAD_LOOK, true);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                EMFAnimationApi.registerVanillaModelCondition(
                        EMFCompatHnSMod::shouldForceVanillaModelInFirstPerson);
                LOGGER.info("Registered EMF first-person vanilla-model condition for Hackers 'n Slashers");
                registerFirstPersonModelHandler();
            } catch (Exception e) {
                LOGGER.error("Failed to register EMF vanilla-model condition", e);
            }
        });
    }

    /**
     * Lets Player Animation Library render the animated vanilla arms and held items whenever
     * H&amp;S has explicitly enabled its full-model first-person pass. EMF's custom first-person
     * model otherwise replaces that pass and leaves the H&amp;S animation hidden.
     */
    private static boolean shouldForceVanillaModelInFirstPerson(EMFEntity entity) {
        if (!isEnabled()) {
            return false;
        }
        Entity mcEntity = (Entity) entity;
        if (!(mcEntity instanceof AbstractClientPlayer player) || !player.isLocalPlayer()) {
            return false;
        }
        if (!isLocalPlayerInFirstPerson(player)) {
            return false;
        }
        return HnSCompat.isFirstPersonAnimationActive(player);
    }

    /**
     * Registers through reflection so First Person Model remains an optional dependency. Its
     * activation API asks every handler whether the body render should be suppressed. While H&amp;S
     * owns the first-person pass, suppressing that second body avoids duplicate arms and items.
     */
    private static void registerFirstPersonModelHandler() {
        try {
            Class<?> api = Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI");
            Class<?> handlerType = Class.forName("dev.tr7zw.firstperson.api.ActivationHandler");
            Object handler = Proxy.newProxyInstance(
                    handlerType.getClassLoader(),
                    new Class<?>[]{handlerType},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "preventFirstperson" -> shouldSuppressFirstPersonModel();
                        case "toString" -> "EMF Compat: Hackers 'n Slashers activation handler";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
            api.getMethod("registerPlayerHandler", Object.class).invoke(null, handler);
            LOGGER.info("Registered First Person Model suppression for Hackers 'n Slashers animations");
        } catch (ClassNotFoundException e) {
            // Optional mod is absent.
        } catch (Throwable t) {
            LOGGER.error("Failed to register First Person Model compatibility", t);
        }
    }

    private static boolean shouldSuppressFirstPersonModel() {
        Minecraft mc = Minecraft.getInstance();
        return isEnabled()
                && mc.player != null
                && isLocalPlayerInFirstPerson(mc.player)
                && HnSCompat.isFirstPersonAnimationActive(mc.player);
    }

    public static boolean isLocalPlayerInFirstPerson(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        return player.isLocalPlayer()
                && mc.options.getCameraType().isFirstPerson()
                && mc.getCameraEntity() == player;
    }
}
