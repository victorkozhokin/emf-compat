package strm.emfcompat.horsesync;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import strm.emfcompat.horsesync.compat.EMFCompat;

@EventBusSubscriber(modid = EMFHorseSync.MOD_ID)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!Config.ENABLED.get()) return;
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
        if (!Config.ENABLED.get()) return;
        if (!(event.getEntity().getVehicle() instanceof AbstractHorse horse)) return;

        Float offset = EMFCompat.horseBodyOffsets.get(horse.getUUID());
        if (offset == null) return;

        float appliedOffset = -offset;
        if (appliedOffset <= 0.0f) return;

        event.getPoseStack().translate(0.0, -appliedOffset, 0.0);
    }
}
