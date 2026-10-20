package strm.touchnmotion.interaction;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.touchnmotion.platform.Platform;

/**
 * Whether an entity is being drawn into the world now, not elsewhere - the inventory's portrait, a
 * mod's preview - where the draw events fire all the same: there the stack is the screen's, and a
 * move made in blocks of the world would throw the portrait about.
 */
public final class WorldDraw {

    private WorldDraw() {
    }

    /** Whether {@code stack} stands at {@code entity} as the world has it, from the camera. */
    public static boolean inWorld(Entity entity, PoseStack stack) {
        Vector3f at = stack.last().pose().getTranslation(new Vector3f());
        Vec3 expected = entity.getPosition(Platform.partialTick(false)).subtract(Platform.cameraPosition());
        // By length: before 1.20.5 the stack has the camera's turn in it as well. A block of slack: a ride may have
        // moved the stack a little already (the saddle), while a portrait stands hundreds of units out.
        return Math.abs(at.length() - expected.length()) < 1.0;
    }
}
