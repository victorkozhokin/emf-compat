package strm.touchnmotion.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}

/** One menu gesture, in its block's own space; no private inventory contents are sent. */
public record HandsWork(int sender, BlockPos block, int motion, Vec3 point, Vec3 out, Vec3 sweep)
        //? if >=1.20.5
        implements CustomPacketPayload
{
    //? if >=1.20.5 {
    public static final Type<HandsWork> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("touch_n_motion", "work_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandsWork> CODEC = StreamCodec.of((buf, work) -> work.write(buf), HandsWork::read);
    public Type<HandsWork> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(sender);
        buf.writeBlockPos(block);
        buf.writeByte(motion);
        writePoint(buf, point);
        writePoint(buf, out);
        buf.writeBoolean(sweep != null);
        if (sweep != null) writePoint(buf, sweep);
    }

    public static HandsWork read(FriendlyByteBuf buf) {
        return new HandsWork(buf.readVarInt(), buf.readBlockPos(), buf.readUnsignedByte(), readPoint(buf), readPoint(buf),
                buf.readBoolean() ? readPoint(buf) : null);
    }

    public HandsWork from(int entityId) { return new HandsWork(entityId, block, motion, point, out, sweep); }

    private static void writePoint(FriendlyByteBuf buf, Vec3 point) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
    }

    private static Vec3 readPoint(FriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }
}
