package strm.emfcompat.animationadditions.blockuse.supplementaries;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * Supplementaries' books, a pile of them lying or a row standing: with a book in the hand it goes onto the top
 * of the pile; one more book ({@code books}), the hand puts it there; one fewer, it takes it.
 * Optional, by name.
 */
public final class BookPile implements BlockTarget {

    private static final ModBlock BLOCKS = ModBlock.named("net.mehvahdjukaar.supplementaries.common.block.blocks.BookPile", "supplementaries:book_pile", "supplementaries:book_pile_horizontal");
    /** A book lying flat is this thick, pixels. */
    private static final double BOOK = 4;

    @Override
    public boolean matches(BlockState block) {
        return BLOCKS.is(block);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        var held = player.getMainHandItem();
        boolean book = held.is(Items.BOOK) || held.is(Items.ENCHANTED_BOOK) || held.is(Items.WRITABLE_BOOK) || held.is(Items.WRITTEN_BOOK)
                || held.getItem().toString().contains("book") || held.getItem().toString().contains("tome");
        return book ? top(pos, block) : null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        int from = books(before), to = books(now);
        if (to > from) return new Gesture(top(pos, now), Motion.PUT);
        return to < from ? new Gesture(top(pos, before), Motion.TAKE) : null;
    }

    private static int books(BlockState block) {
        Property<?> property = block.getBlock().getStateDefinition().getProperty("books");
        return property != null && block.getValue(property) instanceof Integer books ? books : 1;
    }

    private static Spot top(BlockPos pos, BlockState block) {
        // book_pile is books lying one on another; book_pile_horizontal, a row of them standing, 10 px tall.
        boolean standing = block.getBlock().getClass().getName().endsWith("HorizontalBlock");
        return Spots.top(pos, 8, standing ? 10 : Math.min(16, BOOK * books(block)), 8);
    }
}
