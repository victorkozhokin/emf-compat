"""Carries Touch'n Motion over from NeoForge 1.21.1 to Fabric 1.21.1: copies the sources and swaps what is the
loader's - events, networking, the mod list, item handlers. Both are Mojang-mapped 1.21.1, so the game's own names
are the same. Run from the repository root; it rewrites extensions/touch_n_motion/fabric/1.21.1/src/main.
Files this script writes whole (the entry points, the networking, the render-event mixin) are in PORT below."""
import os, re, shutil

SRC = "extensions/touch_n_motion/neoforge/1.21.1/src/main"
DST = "extensions/touch_n_motion/fabric/1.21.1/src/main"
PKG = "java/strm/touchnmotion/"


def rd(p):
    return open(p, encoding="utf-8").read()


def wr(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf-8").write(s)


def patch(rel, pairs):
    p = os.path.join(DST, PKG, rel)
    s = rd(p)
    for a, b in pairs:
        if a not in s:
            raise SystemExit(f"{rel}: not found: {a[:80]!r}")
        s = s.replace(a, b)
    wr(p, s)


shutil.rmtree(DST, ignore_errors=True)
shutil.rmtree("extensions/touch_n_motion/fabric/1.21.1/src/client", ignore_errors=True)
shutil.copytree(os.path.join(SRC, "java"), os.path.join(DST, "java"))
wr(os.path.join(DST, "resources/touch_n_motion.mixins.json"),
   rd(os.path.join(SRC, "resources/touch_n_motion.mixins.json")).replace(
       '"horsesync.PlayerModelMixin",', '"horsesync.PlayerModelMixin",\n    "PlayerRenderEventsMixin",'))

# --- the loader's own names, wherever they are
for d, _, fs in os.walk(os.path.join(DST, "java")):
    for f in fs:
        p = os.path.join(d, f)
        s = rd(p)
        o = s
        s = s.replace("net.neoforged.fml.ModList.get().isLoaded(", "net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(")
        if s != o:
            wr(p, s)

# --- entry points
wr(os.path.join(DST, PKG, "TouchNMotionMod.java"), '''package strm.touchnmotion;

import net.fabricmc.api.ModInitializer;

/**
 * Touch'n Motion on Fabric: the part that runs on both sides - the channels a server passes
 * players' hands on by. Everything else is the client's ({@link TouchNMotionClient}).
 */
public class TouchNMotionMod implements ModInitializer {

    public static final String MOD_ID = "touch_n_motion";
    /** The mod's master option; the core's global switch covers it by its suffix. */
    public static final String KEY_ENABLED = "touchnmotion.enabled";

    @Override
    public void onInitialize() {
        strm.touchnmotion.net.HandsNet.register();
    }
}
''')
wr(os.path.join(DST, PKG, "TouchNMotionClient.java"), '''package strm.touchnmotion;

import net.fabricmc.api.ClientModInitializer;

/** Touch'n Motion on Fabric, the client: the settings, the providers, the hooks. */
public class TouchNMotionClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientInit.run();
        strm.touchnmotion.net.HandsNet.registerClient();
    }
}
''')
wr(os.path.join(DST, PKG, "ModMenuIntegration.java"), '''package strm.touchnmotion;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import strm.emfcompat.core.client.ConfigScreen;

/**
 * Mod Menu entry point: the mod's own entry in the mod list opens the settings - the shared
 * screen, on this mod's tab. Only ever loaded by Mod Menu itself, so nothing here runs, or
 * fails, when Mod Menu is absent.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigScreen(parent, TouchNMotionMod.MOD_ID);
    }
}
''')

# --- ClientInit: no event bus; the world left and the tick come from Fabric's events
patch("ClientInit.java", [
    ("import net.neoforged.bus.api.IEventBus;\nimport net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;\nimport net.neoforged.neoforge.common.NeoForge;\n",
     "import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;\nimport net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;\n"),
    ("static void run(IEventBus modEventBus) {", "static void run() {"),
    ("HorseSync.register(riding, modEventBus);", "HorseSync.register(riding);"),
    ("        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> EntityStates.clearAll());",
     "        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EntityStates.clearAll());"),
    ("        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> strm.touchnmotion.net.ClientHands.tick());",
     "        ClientTickEvents.END_CLIENT_TICK.register(client -> strm.touchnmotion.net.ClientHands.tick());\n        ClientTickEvents.START_CLIENT_TICK.register(client -> strm.touchnmotion.horsesync.ClientEventHandler.onClientTick());"),
    ("        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> strm.touchnmotion.net.ClientHands.forgetAll());",
     "        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> strm.touchnmotion.net.ClientHands.forgetAll());"),
])
s = rd(os.path.join(DST, PKG, "ClientInit.java"))
i = s.index("    /** The mod's own entry in the mod list opens the settings")
s = s[:i].rstrip() + "\n}\n"
wr(os.path.join(DST, PKG, "ClientInit.java"), s)

# --- horse sync: the hook at once, the tick and the player's draw by plain calls
patch("horsesync/HorseSync.java", [
    ("import net.neoforged.bus.api.IEventBus;\nimport net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;\n", ""),
    ("public static void register(ConfigRegistry.Group config, IEventBus modEventBus) {", "public static void register(ConfigRegistry.Group config) {"),
    ("        modEventBus.addListener(HorseSync::onClientSetup);", "        // EMF calls this back once per rendered entity, right after the pack animation.\n        HorseSyncAnimationHook.register();"),
])
s = rd(os.path.join(DST, PKG, "horsesync/HorseSync.java"))
s = re.sub(r"\n    private static void onClientSetup\(FMLClientSetupEvent event\) \{.*?\n    \}\n", "\n", s, flags=re.S)
wr(os.path.join(DST, PKG, "horsesync/HorseSync.java"), s)
wr(os.path.join(DST, PKG, "horsesync/ClientEventHandler.java"), '''package strm.touchnmotion.horsesync;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import strm.touchnmotion.horsesync.compat.EMFCompat;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * The rider on an animated horse. Fabric has no event for a player's draw: the tick comes from
 * Fabric's own, the draw from {@code mixin/PlayerRenderEventsMixin}.
 */
public class ClientEventHandler {

    private static int cleanupCounter = 0;

    public static void onClientTick() {
        if (!HorseSync.isEnabled()) {
            EMFCompat.horseBodyOffsets.clear();
            return;
        }

        if (++cleanupCounter % 200 != 0) return;

        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            EMFCompat.horseBodyOffsets.clear();
            return;
        }

        var activeHorses = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e instanceof AbstractHorse)
                .map(Entity::getUUID)
                .collect(Collectors.toSet());
        EMFCompat.horseBodyOffsets.keySet().retainAll(activeHorses);
    }

    /** How far up the rider is drawn; zero for none. Inverted and clamped: the horse's body going down in model space is up in the world. */
    private static float offset(Entity player) {
        if (!HorseSync.isEnabled()) return 0f;
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return 0f;
        Float offset = EMFCompat.horseBodyOffsets.get(horse.getUUID());
        return offset == null ? 0f : Math.max(0f, -offset);
    }

    public static void onRenderPlayerPre(Entity player, PoseStack stack) {
        float up = offset(player);
        if (up > 0f) stack.translate(0.0, up, 0.0);
    }

    public static void onRenderPlayerPost(Entity player, PoseStack stack) {
        float up = offset(player);
        if (up > 0f) stack.translate(0.0, -up, 0.0);
    }
}
''')
# --- foot grounding: the rider's draw by plain calls
p = os.path.join(DST, PKG, "footgrounding/FootGroundingFeature.java")
s = rd(p)
s = s.replace("import net.neoforged.bus.api.EventPriority;\nimport net.neoforged.neoforge.client.event.RenderPlayerEvent;\nimport net.neoforged.neoforge.common.NeoForge;\n", "")
s = re.sub(r"        // Innermost: after everyone else has moved the rider.*?\n        NeoForge\.EVENT_BUS\.addListener\(EventPriority\.HIGHEST, FootGroundingFeature::onRenderPlayerPost\);\n", "", s, flags=re.S)
a = s.index("    /** The rider goes down and pitches with its horse. */")
b = s.index("    public static boolean isEnabled()", a)
s = s[:a] + '''    /**
     * The rider goes down and pitches with its horse. Innermost of what moves the rider: called
     * last before the draw and undone first after it ({@code mixin/PlayerRenderEventsMixin}).
     */
    public static void onRenderPlayerPre(net.minecraft.world.entity.player.Player player, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
        riderMoved = false;
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return;
        stack.pushPose();
        riderMoved = HorseFootGrounding.moveRider(horse, player, stack, partialTick);
        if (!riderMoved) stack.popPose();
    }

    public static void onRenderPlayerPost(com.mojang.blaze3d.vertex.PoseStack stack) {
        if (riderMoved) stack.popPose();
        riderMoved = false;
    }

''' + s[b:]
wr(p, s)
wr(os.path.join(DST, PKG, "mixin/PlayerRenderEventsMixin.java"), '''package strm.touchnmotion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.footgrounding.FootGroundingFeature;
import strm.touchnmotion.horsesync.ClientEventHandler;

/**
 * A player about to be drawn, and drawn: what NeoForge tells by its render-player events. The
 * rider is lifted with an animated horse and then, innermost, lowered and pitched with a horse
 * that stands on uneven ground - and both undone in the opposite order.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRenderEventsMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void touchnmotion$beforePlayer(AbstractClientPlayer player, float yaw, float partialTick, PoseStack stack,
                                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        ClientEventHandler.onRenderPlayerPre(player, stack);
        FootGroundingFeature.onRenderPlayerPre(player, stack, partialTick);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void touchnmotion$afterPlayer(AbstractClientPlayer player, float yaw, float partialTick, PoseStack stack,
                                          MultiBufferSource buffers, int light, CallbackInfo ci) {
        FootGroundingFeature.onRenderPlayerPost(stack);
        ClientEventHandler.onRenderPlayerPost(player, stack);
    }
}
''')

# --- networking
wr(os.path.join(DST, PKG, "net/HandsNet.java"), '''package strm.touchnmotion.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The mod's only part that runs on a server: two optional channels, on which the server does
 * one thing - passes what a player's hands are at on to the players near enough to see them. A
 * client on a server without the mod never sends, and guesses as it did; a client without the
 * mod is never sent to.
 */
public final class HandsNet {
    /** No further than this, blocks, is anyone told. */
    private static final double RANGE = 96;
    /** A player's states are passed on no oftener than this many a second; the rest are dropped. */
    private static final int PER_SECOND = 24;

    private static final Map<ServerPlayer, long[]> RATE = new WeakHashMap<>();

    private HandsNet() {
    }

    /** Both sides: the two payloads, either way, and what the server does with them. */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(HandsState.TYPE, HandsState.CODEC);
        PayloadTypeRegistry.playS2C().register(HandsState.TYPE, HandsState.CODEC);
        PayloadTypeRegistry.playC2S().register(HandsAct.TYPE, HandsAct.CODEC);
        PayloadTypeRegistry.playS2C().register(HandsAct.TYPE, HandsAct.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HandsState.TYPE, (state, context) -> {
            ServerPlayer player = context.player();
            if (allowed(player)) relay(player, state.from(player.getId()));
        });
        ServerPlayNetworking.registerGlobalReceiver(HandsAct.TYPE, (act, context) -> {
            ServerPlayer player = context.player();
            if (allowed(player)) relay(player, act.from(player.getId()));
        });
    }

    /** The client: what it does with what the server passes on. Kept apart so no client class is touched on a server. */
    public static void registerClient() {
        Client.register();
    }

    private static final class Client {
        static void register() {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(HandsState.TYPE, (state, context) -> ClientHands.receive(state));
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(HandsAct.TYPE, (act, context) -> ClientHands.receive(act));
        }
    }

    private static boolean allowed(ServerPlayer player) {
        long second = System.nanoTime() / 1_000_000_000L;
        long[] rate = RATE.computeIfAbsent(player, p -> new long[2]);
        if (rate[0] != second) {
            rate[0] = second;
            rate[1] = 0;
        }
        return ++rate[1] <= PER_SECOND;
    }

    /** To every other player of that level near enough, whose game knows the channel. */
    public static void relay(ServerPlayer from, CustomPacketPayload payload) {
        UUID own = from.getUUID();
        for (ServerPlayer other : from.serverLevel().players()) {
            if (other.getUUID().equals(own) || other.distanceToSqr(from) > RANGE * RANGE) continue;
            if (ServerPlayNetworking.canSend(other, payload.type())) ServerPlayNetworking.send(other, payload);
        }
    }
}
''')
patch("net/ClientHands.java", [
    ("import net.neoforged.neoforge.network.PacketDistributor;\n", "import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;\n"),
    ("return connection != null && connection.hasChannel(HandsState.TYPE);", "return connection != null && ClientPlayNetworking.canSend(HandsState.TYPE);"),
    ("PacketDistributor.sendToServer(now);", "ClientPlayNetworking.send(now);"),
    ("PacketDistributor.sendToServer(new HandsAct(", "ClientPlayNetworking.send(new HandsAct("),
])

# --- what a block holds: NeoForge's item handler is not on Fabric; whatever answers to the same two calls is read by them
wr(os.path.join(DST, PKG, "blockuse/Held.java"), '''package strm.touchnmotion.blockuse;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * What a mod's block holds, read without the loader's own inventory type: anything that answers
 * to {@code getSlots()} and {@code getStackInSlot(int)} - NeoForge's item handler, and what Fabric
 * ports of the same mods carry in its place - is read by those two calls. Anything else holds nothing.
 */
public final class Held {
    private Held() {
    }

    /** The stacks an inventory holds; empty for what is not one. */
    public static ItemStack[] stacks(Object inventory) {
        if (inventory == null) return new ItemStack[0];
        try {
            Method slots = inventory.getClass().getMethod("getSlots");
            Method stack = inventory.getClass().getMethod("getStackInSlot", int.class);
            int n = (Integer) slots.invoke(inventory);
            ItemStack[] out = new ItemStack[n];
            for (int i = 0; i < n; i++) out[i] = stack.invoke(inventory, i) instanceof ItemStack s ? s : ItemStack.EMPTY;
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ItemStack[0];
        }
    }

    /** How much fluid a tank holds, by its {@code getFluidAmount()}; zero for what has none. */
    public static long fluid(Object tank) {
        if (tank == null) return 0;
        try {
            return ((Number) tank.getClass().getMethod("getFluidAmount").invoke(tank)).longValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }
}
''')
patch("blockuse/ItemRest.java", [
    ("import net.neoforged.neoforge.items.IItemHandler;\n", ""),
    ("""        if (!(value instanceof IItemHandler handler)) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) count += handler.getStackInSlot(i).getCount();
        return count;""", """        int count = 0;
        for (ItemStack stack : Held.stacks(value)) count += stack.getCount();
        return count;"""),
    ("""        if (!(value instanceof IItemHandler handler)) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) return handler.getStackInSlot(i).getItem().toString();
        }
        return null;""", """        for (ItemStack stack : Held.stacks(value)) {
            if (!stack.isEmpty()) return stack.getItem().toString();
        }
        return null;"""),
])
patch("blockuse/create/Basin.java", [
    ("import net.neoforged.neoforge.items.IItemHandler;\n", ""),
    ("""        if (!(inventory instanceof IItemHandler handler)) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) count += handler.getStackInSlot(i).getCount();
        return count;""", """        int count = 0;
        for (net.minecraft.world.item.ItemStack stack : Held.stacks(inventory)) count += stack.getCount();
        return count;"""),
])
patch("blockuse/create/ItemDrain.java", [
    ("import net.neoforged.neoforge.fluids.capability.templates.FluidTank;\n", ""),
    ("return HANDLER.read(tank.get(entity)) instanceof FluidTank handler ? handler.getFluidAmount() : 0;", "return (int) Math.min(Integer.MAX_VALUE, Held.fluid(HANDLER.read(tank.get(entity))));"),
])
patch("blockuse/vanilla/Composter.java", [
    ("import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;\n", ""),
    ("return !stack.isEmpty() && (stack.getItemHolder().getData(NeoForgeDataMaps.COMPOSTABLES) != null\n                || ComposterBlock.COMPOSTABLES.containsKey(stack.getItem()));",
     "return !stack.isEmpty() && ComposterBlock.COMPOSTABLES.containsKey(stack.getItem());"),
])

# --- what NeoForge adds to the game's own classes, said the vanilla way
patch("blockuse/vanilla/FlowerPot.java", [
    ("""        return ((FlowerPotBlock) Blocks.FLOWER_POT).getFullPotsView()
                .getOrDefault(BuiltInRegistries.BLOCK.getKey(item.getBlock()), () -> Blocks.AIR).get() != Blocks.AIR;""",
     """        // Every pot with something in it knows what: the plants that go in a pot are the ones some pot holds.
        if (POTTED.isEmpty()) {
            for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) POTTED.add(pot.getPotted());
            }
        }
        return POTTED.contains(item.getBlock());"""),
    ("    private static boolean plantable(ItemStack stack) {", "    private static final java.util.Set<net.minecraft.world.level.block.Block> POTTED = new java.util.HashSet<>();\n\n    private static boolean plantable(ItemStack stack) {"),
])
patch("blockuse/create/BlazeBurner.java", [
    ("if (stack.getBurnTime(null) > 0) return true;", "if (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(stack)) return true;"),
])
patch("ride/ChestInBow.java", [("boat.getVariant().isRaft()", "(boat.getVariant() == net.minecraft.world.entity.vehicle.Boat.Type.BAMBOO)")])
patch("ride/BoatSeats.java", [("boat.getVariant().isRaft()", "(boat.getVariant() == net.minecraft.world.entity.vehicle.Boat.Type.BAMBOO)")])
patch("mixin/BoatChestMixin.java", [
    ("public abstract class BoatChestMixin {\n", """public abstract class BoatChestMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.Map<Boat.Type, com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.client.model.ListModel<Boat>>> boatResources;
"""),
    ("((BoatRenderer) (Object) this).getModelWithLocation(boat).getSecond()", "boatResources.get(boat.getVariant()).getSecond()"),
])
print("ported")
