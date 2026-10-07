package strm.touchnmotion.blockuse.supplementaries;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import strm.touchnmotion.blockuse.*;

/**
 * Supplementaries' blackboard: looked at on its front, the hand is over the pixel of the board the
 * look is on; a pixel drawn or wiped, the hand is down on that very pixel, and stays down on the
 * board from pixel to pixel while the drawing goes on - it follows the picture as it is made.
 * Optional, by name.
 *
 * <p>Which pixel is told by the picture itself (the 16 by 16 of {@code getPixel}), not by the
 * look: drawn with a dye on the block, the pixel is the one looked at anyway; drawn in the board's
 * own screen the look does not move at all, and the screen puts each pixel into the block entity
 * at once on our own client - the hand goes over the board as the mouse does. Other clients are
 * sent the picture only when that screen is shut: many pixels at once are no stroke of a hand.</p>
 */
public final class Blackboard implements BlockTarget {

    private static final ModBlock BLOCK = ModBlock.exact("net.mehvahdjukaar.supplementaries.common.block.blocks.BlackboardBlock", "supplementaries:blackboard");
    private static final ModAccess DATA = new ModAccess("data");
    private static final int SIZE = 16;
    /** The board is this thick, pixels, at the back of its block: its face is this far from the back. */
    private static final double THICK = 5;
    /** More pixels than this changed at once is a picture sent whole, not drawn. */
    private static final int STROKE = 12;

    private static Method pixel;
    private static boolean failed;
    /** The last picture read, and its pixels: read again only when the picture is another. */
    private static Object lastData;
    private static byte[] lastPixels = new byte[0];

    /** The picture, pixel by pixel. */
    private record Seen(BlockState block, byte[] pixels) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Seen s && s.block == block && Arrays.equals(s.pixels, pixels);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(pixels);
        }
    }

    @Override
    public boolean matches(BlockState block) {
        return BLOCK.is(block);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Direction front = front(block);
        if (front == null || hit.getDirection() != front) return null;
        Vec3 at = hit.getLocation();
        // The middle of the pixel under the look.
        Direction.Axis depth = front.getAxis();
        return new Spot(new Vec3(depth == Direction.Axis.X ? at.x : snap(at.x), snap(at.y), depth == Direction.Axis.Z ? at.z : snap(at.z)),
                Vec3.atLowerCornerOf(front.getNormal()));
    }

    @Override
    public Object snapshot(Level level, BlockPos pos, BlockState block) {
        return new Seen(block, pixels(level.getBlockEntity(pos)));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now) || before.pixels.length != now.pixels.length) return null;
        Direction front = front(now.block);
        if (front == null) return null;
        int changed = 0, last = -1;
        for (int i = 0; i < now.pixels.length; i++) {
            if (now.pixels[i] != before.pixels[i]) {
                changed++;
                last = i;
            }
        }
        if (changed == 0 || changed > STROKE) return null;
        return new Gesture(at(pos, front, last / SIZE, last % SIZE), Motion.HOLD);
    }

    /**
     * The middle of pixel ({@code x}, {@code y}) on the board's face: {@code x} from the left as
     * the board is looked at, {@code y} from the top.
     */
    private static Spot at(BlockPos pos, Direction front, int x, int y) {
        Vec3 out = Vec3.atLowerCornerOf(front.getNormal());
        Vec3 right = Vec3.atLowerCornerOf(front.getCounterClockWise().getNormal());
        Vec3 point = Vec3.atCenterOf(pos)
                .add(out.scale((THICK - 8) / 16))
                .add(right.scale((x + 0.5) / SIZE - 0.5))
                .add(0, 0.5 - (y + 0.5) / SIZE, 0);
        return new Spot(point, out);
    }

    private static Direction front(BlockState block) {
        Property<?> facing = block.getBlock().getStateDefinition().getProperty("facing");
        return facing != null && block.getValue(facing) instanceof Direction d ? d : null;
    }

    private static double snap(double value) {
        return (Math.floor(value * SIZE) + 0.5) / SIZE;
    }

    /** The board's 256 pixels, x by y; the picture is an object replaced whole when a pixel changes. */
    private static byte[] pixels(BlockEntity entity) {
        if (entity == null || failed) return new byte[0];
        Object data = DATA.read(entity);
        if (data != null && data == lastData) return lastPixels;
        try {
            if (pixel == null) pixel = entity.getClass().getMethod("getPixel", int.class, int.class);
            byte[] pixels = new byte[SIZE * SIZE];
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) pixels[x * SIZE + y] = (byte) pixel.invoke(entity, x, y);
            }
            lastData = data;
            lastPixels = pixels;
            return pixels;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read the blackboard's pixels", e);
            return new byte[0];
        }
    }
}
