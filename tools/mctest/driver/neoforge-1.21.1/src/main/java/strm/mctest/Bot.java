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
 * {"bot": {"name": "Bob", "at": [x, y, z], "lookAt": [x, y, z]}}   look at a point from the eyes there
 * {"bot": {"name": "Bob", "cycle": {"use": [x, y, z], "face": "south", "every": 40, "offset": 0, "close": 20}}}
 *     the same click every 40 ticks on the server's tick, a container shut again after 20; "cycle": false stops
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

    /** A bot using one block over and over: every {@code every} ticks, a container shut again {@code close} ticks later. */
    private record Cycle(BlockPos pos, Direction face, Vec3 hit, int every, int offset, int close) {
    }

    private static final Map<ServerPlayer, Cycle> CYCLES = new HashMap<>();
    private static boolean listening;
    private static long ticks;

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
                // Nothing ticks a bot, so nothing sends what it holds: tell those who see it.
                bot.serverLevel().getChunkSource().broadcast(bot, new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(
                        bot.getId(), java.util.List.of(com.mojang.datafixers.util.Pair.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack.copy()))));
            }
            if (v.has("sneak")) {
                boolean sneak = v.get("sneak").getAsBoolean();
                bot.setShiftKeyDown(sneak);
                bot.setPose(sneak ? Pose.CROUCHING : Pose.STANDING);
            }
            if (v.has("at") || v.has("look") || v.has("lookAt")) {
                Vec3 at = v.has("at") ? vec(v.getAsJsonArray("at")) : bot.position();
                float yaw = v.has("look") ? v.getAsJsonArray("look").get(0).getAsFloat() : bot.getYRot();
                float pitch = v.has("look") ? v.getAsJsonArray("look").get(1).getAsFloat() : bot.getXRot();
                if (v.has("lookAt")) {
                    // From the eyes at that spot, standing or crouching.
                    Vec3 to = vec(v.getAsJsonArray("lookAt")).subtract(at.add(0, bot.getEyeHeight(), 0));
                    yaw = (float) Math.toDegrees(-Math.atan2(to.x, to.z));
                    pitch = (float) Math.toDegrees(-Math.atan2(to.y, Math.hypot(to.x, to.z)));
                }
                bot.moveTo(at.x, at.y, at.z, yaw, pitch);
                bot.setYHeadRot(yaw);
                bot.setYBodyRot(yaw);
                bot.setOnGround(true);
            }
            if (v.has("use")) {
                Cycle once = cycle(v, 0, 0, 0);
                out.addProperty("use", use(bot, once).toString());
            }
            if (v.has("cycle")) {
                if (v.get("cycle").isJsonObject()) {
                    JsonObject c = v.getAsJsonObject("cycle");
                    CYCLES.put(bot, cycle(c, c.has("every") ? c.get("every").getAsInt() : 40,
                            c.has("offset") ? c.get("offset").getAsInt() : 0, c.has("close") ? c.get("close").getAsInt() : 0));
                    listen();
                } else {
                    CYCLES.remove(bot);
                }
            }
            if (v.has("menu")) {
                // A click on a slot of the menu the bot has open (after a "use" opened it), as a client's would be.
                JsonObject c = v.getAsJsonObject("menu");
                bot.containerMenu.clicked(c.get("slot").getAsInt(), c.has("button") ? c.get("button").getAsInt() : 0,
                        net.minecraft.world.inventory.ClickType.valueOf(c.has("type") ? c.get("type").getAsString() : "PICKUP"), bot);
                bot.containerMenu.broadcastChanges();
                out.addProperty("menu", bot.containerMenu.getClass().getSimpleName());
            }
            if (v.has("hands")) hands(bot, v.getAsJsonObject("hands"), out);
            if (v.has("act")) act(bot, v.getAsJsonObject("act"), out);
            if (v.has("close")) bot.closeContainer();
            if (v.has("swing")) bot.swing(InteractionHand.MAIN_HAND, true);
            if (v.has("remove")) {
                server.getPlayerList().remove(bot);
                BOTS.values().remove(bot);
                CYCLES.remove(bot);
                out.addProperty("removed", bot.getGameProfile().getName());
                return;
            }
            out.addProperty("name", bot.getGameProfile().getName());
            out.addProperty("pos", bot.position().toString());
            out.addProperty("onGround", bot.onGround());
        }).join();
        return out;
    }

    private static Cycle cycle(JsonObject c, int every, int offset, int close) {
        BlockPos pos = BlockPos.containing(vec(c.getAsJsonArray("use")));
        Direction face = c.has("face") ? Direction.byName(c.get("face").getAsString()) : Direction.UP;
        Vec3 hit = c.has("hit") ? vec(c.getAsJsonArray("hit"))
                : Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
        return new Cycle(pos, face, hit, Math.max(1, every), offset, close);
    }

    /** A right click on the block, as a player's would be, and the swing that goes with it. */
    private static net.minecraft.world.InteractionResult use(ServerPlayer bot, Cycle c) {
        var result = bot.gameMode.useItemOn(bot, bot.serverLevel(), bot.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(c.hit, c.face, c.pos, false));
        bot.swing(InteractionHand.MAIN_HAND, true);
        return result;
    }

    /** Runs the cycles on the server's tick, so they go on between scripts. */
    private static void listen() {
        if (listening) return;
        listening = true;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
                    ticks++;
                    CYCLES.entrySet().removeIf(e -> e.getKey().isRemoved() || e.getKey().hasDisconnected());
                    CYCLES.forEach((bot, c) -> {
                        long phase = (ticks + c.offset) % c.every;
                        bot.setOnGround(true);
                        if (phase == 0) use(bot, c);
                        else if (c.close > 0 && phase == c.close && bot.containerMenu != bot.inventoryMenu) bot.closeContainer();
                    });
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
                    BOTS.clear();
                    CYCLES.clear();
                });
    }

    private static ServerPlayer spawn(MinecraftServer server, String name) {
        listen();
        ServerPlayer old = BOTS.remove(name);
        if (old != null) {
            server.getPlayerList().remove(old);
            CYCLES.remove(old);
        }
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

    /**
     * What a real second player's game would tell the server of their hands, said for the bot: the
     * server passes it on to our client by the addon's own channel, so the packet, its reading and
     * everything after are the real ones. {"use": true, "attack": false, "block": [x, y, z],
     * "face": "up", "hit": [x, y, z], "hold": true, "entity": id, "menu": 1, "actions": 3}; {} clears.
     */
    private static void hands(ServerPlayer bot, JsonObject h, JsonObject out) {
        try {
            String net = "strm.touchnmotion.net.";
            int flags = 0, face = 1, entity = -1;
            BlockPos block = null;
            double x = 0, y = 0, z = 0;
            if (h.has("use") && h.get("use").getAsBoolean()) flags |= 1;
            if (h.has("attack") && h.get("attack").getAsBoolean()) flags |= 2;
            if (h.has("block")) {
                JsonArray b = h.getAsJsonArray("block");
                block = new BlockPos(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt());
                flags |= 4;
                if (h.has("face")) face = Direction.byName(h.get("face").getAsString()).get3DDataValue();
                Vec3 hit = h.has("hit") ? vec(h.getAsJsonArray("hit")) : Vec3.atCenterOf(block);
                x = hit.x;
                y = hit.y;
                z = hit.z;
                if (h.has("hold") && h.get("hold").getAsBoolean()) flags |= 16;
            } else if (h.has("entity")) {
                entity = h.get("entity").getAsInt();
                flags |= 8;
            }
            Class<?> type = Class.forName(net + "HandsState");
            Object state = type.getDeclaredConstructors()[0].newInstance(bot.getId(), flags, block, face, x, y, z, entity, null, null, -1,
                    h.has("menu") ? h.get("menu").getAsInt() : 0, h.has("actions") ? h.get("actions").getAsInt() : 0, -1, null,
                    h.has("menuAt") ? new BlockPos(h.getAsJsonArray("menuAt").get(0).getAsInt(), h.getAsJsonArray("menuAt").get(1).getAsInt(), h.getAsJsonArray("menuAt").get(2).getAsInt()) : null);
            Class.forName(net + "HandsNet").getMethod("relay", ServerPlayer.class, net.minecraft.network.protocol.common.custom.CustomPacketPayload.class)
                    .invoke(null, bot, state);
            out.addProperty("hands", flags);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("bot hands: " + e, e);
        }
    }

    /** The same for one accepted click: {"kind": 0 feed / 1 milk / 2 shear / 3 stand / 4 seed, "entity": id, "at": [x, y, z]}. */
    private static void act(ServerPlayer bot, JsonObject a, JsonObject out) {
        try {
            String net = "strm.touchnmotion.net.";
            Vec3 at = a.has("at") ? vec(a.getAsJsonArray("at")) : Vec3.ZERO;
            Object act = Class.forName(net + "HandsAct").getDeclaredConstructors()[0].newInstance(bot.getId(), a.get("kind").getAsInt(),
                    a.has("entity") ? a.get("entity").getAsInt() : -1, at.x, at.y, at.z, true);
            Class.forName(net + "HandsNet").getMethod("relay", ServerPlayer.class, net.minecraft.network.protocol.common.custom.CustomPacketPayload.class)
                    .invoke(null, bot, act);
            out.addProperty("act", a.get("kind").getAsInt());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("bot act: " + e, e);
        }
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
}
