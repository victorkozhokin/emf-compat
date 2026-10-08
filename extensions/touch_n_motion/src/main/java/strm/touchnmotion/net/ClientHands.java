package strm.touchnmotion.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import strm.touchnmotion.platform.Platform;
import strm.touchnmotion.blockuse.BlockUse;
import strm.touchnmotion.blockuse.aeronautics.Typewriter;
import strm.touchnmotion.buttonpress.aeronautics.ThrottleLever;
import strm.touchnmotion.gesture.AnimalCare;
import strm.touchnmotion.gesture.HandTo;

import java.util.HashMap;
import java.util.Map;
import strm.touchnmotion.DebugLog;

/** The client's end of {@link HandsNet}: tells the server what our own hands are at, and keeps what it is told of others'. */
public final class ClientHands {
    /** While anything lasts it is said again this often, ticks; what a late joiner or a dropped packet missed comes with it. */
    private static final int AGAIN_TICKS = 10;
    /** A change of only where the crosshair is goes out no oftener than this, ticks. */
    private static final int LOOK_TICKS = 3;
    /** What was heard longer ago than this is forgotten: the sender has gone quiet, or away. */
    private static final long FRESH_NANOS = 1_500_000_000L;

    private record Heard(HandsState state, long at) {
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("EMFCompatHands");
    private static final Map<Integer, Heard> HEARD = new HashMap<>();
    private static HandsState sent = HandsState.NOTHING;
    private static int sentAgo, actions;
    private static long slotsHash;
    /** The block our last accepted click used and when, game time; the block whose screen is up, {@code null} with none or not a block's. */
    private static BlockPos used, menuPos;
    private static long usedAt;
    private static boolean menuUp;
    /** A screen that comes up this soon after a block was used, ticks, is that block's. */
    private static final int OPENED_TICKS = 20;

    private ClientHands() {
    }

    /** Whether the server we are on passes these on at all. */
    public static boolean connected() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && Platform.canSendToServer();
    }

    /** Our own game accepted a use of this block: a screen that follows is its. */
    public static void used(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        used = pos.immutable();
        usedAt = mc.level.getGameTime();
    }

