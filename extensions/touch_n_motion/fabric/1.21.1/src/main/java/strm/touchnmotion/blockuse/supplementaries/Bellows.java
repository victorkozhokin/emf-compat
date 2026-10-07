package strm.touchnmotion.blockuse.supplementaries;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.SubLevels;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.blockuse.*;

/** Supplementaries' actual moving plate. Manual use only; redstone and stepping are not hand actions. */
public final class Bellows implements BlockTarget {
    private static final ModBlock BLOCK = ModBlock.family("net.mehvahdjukaar.supplementaries.common.block.blocks.BellowsBlock", "supplementaries:bellows");
    private static final ModAccess HEIGHT = new ModAccess("height"), PREVIOUS = new ModAccess("prevHeight"), MANUAL = new ModAccess("manualPress");
    private static final EntityStates<Press> PRESSES = new EntityStates<>(Press::new);
    private static final class Press {BlockPos pos; boolean swinging; int swing; long until;}
    @Override public boolean matches(BlockState block) { return BLOCK.is(block); }
    public static float height(Level level, BlockPos pos) {
        Object be = level.getBlockEntity(pos), a = PREVIOUS.read(be), b = HEIGHT.read(be);
        if (!(a instanceof Number previous) || !(b instanceof Number current)) return 0;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        return BellowsGeometry.height(previous.floatValue(), current.floatValue(), partial);
    }
    private static boolean usable(AbstractClientPlayer player, BlockState block) {
        return block.getValue(BlockStateProperties.POWER) == 0 && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty();
    }
    static Quaternionf turn(BlockState block) {
        return block.getValue(BlockStateProperties.FACING).getOpposite().getRotation().rotateX(-(float) Math.PI / 2);
    }
    public static Spot contact(AbstractClientPlayer player, BlockPos pos, BlockState block, boolean main) {
        Quaternionf turn = turn(block);
        Vec3 local = SubLevels.at(player.level(), pos).tickToLocal(player.position()).subtract(Vec3.atCenterOf(pos));
        Vector3f projected = new Quaternionf(turn).conjugate().transform(new Vector3f((float) local.x, (float) local.y, (float) local.z));
        boolean right = Body.right(player, main);
        var surface = new TableSurface(0, 1, 0, 1, 1 + height(player.level(), pos), 3 / 16.0);
        var point = surface.contact(projected.x + .5, projected.z + .5, right);
        var at = turn.transform(new Vector3f((float) point.x() - .5f, (float) point.y() - .5f, (float) point.z() - .5f));
        var normal = turn.transform(new Vector3f(0, 1, 0));
        return new Spot(Vec3.atCenterOf(pos).add(at.x, at.y, at.z), new Vec3(normal.x, normal.y, normal.z));
    }
    private static boolean plateAccessible(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        if (block.getValue(BlockStateProperties.FACING).getAxis() != Direction.Axis.Y) return true;
        var normal = turn(block).transform(new Vector3f(0, 1, 0));
        Vec3 local = SubLevels.at(player.level(), pos).tickToLocal(player.position()).subtract(Vec3.atCenterOf(pos));
        // A vertical lid can only be pressed from its exposed face, not through the casing.
        return local.x * normal.x + local.z * normal.z > .65;
    }
    @Override public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        if (!usable(player, block) || !plateAccessible(player, pos, block)) return null;
        var p = PRESSES.seen(player.getUUID(), System.nanoTime()).value;
        if (!pos.equals(p.pos)) { p.pos = pos.immutable(); p.until = 0; p.swinging = false; p.swing = 0; }
        Object count = MANUAL.read(player.level().getBlockEntity(pos));
        // The mod tells only the game that pressed of the press: for anyone else the arm's swing at the plate is the word on it.
        boolean pressed = player != Minecraft.getInstance().player || count instanceof Number n && n.intValue() > 0;
        if (player.swinging && (!p.swinging || player.swingTime < p.swing) && pressed)
            p.until = player.level().getGameTime() + 18;
        p.swinging = player.swinging;
        p.swing = player.swingTime;
        return contact(player, pos, block, true);
    }
    @Override public Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) { return usable(player, block) && plateAccessible(player, pos, block) ? contact(player, pos, block, false) : null; }
    @Override public boolean holds(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        var p = PRESSES.fresh(player.getUUID());
        return usable(player, block) && plateAccessible(player, pos, block) && p != null && pos.equals(p.pos) && level.getGameTime() < p.until;
    }
    public static boolean pressing(AbstractClientPlayer player, BlockPos pos) {
        var p = PRESSES.fresh(player.getUUID());
        return p != null && pos.equals(p.pos) && player.level().getGameTime() < p.until;
    }
    @Override public boolean quietsSwing() { return true; }
    @Override public Gesture changed(BlockPos pos, Object before, Object now) { return null; }
}
