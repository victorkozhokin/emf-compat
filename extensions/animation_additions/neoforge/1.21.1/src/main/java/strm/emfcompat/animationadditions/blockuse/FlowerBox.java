package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplementaries' flower box: three plants in a row. Looked at, the hand is over the third of the
 * row the look is on; a plant put there, the hand puts it; taken, it takes it. Optional, by name.
 *
 * <p>The box is 6 px high and deep, the row across the way it faces: on a wall, against the side
 * behind it; on the floor or under a ceiling, in the middle. Which third is which slot goes by the
 * facing, as the block counts them ({@code getIndex}).</p>
 */
final class FlowerBox implements BlockTarget {

    private static final String BLOCK = "net.mehvahdjukaar.supplementaries.common.block.blocks.FlowerBoxBlock";
    private static final double TOP = 6 / 16.0, BACK = 5 / 16.0;

    /** The three plants, and what the player held. */
    private record Seen(BlockState block, List<String> plants, String hand) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Direction facing = facing(block);
        if (facing == null) return null;
        double along = facing.getAxis() == Direction.Axis.X ? hit.getLocation().z - pos.getZ() : hit.getLocation().x - pos.getX();
        int third = Math.max(0, Math.min(2, (int) Math.floor(along * 3)));
        List<String> plants = plants(player.level(), pos);
        boolean empty = plants.size() != 3 || plants.get(slot(facing, third)) == null;
        return player.getMainHandItem().isEmpty() && empty ? null : at(pos, block, facing, third);
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        ItemStack hand = player.getMainHandItem();
        return new Seen(block, plants(level, pos), hand.isEmpty() ? null : hand.getItem().toString());
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        Direction facing = facing(now.block);
        if (facing == null || before.plants.size() != 3 || now.plants.size() != 3) return null;
        for (int slot = 0; slot < 3; slot++) {
            String had = before.plants.get(slot), has = now.plants.get(slot);
            if (has != null && !has.equals(had)) {
                // slot() is its own inverse: the third of the row this slot is.
                return has.equals(before.hand) ? new Gesture(at(pos, now.block, facing, slot(facing, slot)), Motion.PUT) : null;
            }
            if (has == null && had != null) {
                return before.hand == null ? new Gesture(at(pos, now.block, facing, slot(facing, slot)), Motion.TAKE) : null;
            }
        }
        return null;
    }

    /** The slot of a third of the row counted along the world's axis, and the third of a slot. */
    private static int slot(Direction facing, int third) {
        boolean back = facing.getAxis() == Direction.Axis.X ? facing.getStepX() < 0 : facing.getStepZ() > 0;
        return back ? 2 - third : third;
    }

    private static Spot at(BlockPos pos, BlockState block, Direction facing, int third) {
        double along = (third + 0.5) / 3 * 16;
        boolean wall = true;
        for (Property<?> property : block.getProperties()) {
            if (block.getValue(property) instanceof AttachFace face) wall = face == AttachFace.WALL;
        }
        double depth = wall ? BACK * 16 : 0;
        return facing.getAxis() == Direction.Axis.X
                ? Spots.top(pos, 8 - facing.getStepX() * depth, TOP * 16, along)
                : Spots.top(pos, along, TOP * 16, 8 - facing.getStepZ() * depth);
    }

    private static Direction facing(BlockState block) {
        Property<?> facing = block.getBlock().getStateDefinition().getProperty("facing");
        return facing != null && block.getValue(facing) instanceof Direction d && d.getAxis().isHorizontal() ? d : null;
    }

    private static List<String> plants(Level level, BlockPos pos) {
        List<String> plants = new ArrayList<>(3);
        if (level.getBlockEntity(pos) instanceof Container box) {
            for (int i = 0; i < box.getContainerSize() && i < 3; i++) {
                plants.add(box.getItem(i).isEmpty() ? null : box.getItem(i).getItem().toString());
            }
        }
        return plants;
    }
}
