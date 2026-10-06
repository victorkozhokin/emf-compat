package strm.emfcompat.hackersandslashers.compat;

import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.ExtraAnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads which of Hackers 'n Slashers' animation layers are currently playing on a player.
 *
 * <p>The mod drives everything through zigythebird's Player Animation library, registering one
 * layer per kind of movement, and exposes their ids as public constants on
 * {@code net.dndats.api.animations.PlayerAnimator}. The ids are rebuilt here rather than imported
 * so this addon compiles without the mod on the classpath — Hackers 'n Slashers is published
 * under "All rights reserved" and is on no Maven, so depending on its jar would mean a local file
 * that a fresh clone (or CI) does not have. Only the animation library, which is on a Maven, is
 * needed to compile.</p>
 *
 * <p>The ids are checked against 2.0-beta2.5 and 2.0-beta3 (the layers, the preset's {@code stances.onIdle}
 * and the pose controller's {@code currentPoseAnim} are the same in both). If a future version renames a layer the matching
 * capture simply stops firing — no crash, and the other layers keep working.</p>
 */
public final class HnSCompat {

    /** The mod's own id, and the namespace all of its animation layers live under. */
    public static final String MOD_ID = "hackersandslashers";

    private static final ResourceLocation ATTACK_LAYER = layer("attack_layer");
    private static final ResourceLocation DEFENSE_LAYER = layer("defense_layer");
    private static final ResourceLocation ACTION_LAYER = layer("action_layer");
    private static final ResourceLocation PARKOUR_LAYER = layer("parkour_layer");
    private static final ResourceLocation POSE_ACTION_LAYER = layer("pose_action_layer");
    private static final ResourceLocation POSE_LAYER = layer("pose_layer");
    private static final ResourceLocation FIRST_PERSON_POSE_LAYER = layer("first_person_pose_layer");

    /**
     * The layers that play a deliberate, finite movement — a swing, a block, a roll. These are the
     * ones worth holding against the resource pack, and they share one pose source so that
     * whichever is running wins the arms outright.
     */
    private static final ResourceLocation[] ACTION_LAYERS = {
            ATTACK_LAYER, DEFENSE_LAYER, ACTION_LAYER, PARKOUR_LAYER, POSE_ACTION_LAYER
    };

    private HnSCompat() {
    }

    private static ResourceLocation layer(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** {@code true} while any of the action layers is playing. */
    public static boolean isActionActive(AbstractClientPlayer player) {
        for (ResourceLocation layer : ACTION_LAYERS) {
            if (isLayerActive(player, layer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code true} while a swing or a block is playing - the moves that twist the torso while the
     * player keeps aiming, so the head should stay on the camera. Rolls, dashes and the other
     * parkour-like actions are left to the animation: there the whole body, head included, turns.
     */
    public static boolean isAimedActionActive(AbstractClientPlayer player) {
        return isLayerActive(player, ATTACK_LAYER) || isLayerActive(player, DEFENSE_LAYER);
    }

    /**
     * {@code true} while an action's animation is past its end tick and on its way back to rest -
     * the last few ticks of an attack, in which the mod brings the arms home.
     */
    public static boolean isReturning(AbstractClientPlayer player) {
        try {
            for (ResourceLocation layer : ACTION_LAYERS) {
                IAnimation animation = PlayerAnimationAccess.getPlayerAnimationLayer(player, layer);
                if (!(animation instanceof AnimationController controller) || !controller.isActive()) continue;
                Animation playing = controller.getCurrentAnimationInstance();
                if (playing == null) continue;
                Object end = playing.data().getNullable(ExtraAnimationData.END_TICK_KEY);
                if (end instanceof Number tick && controller.getAnimationTicks() >= tick.floatValue()) return true;
            }
        } catch (Throwable t) {
            // Fail-safe, as every render-time check here.
        }
        return false;
    }

    /**
     * {@code true} while H&amp;S wants Player Animation Library to draw the third-person player
     * model in the first-person pass.
     *
     * <p>Checking only whether an action layer is active is too broad. H&amp;S deliberately assigns
     * {@link FirstPersonMode#DISABLED} to actions such as rolls and dashes, while attacks and
     * blocks request {@link FirstPersonMode#THIRD_PERSON_MODEL}. Weapon stances use a separate
     * first-person layer. Reading the mode from every H&amp;S layer therefore follows the mod's own
     * decision and also keeps working during its fade-in and fade-out transitions.</p>
     */
    public static boolean isFirstPersonAnimationActive(AbstractClientPlayer player) {
        for (ResourceLocation layer : ACTION_LAYERS) {
            if (usesThirdPersonModelInFirstPerson(player, layer)) {
                return true;
            }
        }
        return usesThirdPersonModelInFirstPerson(player, POSE_LAYER)
                || usesThirdPersonModelInFirstPerson(player, FIRST_PERSON_POSE_LAYER);
    }

    /**
     * {@code true} while the stance layer is playing a real weapon stance — the way the mod holds a
     * player who is merely carrying a weapon, as opposed to swinging it. It lasts for as long as the
     * weapon is held, which is why it is captured separately and at a lower priority.
     *
     * <p>"The layer is active" is not enough. {@code DynamicAnimationHandler.updatePlayerPose} sets
     * its target to {@code hackersandslashers:idle} before it looks at the item at all, and keeps it
     * for anything without a weapon preset — a lantern, food, a block. That animation is an empty
     * placeholder with no bones, but it leaves the layer active, so capturing on activity alone froze
     * the arms in whatever pose they had for every item in the game. Two-handed items without a
     * preset fall back to {@code hns_greatsword_idle} instead, which is a real stance: the style is
     * guessed from the item's name, so a fishing rod ("rod", a staff word) gets the greatsword pose.</p>
     *
     * <p>So this asks the same question the mod's handler does — does the held item have a preset
     * with a stance — and only captures when it does.</p>
     */
    public static boolean isStanceActive(AbstractClientPlayer player) {
        if (!isLayerActive(player, POSE_LAYER)) {
            return false;
        }
        Boolean hasStance = heldItemHasStance(player);
        if (hasStance != null) {
            return hasStance;
        }
        // The preset API moved or went away: fall back to recognising the placeholder by name.
        ResourceLocation playing = currentPoseAnim(player);
        return playing == null || !IDLE_PLACEHOLDER.equals(playing);
    }

    /** The empty animation the stance layer holds when the item has no stance of its own. */
    private static final ResourceLocation IDLE_PLACEHOLDER = layer("idle");

    // Resolved once, reflectively: the mod is not on the compile classpath (see the class comment).
    private static boolean reflectionResolved;
    private static Method resolvePresetStats;
    private static Field presetStances;
    private static Field stancesOnIdle;
    private static Field currentPoseAnim;

    private static void resolveReflection() {
        if (reflectionResolved) {
            return;
        }
        reflectionResolved = true;
        try {
            Class<?> itemHelper = Class.forName("net.dndats.hackersandslashers.utils.helper.ItemHelper");
            resolvePresetStats = itemHelper.getMethod("resolvePresetStats", ItemStack.class);
            Class<?> preset = Class.forName("net.dndats.hackersandslashers.datapacks.models.WeaponStatOverride");
            presetStances = preset.getField("stances");
            stancesOnIdle = presetStances.getType().getField("onIdle");
        } catch (Throwable t) {
            resolvePresetStats = null;
        }
        try {
            currentPoseAnim = Class.forName("net.dndats.api.animations.HnSPlayerPoseController")
                    .getField("currentPoseAnim");
        } catch (Throwable t) {
            currentPoseAnim = null;
        }
    }

    /**
     * Whether the main-hand item has a stance preset, exactly as the mod's own pose handler decides
     * it; {@code null} if that could not be asked.
     */
    private static Boolean heldItemHasStance(AbstractClientPlayer player) {
        resolveReflection();
        if (resolvePresetStats == null) {
            return null;
        }
        try {
            Object preset = resolvePresetStats.invoke(null, player.getMainHandItem());
            if (preset == null) {
                return false;
            }
            Object stances = presetStances.get(preset);
            return stances != null && stancesOnIdle.get(stances) != null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The animation the stance layer is playing, or {@code null} if it cannot be read. */
    private static ResourceLocation currentPoseAnim(AbstractClientPlayer player) {
        resolveReflection();
        if (currentPoseAnim == null) {
            return null;
        }
        try {
            IAnimation animation = PlayerAnimationAccess.getPlayerAnimationLayer(player, POSE_LAYER);
            if (animation == null || !currentPoseAnim.getDeclaringClass().isInstance(animation)) {
                return null;
            }
            return (ResourceLocation) currentPoseAnim.get(animation);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isLayerActive(AbstractClientPlayer player, ResourceLocation layer) {
        try {
            IAnimation animation = PlayerAnimationAccess.getPlayerAnimationLayer(player, layer);
            return animation != null && animation.isActive();
        } catch (Throwable t) {
            // Nothing may escape a render-time check: EMF answers a Throwable out of animation
            // evaluation by disabling every animation on the model for the rest of the session.
            return false;
        }
    }

    private static boolean usesThirdPersonModelInFirstPerson(AbstractClientPlayer player,
                                                              ResourceLocation layer) {
        try {
            IAnimation animation = PlayerAnimationAccess.getPlayerAnimationLayer(player, layer);
            return animation != null
                    && animation.isActive()
                    && animation.getFirstPersonMode() == FirstPersonMode.THIRD_PERSON_MODEL;
        } catch (Throwable t) {
            // Keep this render-time compatibility check fail-safe for the same reason as the
            // ordinary layer check above: EMF disables model animations after an escaped error.
            return false;
        }
    }
}
