package strm.touchnmotion.net;

import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}

/**
 * One thing a player's game accepted of a click, which a gesture answers: an animal fed, milked or
 * shorn, an armour stand dressed, a seed planted. Other clients would otherwise have to guess it
 * from the arm's swing.
 */
public record HandsAct(int sender, int kind, int entity, double x, double y, double z, boolean mainHand)
        //? if >=1.20.5
        implements CustomPacketPayload
{
    public static final int FEED = 0, MILK = 1, SHEAR = 2, STAND = 3, SEED = 4;

    //? if >=1.20.5 {
    public static final Type<HandsAct> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("touch_n_motion", "act"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandsAct> CODEC = StreamCodec.of((out, act) -> act.write(out), HandsAct::read);

    @Override
    public Type<HandsAct> type() {
        return TYPE;
    }
    //?}

    public void write(FriendlyByteBuf out) {
        out.writeVarInt(sender);
        out.writeByte(kind);
        out.writeVarInt(entity);
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeBoolean(mainHand);
    }

    public static HandsAct read(FriendlyByteBuf in) {
        return new HandsAct(in.readVarInt(), in.readByte(), in.readVarInt(), in.readDouble(), in.readDouble(), in.readDouble(), in.readBoolean());
    }

    public HandsAct from(int entityId) {
        return new HandsAct(entityId, kind, entity, x, y, z, mainHand);
    }
}
