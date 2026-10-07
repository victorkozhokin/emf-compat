package strm.touchnmotion.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a player is doing with the mouse and the keys, wherever that is known from: our own
 * player's from the game, another's from what their game sent ({@link ClientHands}), and failing
 * that - a server without the addon - a guess from where they look.
 */
public final class Inputs {
    private Inputs() {
    }

    /** Whether this other player's game tells us of their hands. */
    public static boolean told(AbstractClientPlayer player) {
        return ClientHands.of(player) != null;
    }

    /** What the player has under the crosshair. */
    public static HitResult sight(AbstractClientPlayer player, double range, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.hitResult != null) return mc.hitResult;
        HandsState told = ClientHands.of(player);
        if (told == null) return player.pick(range, partial, false);
        if (told.has(HandsState.BLOCK))
            return new BlockHitResult(new Vec3(told.hitX(), told.hitY(), told.hitZ()), Direction.from3DDataValue(told.face()), told.block(), false);
        if (told.has(HandsState.ENTITY)) {
            Entity entity = player.level().getEntity(told.entity());
            if (entity != null) return new EntityHitResult(entity);
        }
        Vec3 eye = player.getEyePosition(partial);
        return BlockHitResult.miss(eye, Direction.UP, BlockPos.containing(eye));
    }

    public static boolean useHeld(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return mc.options.keyUse.isDown();
        HandsState told = ClientHands.of(player);
        return told != null && told.has(HandsState.USE);
    }

    public static boolean attackHeld(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return mc.options.keyAttack.isDown();
        HandsState told = ClientHands.of(player);
        return told != null && told.has(HandsState.ATTACK);
    }

    /** Whether another player's hands are kept on the control at {@code pos} while their eyes are elsewhere. */
    public static boolean holds(AbstractClientPlayer player, BlockPos pos) {
        HandsState told = ClientHands.of(player);
        return told != null && told.has(HandsState.HOLD) && pos.equals(told.block());
    }

    /** The throttle lever another player drags, or {@code null}. */
    public static BlockPos throttle(AbstractClientPlayer player) {
        HandsState told = ClientHands.of(player);
        return told == null ? null : told.throttle();
    }

    /** The typewriter another player types at, or {@code null}; and the key they hold on it, or -1. */
    public static BlockPos typing(AbstractClientPlayer player) {
        HandsState told = ClientHands.of(player);
        return told == null ? null : told.typing();
    }

    public static int typedKey(AbstractClientPlayer player) {
        HandsState told = ClientHands.of(player);
        return told == null ? -1 : told.key();
    }

    /** The contraption another player drives, as their game says, and which of its controls; -1 and {@code null} for none. */
    public static int driveEntity(AbstractClientPlayer player) {
        HandsState told = ClientHands.of(player);
        return told == null || !told.has(HandsState.DRIVE) ? -1 : told.drive();
    }

    public static BlockPos drivePos(AbstractClientPlayer player) {
        HandsState told = ClientHands.of(player);
        return told == null || !told.has(HandsState.DRIVE) ? null : told.drivePos();
    }

    /** How many times another player has moved something in the container they have open; -1 when not known. */
    /**
     * Whether the player has a container's screen up: as our own game or theirs says, or
     * {@code null} where theirs says nothing.
     */
    public static Boolean menu(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) return ClientHands.ownMenu();
        HandsState told = ClientHands.of(player);
        return told == null ? null : told.menu() != 0;
    }

    /** The block whose screen the player has up; {@code null} when none, not a block's, or not known. */
    public static BlockPos menuPos(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) return ClientHands.ownMenuPos();
        HandsState told = ClientHands.of(player);
        return told == null || told.menu() == 0 ? null : told.menuPos();
    }

    /** Whether the player is known to be at this block's screen. */
    public static boolean menuAt(AbstractClientPlayer player, BlockPos pos) {
        return pos.equals(menuPos(player));
    }

    public static int menuActions(AbstractClientPlayer player) {
        HandsState told = ClientHands.of(player);
        return told == null || told.menu() == 0 ? -1 : told.actions();
    }
}
