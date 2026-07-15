package strm.emfcompat.horsesync;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import strm.emfcompat.horsesync.compat.EMFCompat;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@EventBusSubscriber(modid = EMFHorseSync.MOD_ID)
public class ClientEventHandler {

    private static int cleanupCounter = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        //Temporary disabled. Prepare to delete

        //        if (!Config.ENABLED.get()) {
        //            EMFCompat.horseBodyOffsets.clear();
        //            return;
        //        }

        if (++cleanupCounter % 200 != 0) return;

        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            EMFCompat.horseBodyOffsets.clear();
            return;
        }

        var activeHorses = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e instanceof AbstractHorse)
                .map(Entity::getUUID)
                .collect(Collectors.toSet());
        EMFCompat.horseBodyOffsets.keySet().retainAll(activeHorses);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        //Temporary disabled. Prepare to delete
        //        if (!Config.ENABLED.get()) return;
        if (!(event.getEntity().getVehicle() instanceof AbstractHorse horse)) return;

        Float offset = EMFCompat.horseBodyOffsets.get(horse.getUUID());
        if (offset == null) return;

        // Invert and clamp: move the player up when the horse body goes down in model space
        // (which translates to the body going up in world space after scale(-1, -1, 1)).
        float appliedOffset = -offset;
        if (appliedOffset <= 0.0f) return;

        event.getPoseStack().translate(0.0, appliedOffset, 0.0);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        //Temporary disabled. Prepare to delete
        //        if (!Config.ENABLED.get()) return;
        if (!(event.getEntity().getVehicle() instanceof AbstractHorse horse)) return;

        Float offset = EMFCompat.horseBodyOffsets.get(horse.getUUID());
        if (offset == null) return;

        float appliedOffset = -offset;
        if (appliedOffset <= 0.0f) return;

        event.getPoseStack().translate(0.0, -appliedOffset, 0.0);
    }
}
