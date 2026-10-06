package strm.emfcompat.animationadditions.net;

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

    /** How many times another player has moved something in the container they have open; -1 when not known. */
    public static int menuActions(AbstractClientPlayer player) {
        HandsState told = ClientHands.of(player);
        return told == null || told.menu() == 0 ? -1 : told.actions();
    }
}
