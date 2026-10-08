package strm.touchnmotion.blockuse.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.touchnmotion.blockuse.*;
import strm.touchnmotion.interaction.SubLevels;

/**
 * A brewing stand: looked at, the hand waits at the bottle holder on the player's side; a bottle
 * hung on the stand, the hand puts it onto its holder, one taken off, it takes it from there. Which
 * holders have a bottle is in the block's state, so this shows for any player. With our own
 * player's screen open, an ingredient is put on top of the rod and taken from it, and blaze powder
 * is let fall onto the base - the stand holds those where only its own screen tells.
 */
public final class BrewingStand implements BlockTarget {

    /** The three holders, pixels off the rod, as the bottles' models hang them: slot 0 east, 1 and 2 to the west. */
    private static final double[][] HOLDERS = {{4.5, 0}, {-3.2, -3.2}, {-3.2, 3.2}};
    /** Where a bottle is held, the top of the rod, and from where powder is let fall onto the base (the base itself is too low for a standing arm), pixels. */
    private static final double BOTTLE = 7, ROD_TOP = 14, OVER_BASE = 6;
    /** The screen's slots after the three bottles. */
    private static final int INGREDIENT = 3, FUEL = 4;

    /** The stand with its bottles (the state), and what our own screen has on the rod and in the base. */
    private record Seen(BlockState block, int ingredient, int fuel, boolean brewing) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof BrewingStandBlock;
    }

    @Override
    public boolean menu() {
        return true;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Vec3 to = SubLevels.at(player.level(), pos).toLocal(player.position()).subtract(Vec3.atCenterOf(pos));
        int near = 0;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < HOLDERS.length; i++) {
            double towards = HOLDERS[i][0] * to.x + HOLDERS[i][1] * to.z;
            if (towards > best) {
                best = towards;
                near = i;
            }
        }
        return holder(pos, near);
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        if (player == Minecraft.getInstance().player && player.containerMenu instanceof BrewingStandMenu menu)
            return new Seen(block, menu.getSlot(INGREDIENT).getItem().getCount(), menu.getSlot(FUEL).getItem().getCount(),
                    menu.getBrewingTicks() > 0);
        return new Seen(block, 0, 0, false);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        if (!matches(before.block) || !matches(now.block)) return null;
        for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; i++) {
            boolean had = before.block.getValue(BrewingStandBlock.HAS_BOTTLE[i]), has = now.block.getValue(BrewingStandBlock.HAS_BOTTLE[i]);
            if (had != has) return new Gesture(holder(pos, i), has ? Motion.PUT : Motion.TAKE);
        }
        if (now.ingredient > before.ingredient) return new Gesture(Spots.top(pos, 8, ROD_TOP, 8), Motion.PUT);
        // One less while it brews is the brew using it up, not a hand.
        if (now.ingredient < before.ingredient && !before.brewing) return new Gesture(Spots.top(pos, 8, ROD_TOP, 8), Motion.TAKE);
        // The powder only ever goes in by hand: the stand burns it out of sight.
        if (now.fuel > before.fuel) return new Gesture(Spots.top(pos, 8 + HOLDERS[0][0], OVER_BASE, 8), Motion.PUT);
        return null;
    }

    /** Holder {@code i}: the bottle hanging on it, taken from outside. */
    private static Spot holder(BlockPos pos, int i) {
        double x = HOLDERS[i][0], z = HOLDERS[i][1];
        return new Spot(new Vec3(pos.getX() + (8 + x) / 16, pos.getY() + BOTTLE / 16, pos.getZ() + (8 + z) / 16),
                new Vec3(x, 0, z).normalize());
    }
}
