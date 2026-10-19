package strm.touchnmotion.horsesync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.horsesync.compat.EMFCompat;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;


/**
 * Records how a resource pack has moved and turned the horse's body, so the rider can be carried with it.
 *
 * <p>Reads the pose through EMF's animation hook, which fires once per rendered entity right
 * after the pack animation has been applied — where the addon's mixin on
 * {@code EMFModelPartRoot#animate} used to sit.</p>
 */
public final class HorseSyncAnimationHook extends EMFAnimationApi.EMFAnimationHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("emf_compat");

    private HorseSyncAnimationHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new HorseSyncAnimationHook());
        } catch (Throwable t) {
            LOGGER.warn("[EMF Compat: Horse Sync] could not register the EMF animation hook", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        try {
            EMFEntityRenderState state = context.activeState();
            if (state == null) return;
            if (!(state.emfEntity() instanceof Entity entity)) return;
            if (!(entity instanceof AbstractHorse horse)) return;

            if (!HorseSync.isEnabled()) {
                EMFCompat.horseSaddles.remove(horse.getUUID());
                return;
            }

            EMFModelPartRoot root = context.animatingModelRoot();
            if (!root.isMainModel) return;
            if (EMFState.isLayerPhase) return;

            EMFModelPartVanilla bodyPart = root.getAllVanillaPartsByNameEMF().get("body");
            if (bodyPart == null) return;

            // The body bone as the pack has animated it: EMF puts its translations on top of the model's own pose.
            float scale = strm.touchnmotion.platform.Platform.horseScale(horse);
            EMFCompat.horseSaddles.put(horse.getUUID(), new EMFCompat.Saddle(
                    strm.touchnmotion.platform.Platform.y(bodyPart.getInitialPose()), strm.touchnmotion.platform.Platform.z(bodyPart.getInitialPose()),
                    bodyPart.y, bodyPart.z, bodyPart.xRot, scale));
        } catch (Throwable t) {
            // A throw out of an EMF hook makes EMF drop the model's animations until resources reload.
            emfcompat$failed(t);
        }
    }

    private static boolean emfcompat$failureLogged;

    private static void emfcompat$failed(Throwable t) {
        if (emfcompat$failureLogged) return;
        emfcompat$failureLogged = true;
        org.slf4j.LoggerFactory.getLogger("emf_compat").warn("[EMF Compat] {} failed; skipped so EMF keeps animating the model",
                HorseSyncAnimationHook.class.getSimpleName(), t);
    }
}
