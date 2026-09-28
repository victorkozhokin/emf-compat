package strm.emfcompat.animationadditions.stepassist;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The server's side of step assist: nothing but saying it is there. A server with the mod tells
 * each player who joins, and only then does their game tune its speed on steps
 * ({@code StepAssist}); anywhere else the player moves as in vanilla. Optional both ways: a
 * client or a server without the mod joins as usual.
 *
 * <p>Touches no client class, so it loads on a dedicated server.</p>
 */
public final class StepAssistNetwork {

    public record Allowed() implements CustomPacketPayload {
        public static final Allowed INSTANCE = new Allowed();
        public static final Type<Allowed> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("emf_compat_animation_additions", "step_assist"));
        public static final StreamCodec<ByteBuf, Allowed> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Whether the server this game is on has said step assist is allowed. */
    private static volatile boolean allowed;

    private StepAssistNetwork() {
    }

    public static boolean allowed() {
        return allowed;
    }

    /** Leaving a server: nothing is allowed until the next one says so. */
    public static void reset() {
        allowed = false;
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").optional()
                .playToClient(Allowed.TYPE, Allowed.CODEC, (payload, context) -> allowed = true));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(Allowed.TYPE)) {
                PacketDistributor.sendToPlayer(player, Allowed.INSTANCE);
            }
        });
    }
}
