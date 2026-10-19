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
            // Where the pack keeps the body at rest is its own, not the game's - Fresh Animations has the pivot 5 pixels
            // forward and 1.6 up from it, its cubes with it - and it says nothing of it: it is taken from where the body is
            // while the horse is on the ground, evened out over seconds. Only the body moving away from that is the
            // saddle moving: over a jump, up, back and turned.
            long now = System.nanoTime();
            Rest rest = REST.computeIfAbsent(horse.getUUID(), k -> new Rest(bodyPart.y, bodyPart.z, now));
            float dt = Math.min(0.1f, (now - rest.at) / 1e9f);
            rest.at = now;
            if (horse.onGround()) {
                float k = 1f - (float) Math.exp(-dt / REST_SECONDS);
                rest.y += (bodyPart.y - rest.y) * k;
                rest.z += (bodyPart.z - rest.z) * k;
            }
            EMFCompat.horseSaddles.put(horse.getUUID(), new EMFCompat.Saddle(rest.y, rest.z, bodyPart.y, bodyPart.z, bodyPart.xRot, scale));
        } catch (Throwable t) {
            // A throw out of an EMF hook makes EMF drop the model's animations until resources reload.
            emfcompat$failed(t);
        }
    }

    /** A horse's body at rest as the pack has it: the pivot's y and z, and when it was last seen. */
    private static final class Rest {
        float y, z;
        long at;

        Rest(float y, float z, long at) {
            this.y = y;
            this.z = z;
            this.at = at;
        }
    }

    private static final java.util.Map<java.util.UUID, Rest> REST = strm.touchnmotion.interaction.EntityStates.alsoClear(new java.util.HashMap<>());
    /** Seconds the rest is evened out over: long against a stride, short against settling after a reload. */
    private static final float REST_SECONDS = 2f;

    private static boolean emfcompat$failureLogged;

    private static void emfcompat$failed(Throwable t) {
        if (emfcompat$failureLogged) return;
        emfcompat$failureLogged = true;
        org.slf4j.LoggerFactory.getLogger("emf_compat").warn("[EMF Compat] {} failed; skipped so EMF keeps animating the model",
                HorseSyncAnimationHook.class.getSimpleName(), t);
    }
}
