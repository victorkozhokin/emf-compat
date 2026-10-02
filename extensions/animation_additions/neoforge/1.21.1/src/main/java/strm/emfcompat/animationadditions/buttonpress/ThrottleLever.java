package strm.emfcompat.animationadditions.buttonpress;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Create Aeronautics' throttle lever ({@code simulated}): a lever the player holds and drags, its
 * handle turning through 80 degrees. Optional: looked up by name, nothing when the mod is not
 * there.
 *
 * <p>The knob is placed with the mod's own transform of the handle
 * ({@code ThrottleLeverRenderer.transformHandleExternal}), so the hand is on it wherever the
 * handle is drawn, and goes along as it is dragged.</p>
 */
public final class ThrottleLever {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatButtonPress");
    private static final String BLOCK = "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlock";
    private static final String RENDERER = "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverRenderer";
    private static final String ENTITY = "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity";
    /** The middle of the knob in the handle's model, blocks. */
    private static final Vector3f KNOB = new Vector3f(0.5f, 18.2f / 16f, 0.5f);

    private static Method transform;
    private static boolean looked, failed;

    private ThrottleLever() {
    }

    public static boolean is(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    /** Where the knob is drawn now, world; {@code null} when it cannot be told. */
    public static Vec3 knob(Level level, BlockPos pos) {
        Method m = method();
        if (m == null) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return null;
        try {
            PoseStack stack = new PoseStack();
            m.invoke(null, entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), stack);
            Vector3f at = stack.last().pose().transformPosition(new Vector3f(KNOB));
            // The block's own corner added in double: a pose stack is float, off by whole blocks in a sub-level's plot.
            return new Vec3(pos.getX() + (double) at.x, pos.getY() + (double) at.y, pos.getZ() + (double) at.z);
        } catch (Throwable t) {
            if (!failed) LOGGER.warn("[ButtonPress] could not place the throttle lever's knob", t);
            failed = true;
            return null;
        }
    }

    /** Actual held lever; looking away during a drag must not release the visual grip. */
    public static BlockPos heldPosition() {
        try {
            Object handler=Class.forName("dev.simulated_team.simulated.index.SimClickInteractions")
                    .getField("THROTTLE_LEVER_MANAGER").get(null);
            if (!(boolean)handler.getClass().getMethod("isActive").invoke(handler)) return null;
            return (BlockPos)handler.getClass().getMethod("getInteractionPos").invoke(handler);
        } catch (ReflectiveOperationException | ClassCastException e) { return null; }
    }

    private static Method method() {
        if (looked) return failed ? null : transform;
        looked = true;
        try {
            Class<?> renderer = Class.forName(RENDERER);
            Class<?> entity = Class.forName(ENTITY);
            transform = renderer.getMethod("transformHandleExternal", entity, float.class, PoseStack.class);
        } catch (Throwable t) {
            failed = true;
        }
        return transform;
    }
}
