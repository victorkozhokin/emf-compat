package strm.emfcompat.horsesync.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//Temporary disabled. Prepare to delete
//import strm.emfcompat.horsesync.Config;
import strm.emfcompat.horsesync.compat.EMFCompat;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Mixin(EMFModelPartRoot.class)
public class EMFModelPartRootMixin {

    @Unique
    private static int emfhorsesync$cleanupCounter = 0;

    @Inject(method = "animate", at = @At("RETURN"))
    private void emfhorsesync$captureHorseBodyOffset(CallbackInfo ci) {

        //Temporary disabled. Prepare to delete
        //        if (!Config.ENABLED.get()) {
        //            EMFCompat.horseBodyOffsets.clear();
        //            return;
        //        }

        if (++emfhorsesync$cleanupCounter % 200 == 0) {
            var mc = Minecraft.getInstance();
            if (mc.level != null) {
                var activeHorses = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                        .filter(e -> e instanceof AbstractHorse)
                        .map(Entity::getUUID)
                        .collect(Collectors.toSet());
                EMFCompat.horseBodyOffsets.keySet().retainAll(activeHorses);
            }
        }

        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) return;
        if (!(state.emfEntity() instanceof Entity entity)) return;
        if (!(entity instanceof AbstractHorse horse)) return;

        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;
        if (!root.isMainModel) return;
        if (EMFAnimationEntityContext.isLayerPhase()) return;

        EMFModelPartVanilla bodyPart = null;
        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            if ("[vanilla part body]".equals(part.toStringShort())) {
                bodyPart = part;
                break;
            }
        }
        if (bodyPart == null) return;

        float animatedY = bodyPart.y;
        float baseY = bodyPart.getInitialPose().y;
        float deltaPixels = animatedY - baseY;

        float scale = 1.0f;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(horse);
        if (renderer instanceof AbstractHorseRendererAccessor accessor) {
            scale = accessor.emfhorsesync$getScale();
        }

        float offsetBlocks = (deltaPixels / 16.0f) * scale;
        EMFCompat.horseBodyOffsets.put(horse.getUUID(), offsetBlocks);
    }
}
