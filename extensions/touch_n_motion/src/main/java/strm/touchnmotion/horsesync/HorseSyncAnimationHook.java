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
 * Records how far a resource pack has moved the horse's body, so the rider can be moved with it.
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
        EMFEntityRenderState state = context.activeState();
        if (state == null) return;
        if (!(state.emfEntity() instanceof Entity entity)) return;
        if (!(entity instanceof AbstractHorse horse)) return;

        if (!HorseSync.isEnabled()) {
            EMFCompat.horseBodyOffsets.remove(horse.getUUID());
            return;
        }

        EMFModelPartRoot root = context.animatingModelRoot();
        if (!root.isMainModel) return;
        if (EMFState.isLayerPhase) return;

        EMFModelPartVanilla bodyPart = root.getAllVanillaPartsByNameEMF().get("body");
        if (bodyPart == null) return;

        // The value we inherit is the horse's CEM body.ty (the body bone's animated Y translation).
        // EMF applies ty on top of the initial pose, so ModelPart.y - initialPose.y == body.ty.
        float animatedY = bodyPart.y;
        float baseY = strm.touchnmotion.platform.Platform.y(bodyPart.getInitialPose());
        float bodyTy = animatedY - baseY;

        float scale = strm.touchnmotion.platform.Platform.horseScale(horse);

        // Model pixels -> world blocks (16 px per block), scaled by the horse renderer's scale.
        float offsetBlocks = (bodyTy / 16.0f) * scale;
        EMFCompat.horseBodyOffsets.put(horse.getUUID(), offsetBlocks);
    }
}
