package strm.emfcompat.animationadditions.buttonpress.aeronautics;

import strm.emfcompat.animationadditions.blockuse.ModFailures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import strm.emfcompat.animationadditions.blockuse.ModBlock;
import strm.emfcompat.animationadditions.buttonpress.*;

/**
 * Create Aeronautics' physics assembler ({@code simulated}): a lever flicked over and held to
 * assemble or take apart. Optional: looked up by name, nothing when the mod is not there.
 *
 * <p>The knob is placed as {@code PhysicsAssemblerRenderer} draws the lever: turned to the block's
 * face and facing about the middle - {@code rotateCentered(horizontalAngle, UP)} then
 * {@code rotateCentered(0 / 90 / 180 for floor / wall / ceiling, EAST)}, as Create's analog lever -
 * and within that about the lever's pivot (8, 7, 8) px round east by {@code getRenderAngle}
 * (radians), so the hand goes over with it when it is flicked.</p>
 */
public final class PhysicsAssembler {

    private static final ModBlock BLOCK = ModBlock.exact("dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock", "simulated:physics_assembler");
    private static final String RENDERER = "dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerRenderer";
    private static final String ENTITY = "dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity";
    /** The middle of the knob in the lever's model, blocks; the lever's pivot. */
    private static final Vector3f KNOB = new Vector3f(0.5f, 16.5f / 16f, 0.5f);
    private static final Vector3f PIVOT = new Vector3f(0.5f, 7f / 16f, 0.5f);

    private static Method angle;
    private static boolean looked, failed;
    private static final ModFailures FAILURES = new ModFailures("place the physics assembler's knob");

    private PhysicsAssembler() {
    }

    public static boolean is(BlockState block) {
        return BLOCK.is(block);
    }

    /** Where the knob is drawn now, world; {@code null} when it cannot be told. */
    public static Vec3 knob(Level level, BlockPos pos, BlockState block) {
        Method m = FAILURES.off() ? null : method();
        if (m == null) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return null;
        try {
            float radians = (Float) m.invoke(null, entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            Vector3f at = place(block.getValue(FaceAttachedHorizontalDirectionalBlock.FACE),
                    block.getValue(FaceAttachedHorizontalDirectionalBlock.FACING), radians);
            // In double: on a sub-level the block is in a plot millions of blocks out, past a float's precision.
            return new Vec3(pos.getX() + (double) at.x, pos.getY() + (double) at.y, pos.getZ() + (double) at.z);
        } catch (Throwable t) {
            FAILURES.failed(t);
            return null;
        }
    }

    /** The knob in the block, blocks, for the lever turned {@code radians} from upright. */
    static Vector3f place(AttachFace face, Direction facing, float radians) {
        float tilt = face == AttachFace.FLOOR ? 0f : face == AttachFace.WALL ? 90f : 180f;
        float turn = facing.getAxis() == Direction.Axis.X ? -facing.toYRot() : facing.toYRot();
        Matrix4f m = new Matrix4f()
                .translate(0.5f, 0.5f, 0.5f).rotateY((float) Math.toRadians(turn)).translate(-0.5f, -0.5f, -0.5f)
                .translate(0.5f, 0.5f, 0.5f).rotateX((float) Math.toRadians(tilt)).translate(-0.5f, -0.5f, -0.5f)
                .translate(PIVOT).rotateX(radians).translate(new Vector3f(PIVOT).negate());
        return m.transformPosition(new Vector3f(KNOB));
    }

    private static Method method() {
        if (looked) return failed ? null : angle;
        looked = true;
        try {
            angle = Class.forName(RENDERER).getMethod("getRenderAngle", Class.forName(ENTITY), float.class);
        } catch (Throwable t) {
            failed = true;
        }
        return angle;
    }
}
