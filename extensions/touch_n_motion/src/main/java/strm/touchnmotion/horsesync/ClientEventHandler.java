package strm.touchnmotion.horsesync;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import strm.touchnmotion.horsesync.compat.EMFCompat;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * The rider on an animated horse. The tick and the draw are told by the loader's own part:
 * by events on NeoForge, on Fabric by its tick and {@code mixin/fabric/PlayerRenderEventsMixin}.
 */
public class ClientEventHandler {

    private static int cleanupCounter = 0;

    public static void onClientTick() {
        if (!HorseSync.isEnabled()) {
            EMFCompat.horseBodyOffsets.clear();
            return;
        }

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

    /** How far up the rider is drawn; zero for none. Inverted and clamped: the horse's body going down in model space is up in the world. */
    private static float offset(Entity player) {
        if (!HorseSync.isEnabled()) return 0f;
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return 0f;
        Float offset = EMFCompat.horseBodyOffsets.get(horse.getUUID());
        return offset == null ? 0f : Math.max(0f, -offset);
    }

    public static void onRenderPlayerPre(Entity player, PoseStack stack) {
        float up = offset(player);
        if (up > 0f) stack.translate(0.0, up, 0.0);
    }

    public static void onRenderPlayerPost(Entity player, PoseStack stack) {
        float up = offset(player);
        if (up > 0f) stack.translate(0.0, -up, 0.0);
    }
}
