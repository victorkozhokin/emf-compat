package strm.emfcompat.animationadditions.ejector;

import strm.emfcompat.animationadditions.blockuse.ModFailures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.blockuse.ModAccess;

import java.lang.reflect.Method;
import strm.emfcompat.animationadditions.blockuse.ModBlock;

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

    private static final ModBlock BLOCK = ModBlock.family("com.simibubi.create.content.logistics.depot.EjectorBlock", "create:weighted_ejector");
    private static final double HINGE_IN = 0.75 / 16, HINGE_UP = 11.25 / 16, TOP_OVER_HINGE = 1.75 / 16;
    /** The plate, along itself from the hinge, blocks. */
    private static final double PLATE_FROM = 0.25 / 16, PLATE_TO = 14.25 / 16;
    private static final double OPEN = Math.toRadians(70);

    private static Method progress;
    private static final ModFailures FAILURES = new ModFailures("read the ejector's lid");

    private EjectorLid() {
    }

    /** A floor probe's hit, lifted onto the lid when it fell on an ejector beside the one who stands, its lid up; the hit itself otherwise. */
    public static Vec3 onLid(Entity standing, BlockHitResult hit) {
        Vec3 at = hit.getLocation();
        // The ejector stood on is not a step beside the feet: the whole body goes up onto its lid ({@link #over}).
        if (hit.getBlockPos().equals(BlockPos.containing(standing.getX(), standing.getY() - 0.01, standing.getZ()))) return at;
        double y = topAt(standing.level(), hit.getBlockPos(), at.x, at.z);
        return y > at.y ? new Vec3(at.x, y, at.z) : at;
    }

    /**
     * How far over {@code y} the lid is at this spot, blocks, for whoever stands on the ejector
     * there; 0 with no ejector under the spot, its lid shut, or the lid not over it.
     */
    public static double over(Level level, double x, double y, double z) {
        double top = topAt(level, BlockPos.containing(x, y - 0.01, z), x, z);
        return top > y ? top - y : 0;
    }

    /** The world y of the lid's top over a spot of the ejector at {@code pos}; NaN when it is no ejector, its lid is shut or not over the spot. */
    private static double topAt(Level level, BlockPos pos, double x, double z) {
        BlockState block = level.getBlockState(pos);
        if (FAILURES.off() || !BLOCK.is(block) || !block.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return Double.NaN;
        double angle = progress(level, pos) * OPEN;
        if (angle < 1e-3) return Double.NaN;
        Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Level, from the hinge towards the lid's far end: against the way the block faces.
        double fromFront = facing.getAxis() == Direction.Axis.X
                ? (facing.getStepX() > 0 ? pos.getX() + 1 - x : x - pos.getX())
 : (facing.getStepZ() > 0 ? pos.getZ() + 1 - z : z - pos.getZ());
        // The point of the plate's top over this spot: along the plate, and the top's height over the hinge across it.
        double along = (fromFront - HINGE_IN + TOP_OVER_HINGE * Math.sin(angle)) / Math.cos(angle);
        if (along < PLATE_FROM || along > PLATE_TO) return Double.NaN;
        return pos.getY() + HINGE_UP + along * Math.sin(angle) + TOP_OVER_HINGE * Math.cos(angle);
    }

    private static double progress(Level level, BlockPos pos) {
        Object entity = level.getBlockEntity(pos);
        if (entity == null) return 0;
        try {
            if (progress == null) progress = entity.getClass().getMethod("getLidProgress", float.class);
            return (float) progress.invoke(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            FAILURES.failed(e);
            return 0;
        }
    }
}
