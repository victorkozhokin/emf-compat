package strm.touchnmotion.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What one player's hands are at, as only that player's own game knows it: the buttons held, the
 * thing under the crosshair (a craft's blocks included, which another client's ray does not see),
 * the control held on to, the key typed, the train driven, the container gone through and which block it is. Sent to the server when it
 * changes and now and then while it lasts; the server passes it on to whoever sees that player.
 * Nothing here moves the world - it only poses the sender's model on other screens.
 */
public record HandsState(int sender, int flags, BlockPos block, int face, double hitX, double hitY, double hitZ, int entity,
                         BlockPos throttle, BlockPos typing, int key, int menu, int actions, int drive, BlockPos drivePos,
                         BlockPos menuPos) implements CustomPacketPayload {
    public static final int USE = 1, ATTACK = 2, BLOCK = 4, ENTITY = 8, HOLD = 16, THROTTLE = 32, TYPING = 64, DRIVE = 128;
    public static final Type<HandsState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("touch_n_motion", "hands"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandsState> CODEC = StreamCodec.of(HandsState::write, HandsState::read);
    public static final HandsState NOTHING = new HandsState(0, 0, null, 0, 0, 0, 0, -1, null, null, -1, 0, 0, -1, null, null);

    private static void write(RegistryFriendlyByteBuf out, HandsState s) {
        out.writeVarInt(s.sender);
        out.writeByte(s.flags);
        if (s.has(BLOCK)) {
            out.writeBlockPos(s.block);
            out.writeByte(s.face);
            out.writeDouble(s.hitX);
            out.writeDouble(s.hitY);
            out.writeDouble(s.hitZ);
        }
        if (s.has(ENTITY)) out.writeVarInt(s.entity);
        if (s.has(THROTTLE)) out.writeBlockPos(s.throttle);
        if (s.has(TYPING)) {
            out.writeBlockPos(s.typing);
            out.writeByte(s.key);
        }
        if (s.has(DRIVE)) {
            out.writeVarInt(s.drive);
            out.writeBlockPos(s.drivePos);
        }
        // 0 no screen, 1 a container's screen, 2 the same and which block it is of.
        out.writeByte(s.menu == 0 ? 0 : s.menuPos == null ? 1 : 2);
        out.writeShort(s.actions);
        if (s.menu != 0 && s.menuPos != null) out.writeBlockPos(s.menuPos);
    }

    private static HandsState read(RegistryFriendlyByteBuf in) {
        int sender = in.readVarInt(), flags = in.readByte() & 0xFF;
        BlockPos block = null, throttle = null, typing = null, drivePos = null;
        int face = 0, entity = -1, key = -1, drive = -1;
        double x = 0, y = 0, z = 0;
        if ((flags & BLOCK) != 0) {
            block = in.readBlockPos();
            face = in.readByte();
            x = in.readDouble();
            y = in.readDouble();
            z = in.readDouble();
        }
        if ((flags & ENTITY) != 0) entity = in.readVarInt();
        if ((flags & THROTTLE) != 0) throttle = in.readBlockPos();
        if ((flags & TYPING) != 0) {
            typing = in.readBlockPos();
            key = in.readByte();
        }
        if ((flags & DRIVE) != 0) {
            drive = in.readVarInt();
            drivePos = in.readBlockPos();
        }
        int menu = in.readByte(), actions = in.readShort();
        BlockPos menuPos = menu == 2 ? in.readBlockPos() : null;
        return new HandsState(sender, flags, block, face, x, y, z, entity, throttle, typing, key, menu == 0 ? 0 : 1, actions, drive, drivePos, menuPos);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** Whether there is anything in it worth keeping another client told of. */
    public boolean idle() {
        return flags == 0 && menu == 0;
    }

    public HandsState from(int entityId) {
        return new HandsState(entityId, flags, block, face, hitX, hitY, hitZ, entity, throttle, typing, key, menu, actions, drive, drivePos, menuPos);
    }

    @Override
    public Type<HandsState> type() {
        return TYPE;
    }
}
