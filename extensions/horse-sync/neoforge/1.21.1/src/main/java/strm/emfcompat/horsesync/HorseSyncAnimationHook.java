package strm.emfcompat.horsesync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import strm.emfcompat.horsesync.compat.EMFCompat;
import strm.emfcompat.horsesync.mixin.AbstractHorseRendererAccessor;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Records how far a resource pack has moved the horse's body, so the rider can be moved with it.
 *
 * <p>Reads the pose through EMF's animation hook, which fires once per rendered entity right
 * after the pack animation has been applied — where the addon's mixin on
 * {@code EMFModelPartRoot#animate} used to sit.</p>
 */
public final class HorseSyncAnimationHook extends EMFAnimationApi.EMFAnimationHook {

    private static int cleanupCounter = 0;

    private HorseSyncAnimationHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new HorseSyncAnimationHook());
        } catch (Throwable t) {
            System.err.println("[EMF Compat: Horse Sync] could not register the EMF animation hook: " + t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        // The hook runs once per rendered entity, so this counter now measures entity renders
        // rather than model parts - roughly two hundred of them between sweeps.
        if (++cleanupCounter % 200 == 0) {
            var mc = Minecraft.getInstance();
            if (mc.level != null) {
                var activeHorses = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                        .filter(e -> e instanceof AbstractHorse)
                        .map(Entity::getUUID)
                        .collect(Collectors.toSet());
                EMFCompat.horseBodyOffsets.keySet().retainAll(activeHorses);
            }
        }

        EMFEntityRenderState state = context.activeState();
        if (state == null) return;
        if (!(state.emfEntity() instanceof Entity entity)) return;
        if (!(entity instanceof AbstractHorse horse)) return;

        if (!EMFHorseSync.isEnabled()) {
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
        float baseY = bodyPart.getInitialPose().y;
        float bodyTy = animatedY - baseY;

        float scale = 1.0f;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(horse);
        if (renderer instanceof AbstractHorseRendererAccessor accessor) {
            scale = accessor.emfhorsesync$getScale();
        }

        // Model pixels -> world blocks (16 px per block), scaled by the horse renderer's scale.
        float offsetBlocks = (bodyTy / 16.0f) * scale;
        EMFCompat.horseBodyOffsets.put(horse.getUUID(), offsetBlocks);
    }
}
