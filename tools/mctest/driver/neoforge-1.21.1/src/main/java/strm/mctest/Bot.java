package strm.mctest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Another player for multiplayer tests: a server player on the integrated server with a
 * connection that goes nowhere, as Carpet's {@code /player} makes one. Our client sees it as any
 * other player - a {@code RemotePlayer}, drawn with its pack, moved and swung by the server's
 * packets - which is the code path a real second player takes.
 *
 * <p>Its outgoing packets and keep-alive are dropped ({@code mixin.BotListenerMixin}): NeoForge
 * refuses mods' payloads to a connection that never negotiated their channels. It does not move
 * by itself; each step puts it where it should be.</p>
 *
 * <pre>
 * {"bot": {"spawn": "Bob", "at": [x, y, z], "look": [yaw, pitch]}}
 * {"bot": {"name": "Bob", "at": [x, y, z], "look": [yaw, pitch], "sneak": true, "item": "minecraft:stick"}}
 * {"bot": {"name": "Bob", "use": [x, y, z], "face": "west", "hit": [x, y, z]}}   right click on a block
 * {"bot": {"name": "Bob", "swing": true}}   {"bot": {"name": "Bob", "remove": true}}
 * </pre>
 * "name" can be left out while there is one bot.
 */
public final class Bot {

    /** A connection with nothing on the other end. */
    static final class BotConnection extends Connection {
        BotConnection() {
            super(PacketFlow.SERVERBOUND);
            try {
                Field channel = Connection.class.getDeclaredField("channel");
                channel.setAccessible(true);
                channel.set(this, new EmbeddedChannel());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
            try {
                Field field = Connection.class.getDeclaredField("packetListener");
                field.setAccessible(true);
                field.set(this, listener);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void setupOutboundProtocol(ProtocolInfo<?> protocol) {
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
        }

        @Override
        public void handleDisconnection() {
        }

        @Override
        public void setReadOnly() {
        }
    }

    private static final Map<String, ServerPlayer> BOTS = new HashMap<>();

    private Bot() {
    }

    static JsonObject run(Minecraft mc, JsonObject v) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("bot: needs the integrated server (singleplayer)");
        JsonObject out = new JsonObject();
        server.submit(() -> {
            ServerPlayer bot = v.has("spawn") ? spawn(server, v.get("spawn").getAsString()) : find(v);
            if (v.has("item")) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(v.get("item").getAsString())));
                bot.setItemInHand(InteractionHand.MAIN_HAND, stack);
            }
            if (v.has("sneak")) {
                boolean sneak = v.get("sneak").getAsBoolean();
                bot.setShiftKeyDown(sneak);
                bot.setPose(sneak ? Pose.CROUCHING : Pose.STANDING);
            }
            if (v.has("at") || v.has("look")) {
                Vec3 at = v.has("at") ? vec(v.getAsJsonArray("at")) : bot.position();
                float yaw = v.has("look") ? v.getAsJsonArray("look").get(0).getAsFloat() : bot.getYRot();
                float pitch = v.has("look") ? v.getAsJsonArray("look").get(1).getAsFloat() : bot.getXRot();
                bot.moveTo(at.x, at.y, at.z, yaw, pitch);
                bot.setYHeadRot(yaw);
                bot.setYBodyRot(yaw);
                bot.setOnGround(true);
            }
            if (v.has("use")) {
                BlockPos pos = BlockPos.containing(vec(v.getAsJsonArray("use")));
                Direction face = v.has("face") ? Direction.byName(v.get("face").getAsString()) : Direction.UP;
                Vec3 hit = v.has("hit") ? vec(v.getAsJsonArray("hit"))
                        : Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
                var result = bot.gameMode.useItemOn(bot, bot.serverLevel(), bot.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(hit, face, pos, false));
                bot.swing(InteractionHand.MAIN_HAND, true);
                out.addProperty("use", result.toString());
            }
            if (v.has("swing")) bot.swing(InteractionHand.MAIN_HAND, true);
            if (v.has("remove")) {
                server.getPlayerList().remove(bot);
                BOTS.values().remove(bot);
                out.addProperty("removed", bot.getGameProfile().getName());
                return;
            }
            out.addProperty("name", bot.getGameProfile().getName());
            out.addProperty("pos", bot.position().toString());
            out.addProperty("onGround", bot.onGround());
        }).join();
        return out;
    }

    private static ServerPlayer spawn(MinecraftServer server, String name) {
        ServerPlayer old = BOTS.remove(name);
        if (old != null) server.getPlayerList().remove(old);
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerLevel level = server.overworld();
        ServerPlayer bot = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        server.getPlayerList().placeNewPlayer(new BotConnection(), bot,
                new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false));
        BOTS.put(name, bot);
        return bot;
    }

    private static ServerPlayer find(JsonObject v) {
        if (v.has("name")) {
            ServerPlayer bot = BOTS.get(v.get("name").getAsString());
            if (bot == null) throw new IllegalStateException("bot: no bot named " + v.get("name").getAsString());
            return bot;
        }
        if (BOTS.size() != 1) throw new IllegalStateException("bot: say which (\"name\"), there are " + BOTS.size());
        return BOTS.values().iterator().next();
    }

    public static boolean isBot(Connection connection) {
        return connection instanceof BotConnection;
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
}
