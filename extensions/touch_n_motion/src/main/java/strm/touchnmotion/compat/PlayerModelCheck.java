package strm.touchnmotion.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.DebugLog;
import traben.entity_model_features.EMFAnimationApi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * This mod only works while EMF animates the player's model, and when it does not, nothing says
 * so: the player just stands as vanilla poses them. With the decisions log on, it is said once in
 * chat and in the log, with the likely cause:
 *
 * <ul>
 *   <li>the player model is switched off in EMF's settings ({@code modelsNamesDisabled}) -
 *   Smooth Player Animations writes it there while its CEM Animation Engine is on;</li>
 *   <li>EMF has a model for the player but no animation on it - no player pack, or EMF dropped
 *   the animations after something threw out of them ("Disabling all animations for model").</li>
 * </ul>
 *
 * EMF's settings are only read, never written.
 */
public final class PlayerModelCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger("TouchNMotion");
    private static final int EVERY_TICKS = 100;
    private static final String[] PLAYER_MODELS = {"player", "player_slim"};

    private static int ticks;
    private static String said;
    private static Method config, getConfig;
    private static Field disabled;
    private static boolean settingsUnreadable;

    private PlayerModelCheck() {
    }

    public static void tick() {
        if (!DebugLog.decisions()) {
            said = null;
            return;
        }
        if (++ticks < EVERY_TICKS) return;
        ticks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        String problem;
        try {
            problem = problem(minecraft, player);
        } catch (Throwable t) {
            return;
        }
        if (problem == null || problem.equals(said)) {
            if (problem == null) said = null;
            return;
        }
        said = problem;
        LOGGER.warn("[TouchNMotion] {}", problem);
        //? if >=1.21.11 && <26.1 {
        /*player.displayClientMessage(Component.literal("[Touch'n Motion] " + problem), false);
        *///?} else {
        player.sendSystemMessage(Component.literal("[Touch'n Motion] " + problem));
        //?}
    }

    private static String problem(Minecraft minecraft, LocalPlayer player) {
        String off = switchedOff();
        if (off != null) {
            return "EMF's '" + off + "' model is switched off in its settings, so this mod does nothing."
                    + " Smooth Player Animations does this while its CEM Animation Engine is on.";
        }
        if (!(minecraft.getEntityRenderDispatcher().getRenderer(player) instanceof RenderLayerParent<?, ?> renderer)) return null;
        if (!EMFAnimationApi.isModelCustomizedByEMF(renderer.getModel())) {
            return "EMF has no model for the player: no player model pack is on, so this mod does nothing.";
        }
        if (!EMFAnimationApi.isModelAnimatedByEMF(renderer.getModel())) {
            return "EMF is not animating the player's model, so this mod does nothing. The pack has no animation"
                    + " for it, or EMF dropped its animations after an error - look for 'Disabling all animations' in the log.";
        }
        return null;
    }

    /** The player model EMF's settings switch off, or {@code null}. */
    private static String switchedOff() {
        if (settingsUnreadable) return null;
        try {
            if (disabled == null) {
                config = Class.forName("traben.entity_model_features.EMF").getMethod("config");
                Object handler = config.invoke(null);
                getConfig = handler.getClass().getMethod("getConfig");
                disabled = getConfig.invoke(handler).getClass().getField("modelsNamesDisabled");
            }
            Object names = disabled.get(getConfig.invoke(config.invoke(null)));
            if (!(names instanceof Collection<?> collection)) return null;
            for (String model : PLAYER_MODELS) if (collection.contains(model)) return model;
            return null;
        } catch (Throwable t) {
            settingsUnreadable = true;
            return null;
        }
    }
}
