package strm.touchnmotion.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Persistent input that cannot be recovered from another player's look or held items. */
public record HandsDetails(BlockPos cockpitWheel, int typingKeys, BlockPos breaking, int leadHand, boolean cannonAim) {
    public static final HandsDetails NONE = new HandsDetails(null, 0, null, -1, false);

    public boolean idle() {
        return cockpitWheel == null && typingKeys == 0 && breaking == null && leadHand < 0 && !cannonAim;
    }

    public void write(FriendlyByteBuf out) {
        writePos(out, cockpitWheel);
        out.writeByte(typingKeys);
        writePos(out, breaking);
        out.writeByte(leadHand);
        out.writeBoolean(cannonAim);
    }

    public static HandsDetails read(FriendlyByteBuf in) {
        return new HandsDetails(readPos(in), in.readUnsignedByte(), readPos(in), in.readByte(), in.readBoolean());
    }

    private static void writePos(FriendlyByteBuf out, BlockPos pos) {
        out.writeBoolean(pos != null);
        if (pos != null) out.writeBlockPos(pos);
    }

    private static BlockPos readPos(FriendlyByteBuf in) {
        return in.readBoolean() ? in.readBlockPos() : null;
    }
}
