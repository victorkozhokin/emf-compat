package strm.emfcompat.animationadditions.buttonpress;

import strm.emfcompat.animationadditions.blockuse.ModFailures;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

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

    private static final String BLOCK = "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlock";
    private static final String RENDERER = "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverRenderer";
    private static final String ENTITY = "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity";
    /** The middle of the knob in the handle's model, blocks. */
    private static final Vector3f KNOB = new Vector3f(0.5f, 18.2f / 16f, 0.5f);

    private static Method transform;
    private static boolean looked, failed;
    private static final ModFailures FAILURES = new ModFailures("place the throttle lever's knob");

    private ThrottleLever() {
    }

    public static boolean is(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    /** Where the knob is drawn now, world; {@code null} when it cannot be told. */
    public static Vec3 knob(Level level, BlockPos pos) {
        Vec3[] grips=grips(level,pos);
        return grips==null ? null : grips[0].add(grips[1]).scale(.5);
    }

    /** Two separated material points across the real handle, transformed by its renderer. */
    public static Vec3[] grips(Level level,BlockPos pos) {
        Method m=FAILURES.off() ? null : method();BlockEntity entity=level.getBlockEntity(pos);
        if(m==null || entity==null || !is(level.getBlockState(pos))) return null;
        try {
            PoseStack stack=new PoseStack();
            m.invoke(null,entity,Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),stack);
            Vec3[] result=new Vec3[2];
            for(int i=0;i<2;i++) {
                Vector3f at=stack.last().pose().transformPosition(new Vector3f(KNOB).add(i==0 ? -.08f : .08f,0,0));
                result[i]=new Vec3(pos.getX()+(double)at.x,pos.getY()+(double)at.y,pos.getZ()+(double)at.z);
            }
            return result;
        } catch(Throwable t) {
            FAILURES.failed(t);
            return null;
        }
    }

    /** Read the actual 0..15 signal, without inventing resistance or changing control input. */
    public static Integer signal(Level level,BlockPos pos) {
        BlockEntity entity=level.getBlockEntity(pos);
        if(entity==null || !is(level.getBlockState(pos)))return null;
        try {return (Integer)entity.getClass().getMethod("getState").invoke(entity);}
        catch(ReflectiveOperationException | ClassCastException e) {return null;}
    }

    /** Actual held lever; looking away during a drag must not release the visual grip. */
    public static BlockPos heldPosition() {
        if (heldAbsent) return null;
        try {
            // Asked every frame of our own player: the class, the field and the methods are found once.
            if (heldManager == null) {
                try {
                    heldManager = Class.forName("dev.simulated_team.simulated.index.SimClickInteractions")
                            .getField("THROTTLE_LEVER_MANAGER");
                } catch (ReflectiveOperationException | LinkageError missing) {
                    heldAbsent = true;
                    return null;
                }
            }
            Object handler = heldManager.get(null);
            if (heldActive == null || heldActive.getDeclaringClass() != handler.getClass() && !heldActive.getDeclaringClass().isInstance(handler)) {
                heldActive = handler.getClass().getMethod("isActive");
                heldPos = handler.getClass().getMethod("getInteractionPos");
            }
            if (!(boolean) heldActive.invoke(handler)) return null;
            return (BlockPos) heldPos.invoke(handler);
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    private static java.lang.reflect.Field heldManager;
    private static Method heldActive, heldPos;
    private static boolean heldAbsent;

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
