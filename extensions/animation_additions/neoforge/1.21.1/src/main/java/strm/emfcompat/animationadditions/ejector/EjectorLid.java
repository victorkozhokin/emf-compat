package strm.emfcompat.animationadditions.ejector;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.blockuse.ModAccess;

import java.lang.reflect.Method;

/**
 * Where the lid of Create's weighted ejector is drawn. The block's collision is the shut lid, but
 * thrown open the lid swings up and then comes down slowly as the spring is wound: a foot on it is
 * to stand on the lid as drawn, not on the box under it. For {@code FootGrounding}'s floor probes.
 *
 * <p>The lid as {@code EjectorRenderer} turns it: a plate, its top 13 px up, hinged 0.75 px in from
 * the side the block faces and 11.25 px up, the far end lifted by up to 70 degrees
 * ({@code getLidProgress}). Optional, by name.</p>
 */
public final class EjectorLid {

    private static final String BLOCK = "com.simibubi.create.content.logistics.depot.EjectorBlock";
    private static final double HINGE_IN = 0.75 / 16, HINGE_UP = 11.25 / 16, TOP_OVER_HINGE = 1.75 / 16;
    /** The plate, along itself from the hinge, blocks. */
    private static final double PLATE_FROM = 0.25 / 16, PLATE_TO = 14.25 / 16;
    private static final double OPEN = Math.toRadians(70);

    private static Method progress;
    private static boolean failed;

    private EjectorLid() {
    }

    /** A floor probe's hit, lifted onto the lid when it fell on an ejector whose lid is up; the hit itself otherwise. */
    public static Vec3 onLid(Level level, BlockHitResult hit) {
        Vec3 at = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        BlockState block = level.getBlockState(pos);
        if (failed || !ModAccess.is(block.getBlock().getClass(), BLOCK) || !block.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return at;
        double angle = progress(level, pos) * OPEN;
        if (angle < 1e-3) return at;
        Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Level, from the hinge towards the lid's far end: against the way the block faces.
        double fromFront = facing.getAxis() == Direction.Axis.X
                ? (facing.getStepX() > 0 ? pos.getX() + 1 - at.x : at.x - pos.getX())
                : (facing.getStepZ() > 0 ? pos.getZ() + 1 - at.z : at.z - pos.getZ());
        double level0 = fromFront - HINGE_IN;
        // The point of the plate's top over this spot: along the plate a, across it the top's height over the hinge.
        double along = (level0 + TOP_OVER_HINGE * Math.sin(angle)) / Math.cos(angle);
        if (along < PLATE_FROM || along > PLATE_TO) return at;
        double y = pos.getY() + HINGE_UP + along * Math.sin(angle) + TOP_OVER_HINGE * Math.cos(angle);
        return y > at.y ? new Vec3(at.x, y, at.z) : at;
    }

    private static double progress(Level level, BlockPos pos) {
        Object entity = level.getBlockEntity(pos);
        if (entity == null) return 0;
        try {
            if (progress == null) progress = entity.getClass().getMethod("getLidProgress", float.class);
            return (float) progress.invoke(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            return 0;
        }
    }
}
