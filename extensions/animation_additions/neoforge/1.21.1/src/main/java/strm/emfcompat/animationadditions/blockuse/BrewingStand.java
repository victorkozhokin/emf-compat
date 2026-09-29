package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A brewing stand: the hand goes to the bottle place nearest where it is looked at - one with a
 * bottle in it, or an empty one with a bottle or a potion in the hand - and when a bottle goes in
 * or comes out of a place (through the stand's screen, as the game has it), the hand puts it on
 * that place or takes it off.
 *
 * <p>The places, from the model ({@code brewing_stand_bottle0..2}): one east of the rod, the other
 * two turned 45 degrees off west, to the north-west and the south-west - over the stand's three
 * feet. A bottle goes in towards the rod and comes out away from it.</p>
 */
final class BrewingStand implements BlockTarget {

    /** Each place's middle, model pixels: x, y, z. */
    private static final double[][] PLACES = {{12.5, 6, 8}, {4.8, 6, 4.8}, {4.8, 6, 11.2}};

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof BrewingStandBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        boolean bottle = isBottle(player.getMainHandItem());
        Spot best = null;
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < PLACES.length; i++) {
            boolean full = block.getValue(BrewingStandBlock.HAS_BOTTLE[i]);
            if (!full && !bottle) continue;
            Spot spot = place(pos, i);
            double d = spot.point().distanceToSqr(hit.getLocation());
            if (d < nearest) {
                nearest = d;
                best = spot;
            }
        }
        return best;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before)) return null;
        for (int i = 0; i < PLACES.length; i++) {
            boolean had = before.getValue(BrewingStandBlock.HAS_BOTTLE[i]);
            boolean has = now.getValue(BrewingStandBlock.HAS_BOTTLE[i]);
            if (had != has) return new Gesture(place(pos, i), has ? Motion.PUT : Motion.TAKE);
        }
        return null;
    }

    private static boolean isBottle(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)
                || stack.is(Items.GLASS_BOTTLE);
    }

    /** A place's middle, and out of it: away from the rod. */
    private static Spot place(BlockPos pos, int i) {
        double[] p = PLACES[i];
        Vec3 point = new Vec3(pos.getX() + p[0] / 16, pos.getY() + p[1] / 16, pos.getZ() + p[2] / 16);
        Vec3 out = new Vec3(p[0] - 8, 0, p[2] - 8).normalize();
        return new Spot(point, out);
    }
}
