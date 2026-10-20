package strm.touchnmotion.horsesync;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import strm.touchnmotion.horsesync.compat.EMFCompat;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * The rider on an animated horse. The tick and the draw are told by the loader's own part:
 * by events on NeoForge, on Fabric by its tick and {@code mixin/fabric/legacy/PlayerRenderEventsMixin}.
 */
public class ClientEventHandler {

    private static int cleanupCounter = 0;
    /** Blocks the game moves a rearing horse's rider back and up, at full rear. */
    private static final double REAR_BACK = 0.7, REAR_UP = 0.15;

    public static void onClientTick() {
        if (!HorseSync.isEnabled()) {
            EMFCompat.horseSaddles.clear();
            HorseSyncAnimationHook.retain(java.util.Set.of());
            return;
        }

        if (++cleanupCounter % 200 != 0) return;

        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            EMFCompat.horseSaddles.clear();
            HorseSyncAnimationHook.retain(java.util.Set.of());
            return;
        }

        var activeHorses = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e instanceof AbstractHorse)
                .map(Entity::getUUID)
                .collect(Collectors.toSet());
        EMFCompat.horseSaddles.keySet().retainAll(activeHorses);
        HorseSyncAnimationHook.retain(activeHorses);
    }

    /** The saddle the rider is on this frame; {@code null} for none - not on a horse, or the pack does not move its body. */
    public static EMFCompat.Saddle saddle(Entity player) {
        if (!HorseSync.isEnabled()) return null;
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return null;
        return EMFCompat.horseSaddles.get(horse.getUUID());
    }

    /** Radians the saddle is turned nose up from level, as the pack turns the horse's body; 0 off one. */
    public static float noseUp(Entity player) {
        EMFCompat.Saddle saddle = saddle(player);
        return saddle == null ? 0f : -saddle.pitch();
    }

    /**
     * Carries the rider with the horse's body as the pack moves it - up, down, along, and turned about the
     * body's pivot - as a rider sits a saddle: the stack is at the rider, world-aligned, before the rider is drawn.
     * Whether it pushed a pose on the stack (see {@link strm.touchnmotion.stages.BodyStages#RIDER}).
     */
    public static boolean carryRider(Entity player, PoseStack stack, float partialTick) {
        EMFCompat.Saddle saddle = saddle(player);
        if (saddle == null || !(player.getVehicle() instanceof AbstractHorse horse)
                || !strm.touchnmotion.interaction.WorldDraw.inWorld(player, stack)) return false;
        stack.pushPose();
        float partial = strm.touchnmotion.platform.Platform.partialTick(false);
        double yaw = Math.toRadians(net.minecraft.util.Mth.rotLerp(partial, horse.yBodyRotO, horse.yBodyRot));
        Vec3 along = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        // Rearing - and a horse rears to jump - the game moves its rider back and up, for the body turned up about its
        // hind end; the pack turns the body itself, and the rider is carried with that below, so the game's move is undone.
        float stand = horse.getStandAnim(partial);
        Vec3 undo = along.scale(REAR_BACK * stand).add(0, -REAR_UP * stand, 0);
        stack.translate(undo.x, undo.y, undo.z);
        Vec3 horseAt = horse.getPosition(partial).subtract(player.getPosition(partial)).subtract(undo);
        float s = saddle.scale();
        // The model's pixels in the world: y down from 1.501 blocks up, z towards the tail.
        Vec3 pivot = horseAt.add(0, s * (1.501 - saddle.y0() / 16.0), 0).subtract(along.scale(s * saddle.z0() / 16.0));
        Vec3 moved = horseAt.add(0, s * (1.501 - saddle.y() / 16.0), 0).subtract(along.scale(s * saddle.z() / 16.0));
        stack.translate(moved.x, moved.y, moved.z);
        // Nose up is a turn about the horse's right that brings its front up.
        strm.touchnmotion.platform.Platform.rotate(stack, new Quaternionf().rotationAxis(-saddle.pitch(), (float) right.x, 0f, (float) right.z));
        stack.translate(-pivot.x, -pivot.y, -pivot.z);
        return true;
    }
}
