package strm.touchnmotion.blockuse.create;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.touchnmotion.interaction.SubLevels;
import strm.touchnmotion.blockuse.*;

/**
 * Create's blaze burner: with fuel in the hand - anything a furnace burns, a blaze cake - the hand
 * brings it to the blaze through the cage's side the player is at; fed (the burn time left goes
 * up, or the heat), it puts the fuel in. Optional, by name. No blaze in it, no hand.
 */
public final class BlazeBurner implements BlockTarget {

    private static final ModBlock BLOCK = ModBlock.exact("com.simibubi.create.content.processing.burner.BlazeBurnerBlock", "create:blaze_burner");
    private static final ModAccess BURN_TIME = new ModAccess("getRemainingBurnTime");
    /** The blaze's head, blocks up; the cage's side, blocks from the middle. */
    private static final double HEAD_Y = 9 / 16.0;
    private static final double CAGE = 6 / 16.0;
    /** The burn time counts down by itself, and is sent now and then: more than this up, ticks, is a feeding. */
    private static final int FED = 40;

    /** The heat, the burn time left, and the side the player is at. */
    private record Seen(BlockState block, int burnTime, Direction side) {
    }

    @Override
    public boolean matches(BlockState block) {
        return BLOCK.is(block);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return heat(block) > 0 && fuel(player.getMainHandItem()) ? feed(pos, side(player, pos)) : null;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return new Seen(block, BURN_TIME.read(level.getBlockEntity(pos)) instanceof Integer time ? time : 0, side(player, pos));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        boolean fed = now.burnTime > before.burnTime + FED || heat(now.block) > heat(before.block);
        return fed ? new Gesture(feed(pos, now.side), Motion.PUT) : null;
    }

    /** The block's {@code blaze} property, as a number: 0 with no blaze in the burner. */
    private static int heat(BlockState block) {
        Property<?> property = block.getBlock().getStateDefinition().getProperty("blaze");
        return property != null && block.getValue(property) instanceof Enum<?> level ? level.ordinal() : 0;
    }

    private static boolean fuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getBurnTime(null) > 0) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("create") && id.getPath().endsWith("blaze_cake");
    }

    /** The side of the cage nearest the player. */
    private static Direction side(AbstractClientPlayer player, BlockPos pos) {
        Vec3 to = SubLevels.at(player.level(), pos).toLocal(player.position()).subtract(Vec3.atCenterOf(pos));
        return Direction.getNearest(to.x, 0, to.z);
    }

    private static Spot feed(BlockPos pos, Direction side) {
        Vec3 out = Vec3.atLowerCornerOf(side.getNormal());
        return new Spot(new Vec3(pos.getX() + 0.5, pos.getY() + HEAD_Y, pos.getZ() + 0.5).add(out.scale(CAGE)), out);
    }
}