    /** Whether our own player has a container's screen up - one opened in the world, not their own inventory. */
    public static boolean ownMenu() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && strm.touchnmotion.platform.Platform.screen() instanceof AbstractContainerScreen<?> screen && screen.getMenu() != mc.player.inventoryMenu;
    }

    /** The block whose screen our own player has up; {@code null} with none, or one no block opened. */
    public static BlockPos ownMenuPos() {
        return ownMenu() ? menuPos : null;
    }

    private static void watchMenu(Minecraft mc) {
        boolean up = ownMenu();
        if (up && !menuUp)
            menuPos = used != null && mc.level != null && mc.level.getGameTime() - usedAt <= OPENED_TICKS ? used : null;
        if (!up) menuPos = null;
        menuUp = up;
    }

    /** Once a client tick: our own state, sent when it has changed or is due again. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) watchMenu(mc);
        if (player == null || !connected()) {
            sent = HandsState.NOTHING;
            return;
        }
        HandsState now = capture(mc, player);
        sentAgo++;
        boolean looked = !same(now, sent, false), changed = !same(now, sent, true);
        if (changed || looked && sentAgo >= LOOK_TICKS || !now.idle() && sentAgo >= AGAIN_TICKS) {
            Platform.sendToServer(now);
            if (changed && DebugLog.decisions())
                LOGGER.info("[Hands] sent flags={} block={} entity={} menu={}", now.flags(), now.block(), now.entity(), now.menu());
            sent = now;
            sentAgo = 0;
        }
    }

    private static HandsState capture(Minecraft mc, LocalPlayer player) {
        int flags = 0, face = 0, entity = -1, key = -1, menu = 0;
        BlockPos block = null;
        double x = 0, y = 0, z = 0;
        boolean playing = strm.touchnmotion.platform.Platform.screen() == null;
        if (playing && mc.options.keyUse.isDown()) flags |= HandsState.USE;
        if (playing && mc.options.keyAttack.isDown()) flags |= HandsState.ATTACK;
        // A control the hands stay on while the eyes wander is what is "looked at" for as long as it is held.
        BlockPos held = BlockUse.heldBlock(player);
        HitResult hit = mc.hitResult;
        if (held != null) {
            flags |= HandsState.HOLD | HandsState.BLOCK;
            block = held.immutable();
            face = 1;
            x = held.getX() + .5;
            y = held.getY() + .5;
            z = held.getZ() + .5;
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            flags |= HandsState.BLOCK;
            block = blockHit.getBlockPos().immutable();
            face = blockHit.getDirection().get3DDataValue();
            // To a sixteenth of a block: finer than a hand can show, and it keeps a steady look from reading as a change.
            x = Math.round(hit.getLocation().x * 16) / 16.0;
            y = Math.round(hit.getLocation().y * 16) / 16.0;
            z = Math.round(hit.getLocation().z * 16) / 16.0;
        } else if (hit instanceof EntityHitResult entityHit) {
            flags |= HandsState.ENTITY;
            entity = entityHit.getEntity().getId();
        }
        BlockPos throttle = ThrottleLever.heldPosition();
        if (throttle != null) flags |= HandsState.THROTTLE;
        BlockPos typing = Typewriter.activePosition(player);
        if (typing != null) {
            flags |= HandsState.TYPING;
            key = Typewriter.pressedKey(player);
        }
        // A container opened in the world; the player's own inventory is not one.
        if (ownMenu() && strm.touchnmotion.platform.Platform.screen() instanceof AbstractContainerScreen<?> screen) {
            menu = 1;
            long hash = 1;
            for (Slot slot : screen.getMenu().slots)
                hash = hash * 31 + (slot.getItem().isEmpty() ? 0 : slot.getItem().getItem().hashCode() * 131L + slot.getItem().getCount());
            if (hash != slotsHash) {
                if (slotsHash != 0) actions = actions + 1 & 0x7FFF;
                slotsHash = hash;
            }
        } else {
            slotsHash = 0;
        }
        // A train driven: which contraption and which of its controls only the driver's own game knows.
        int drive = -1;
        BlockPos drivePos = strm.touchnmotion.blockuse.create.TrainControls.drivenControls(player);
        Entity train = drivePos == null ? null : strm.touchnmotion.blockuse.create.TrainControls.drivenContraption(player);
        if (train != null) {
            flags |= HandsState.DRIVE;
            drive = train.getId();
        } else {
            drivePos = null;
        }
        return new HandsState(0, flags, block, face, x, y, z, entity, throttle, typing, key, menu, actions, drive, drivePos, menu == 0 ? null : menuPos);
    }

    /** {@code all}: everything; else everything but where on the same block the crosshair is. */
    private static boolean same(HandsState a, HandsState b, boolean all) {
        if (a.flags() != b.flags() || a.entity() != b.entity() || a.key() != b.key() || a.drive() != b.drive() || a.menu() != b.menu() || !java.util.Objects.equals(a.menuPos(), b.menuPos()) || a.actions() != b.actions()
                || !java.util.Objects.equals(a.block(), b.block()) || !java.util.Objects.equals(a.throttle(), b.throttle())
                || !java.util.Objects.equals(a.typing(), b.typing())) return false;
        // The spot looked at on one and the same block is the only thing that waits its turn.
        return all || a.face() == b.face() && a.hitX() == b.hitX() && a.hitY() == b.hitY() && a.hitZ() == b.hitZ();
    }

    /** Our own game accepted a click a gesture answers: others are told, so they need not guess. */
    public static void act(int kind, Entity target, Vec3 point, boolean mainHand) {
        if (!connected()) return;
        Vec3 at = point != null ? point : target != null ? target.position() : Vec3.ZERO;
        Platform.sendToServer(new HandsAct(0, kind, target == null ? -1 : target.getId(), at.x, at.y, at.z, mainHand));
    }

    public static void receive(HandsState state) {
        Minecraft.getInstance().execute(() -> {
            if (HEARD.size() > 256) HEARD.clear();
            Heard before = HEARD.put(state.sender(), new Heard(state, System.nanoTime()));
            if (DebugLog.decisions() && (before == null || before.state.flags() != state.flags()))
                LOGGER.info("[Hands] heard from {} flags={} block={} entity={}", state.sender(), state.flags(), state.block(), state.entity());
        });
    }

    public static void receive(HandsAct act) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null || !(mc.level.getEntity(act.sender()) instanceof AbstractClientPlayer player) || player == mc.player) return;
            if (act.kind() <= HandsAct.SHEAR) {
                Entity animal = mc.level.getEntity(act.entity());
                if (animal != null) AnimalCare.done(player, animal, act.kind(), act.mainHand());
            } else {
                HandTo.done(player, act.kind() == HandsAct.STAND ? HandTo.STAND : HandTo.SEED, new Vec3(act.x(), act.y(), act.z()), act.mainHand());
            }
        });
    }

    /** What this player's own game last said of their hands; {@code null} for our own player, for none, for too long ago. */
    public static HandsState of(AbstractClientPlayer player) {
        if (HEARD.isEmpty() || player == Minecraft.getInstance().player) return null;
        Heard heard = HEARD.get(player.getId());
        return heard != null && System.nanoTime() - heard.at < FRESH_NANOS ? heard.state : null;
    }

    /** For tests: as if this had come from the server. */
    public static void inject(HandsState state) {
        receive(state);
    }

    public static void forgetAll() {
        HEARD.clear();
        sent = HandsState.NOTHING;
    }
}
