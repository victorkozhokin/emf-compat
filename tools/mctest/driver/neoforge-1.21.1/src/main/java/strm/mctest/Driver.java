package strm.mctest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Runs scripts that tools/mctest drops into {@code <gameDir>/mctest/inbox}, one step at a time on
 * the client thread, and answers in {@code outbox}. Also keeps {@code status.json} fresh so the
 * launcher can tell when the world is up.
 *
 * <p>Steps run back to back within a tick until a {@code wait}. A screenshot captures the most
 * recently rendered frame, so give a change at least one tick before shooting it.</p>
 */
public final class Driver {

    private static final Logger LOG = LoggerFactory.getLogger("mctest");
    private static final Gson GSON = new Gson();

    private static Path dir;
    private static Script current;
    private static final Set<KeyMapping> HELD = new LinkedHashSet<>();
    private static int statusCountdown;
    /**
     * Yaw offset from where the player faces, pitch and distance of the orbit camera; null = off.
     * Six values: an absolute yaw, pitch, distance and the pinned world point it orbits.
     */
    private static volatile float[] orbit;
    private static boolean hideScreen;
    public static boolean hideScreen() { return hideScreen; }

    private Driver() {
    }

    /** Read by the camera mixin every frame. */
    public static float[] orbit() {
        return orbit;
    }

    public static boolean enabled() {
        return System.getProperty("mctest") != null;
    }

    /** Once per client tick, after the game's own tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (dir == null) {
            dir = mc.gameDirectory.toPath().resolve("mctest");
            LOG.info("mctest driver active, watching {}", dir);
        }
        // Held keys are re-asserted every tick: opening a screen releases every mapping.
        for (KeyMapping key : HELD) {
            key.setDown(true);
        }
        if (--statusCountdown <= 0) {
            statusCountdown = 10;
            writeStatus(mc);
        }
        try {
            if (current == null) {
                current = poll();
            }
            if (current != null) {
                current.advance(mc);
                if (current.done()) {
                    write(dir.resolve("outbox").resolve(current.id + ".json"), current.answer());
                    current = null;
                }
            }
        } catch (Throwable t) {
            LOG.error("mctest driver failed", t);
            current = null;
        }
    }

    private static Script poll() throws IOException {
        Path inbox = dir.resolve("inbox");
        if (!Files.isDirectory(inbox)) {
            return null;
        }
        Path next;
        try (Stream<Path> files = Files.list(inbox)) {
            next = files.filter(p -> p.toString().endsWith(".json")).min(Comparator.naturalOrder()).orElse(null);
        }
        if (next == null) {
            return null;
        }
        JsonObject json = GSON.fromJson(Files.readString(next, StandardCharsets.UTF_8), JsonObject.class);
        Files.delete(next);
        return new Script(json.get("id").getAsString(), json.getAsJsonArray("steps"));
    }

    private static void write(Path target, JsonObject json) {
        try {
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("mctest: could not write {}", target, e);
        }
    }

    private static void writeStatus(Minecraft mc) {
        JsonObject s = new JsonObject();
        s.addProperty("inWorld", mc.level != null && mc.player != null);
        s.addProperty("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        s.addProperty("fps", mc.getFps());
        s.addProperty("paused", mc.isPaused());
        s.addProperty("busy", current != null);
        write(dir.resolve("status.json"), s);
    }

    // ---------------------------------------------------------------------------------------

    private static final class Script {
        final String id;
        final JsonArray steps;
        final JsonArray results = new JsonArray();
        int index;
        int waiting;
        JsonObject until;
        JsonObject untilResult;
        int untilTicks;
        int untilShots;

        /**
         * {"until": {"z<": 413.7, "timeout": 80, "shots": "pit", "every": 2}} - waits until every
         * condition holds (x/y/z with &lt; or &gt;, "onGround": bool, "swing&lt;"/"swing&gt;": ParCool's
         * bar swing in rad/tick), or the timeout (ticks, default
         * 200) runs out - with no condition it is a plain wait of that long; with "shots", takes a screenshot every "every" ticks while it waits.
         * The result says how many ticks it took and whether it timed out.
         */
        boolean untilDone(Minecraft mc) {
            LocalPlayer p = mc.player;
            boolean met = p != null;
            boolean any = false;
            for (Map.Entry<String, JsonElement> c : until.entrySet()) {
                String k = c.getKey();
                if (p == null || k.equals("timeout") || k.equals("shots") || k.equals("every")) {
                    continue;
                }
                any = true;
                if (k.equals("onGround")) {
                    met &= p.onGround() == c.getValue().getAsBoolean();
                    continue;
                }
                if (k.startsWith("swing")) {
                    // ParCool's bar swing, rad/tick (HangDown angular speed); 0 when not on a bar
                    double w = barSwing(p);
                    double limit = c.getValue().getAsDouble();
                    met &= k.charAt(5) == '<' ? w < limit : w > limit;
                    continue;
                }
                double at = switch (k.charAt(0)) {
                    case 'x' -> p.getX();
                    case 'y' -> p.getY();
                    case 'z' -> p.getZ();
                    default -> throw new IllegalArgumentException("until: x/y/z with < or >, or onGround");
                };
                double limit = c.getValue().getAsDouble();
                met &= k.charAt(1) == '<' ? at < limit : at > limit;
            }
            met &= any; // no condition: just the timeout, shooting as it goes
            int timeout = until.has("timeout") ? until.get("timeout").getAsInt() : 200;
            if (met || untilTicks >= timeout) {
                untilResult.addProperty("ticks", untilTicks);
                if (!met && any) {
                    untilResult.addProperty("timedOut", true);
                }
                if (p != null) {
                    untilResult.addProperty("pos", String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f",
                            p.getX(), p.getY(), p.getZ()));
                    double w = barSwing(p);
                    if (w != 0) untilResult.addProperty("swing", Math.round(w * 1000) / 1000.0);
                }
                until = null;
                return true;
            }
            if (until.has("shots")) {
                int every = until.has("every") ? until.get("every").getAsInt() : 2;
                if (untilTicks % every == 0) {
                    String name = (until.get("shots").getAsString() + "_" + String.format("%02d", untilShots++))
                            .replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
                    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
                    JsonArray list = untilResult.has("screenshots") ? untilResult.getAsJsonArray("screenshots") : new JsonArray();
                    list.add(mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath().toString());
                    untilResult.add("screenshots", list);
                }
            }
            untilTicks++;
            return false;
        }

        Script(String id, JsonArray steps) {
            this.id = id;
            this.steps = steps;
        }

        boolean done() {
            return index >= steps.size() && waiting <= 0;
        }

        JsonObject answer() {
            JsonObject a = new JsonObject();
            a.addProperty("id", id);
            a.add("results", results);
            return a;
        }

        void advance(Minecraft mc) {
            if (waiting > 0 && --waiting > 0) {
                return;
            }
            if (until != null && !untilDone(mc)) {
                return;
            }
            while (index < steps.size()) {
                JsonObject step = steps.get(index++).getAsJsonObject();
                JsonObject result = new JsonObject();
                result.addProperty("step", index - 1);
                if (step.has("until")) {
                    until = step.getAsJsonObject("until");
                    untilResult = result;
                    untilTicks = 0;
                    untilShots = 0;
                    results.add(result);
                    if (!untilDone(mc)) {
                        return;
                    }
                    continue;
                }
                try {
                    int wait = run(mc, step, result);
                    results.add(result);
                    if (wait > 0) {
                        waiting = wait;
                        return;
                    }
                } catch (Throwable t) {
                    result.addProperty("error", t.toString());
                    results.add(result);
                }
            }
        }
    }

    /** Runs one step; returns how many ticks to wait before the next. */
    private static int run(Minecraft mc, JsonObject step, JsonObject result) throws ReflectiveOperationException {
        LocalPlayer player = mc.player;
        for (Map.Entry<String, JsonElement> e : step.entrySet()) {
            String kind = e.getKey();
            JsonElement v = e.getValue();
            switch (kind) {
                case "wait" -> {
                    return Math.max(1, v.getAsInt());
                }
                case "cmd" -> {
                    String cmd = v.getAsString();
                    requirePlayer(player).connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                }
                case "chat" -> requirePlayer(player).connection.sendChat(v.getAsString());
                case "hold" -> press(mc, key(mc, v.getAsString()), true);
                case "release" -> press(mc, key(mc, v.getAsString()), false);
                case "releaseAll" -> {
                    HELD.forEach(k -> k.setDown(false));
                    HELD.clear();
                }
                // getKey() is gone on newer versions; the saved binding names the same key everywhere.
                case "click" -> KeyMapping.click(InputConstants.getKey(key(mc, v.getAsString()).saveString()));
                case "slot" -> requirePlayer(player).getInventory().selected = v.getAsInt();
                case "camera" -> mc.options.setCameraType(switch (v.getAsString()) {
                    case "first" -> CameraType.FIRST_PERSON;
                    case "back" -> CameraType.THIRD_PERSON_BACK;
                    case "front" -> CameraType.THIRD_PERSON_FRONT;
                    default -> throw new IllegalArgumentException("camera: first, back or front");
                });
                case "orbit" -> {
                    // [yawOffset, pitch, distance] around the player (90 = its left side), or false.
                    // A fourth element true pins the camera where it is now, in the world: it stops
                    // following the player, so how the model moves against the scenery shows.
                    if (v.isJsonArray()) {
                        JsonArray a = v.getAsJsonArray();
                        float distance = a.size() > 2 ? a.get(2).getAsFloat() : 4f;
                        if (a.size() > 3 && a.get(3).getAsBoolean()) {
                            LocalPlayer p = requirePlayer(player);
                            orbit = new float[]{p.getYRot() + a.get(0).getAsFloat(), a.get(1).getAsFloat(), distance,
                                    (float) p.getX(), (float) p.getEyeY(), (float) p.getZ()};
                        } else {
                            orbit = new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), distance};
                        }
                        if (mc.options.getCameraType().isFirstPerson()) {
                            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                        }
                    } else {
                        orbit = null;
                    }
                }
                case "parcool" -> result.add("parcool", parcool(mc));
                case "look" -> {
                    JsonArray a = v.getAsJsonArray();
                    float yaw = a.get(0).getAsFloat();
                    float pitch = a.get(1).getAsFloat();
                    LocalPlayer p = requirePlayer(player);
                    p.setYRot(yaw);
                    p.setXRot(pitch);
                    p.yRotO = yaw;
                    p.xRotO = pitch;
                    p.setYHeadRot(yaw);
                    p.yBodyRot = yaw;
                    p.yBodyRotO = yaw;
                }
                case "hideScreen" -> hideScreen = v.getAsBoolean();
                case "steeringDrag" -> {
                    Object handler = Class.forName("dev.simulated_team.simulated.index.SimClickInteractions")
                            .getField("STEERING_WHEEL_MANAGER").get(null);
                    Class<?> manager = Class.forName("dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager");
                    Class<?> interaction = Class.forName("dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction");
                    if (!(boolean) manager.getMethod("isActive", interaction).invoke(null, handler))
                        throw new IllegalStateException("Steering wheel is not held: click its rim first");
                    handler.getClass().getMethod("activeOnMouseMove", double.class, double.class).invoke(handler, v.getAsDouble(), 0d);
                }
                case "throttleHold" -> {
                    Object handler=Class.forName("dev.simulated_team.simulated.index.SimClickInteractions")
                            .getField("THROTTLE_LEVER_MANAGER").get(null);
                    JsonArray position=v.getAsJsonArray();
                    var block=new net.minecraft.core.BlockPos(position.get(0).getAsInt(),position.get(1).getAsInt(),position.get(2).getAsInt());
                    handler.getClass().getMethod("startHold",net.minecraft.world.level.Level.class,
                            net.minecraft.world.entity.player.Player.class,net.minecraft.core.BlockPos.class)
                            .invoke(handler,requirePlayer(player).level(),player,block);
                }
                case "throttleDrag" -> {
                    Object handler=Class.forName("dev.simulated_team.simulated.index.SimClickInteractions")
                            .getField("THROTTLE_LEVER_MANAGER").get(null);
                    if (!(boolean)handler.getClass().getMethod("isActive").invoke(handler))
                        throw new IllegalStateException("Throttle is not held: click it first");
                    handler.getClass().getMethod("activeOnMouseMove",double.class,double.class).invoke(handler,0d,v.getAsDouble());
                }
                case "steeringRelease" -> Class.forName("dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager")
                        .getMethod("stop").invoke(null);
                case "hideGui" -> mc.options.hideGui = v.getAsBoolean();
                case "closeScreen" -> mc.setScreen(null);
                case "screenshot" -> {
                    String name = v.getAsString().replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
                    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
                    result.addProperty("screenshot",
                            mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath().toString());
                }
                case "state" -> result.add("state", state(mc));
                case "config" -> config(v.getAsJsonObject());
                case "fade" -> result.add("fade", fade(mc));
                case "packs" -> {
                    result.add("packs", packs(mc, v.getAsJsonArray()));
                    return 1;
                }
                case "model" -> result.add("model", model(mc, v));
                case "log" -> LOG.info("[mctest] {}", v.getAsString());
                case "maxFps" -> {
                    mc.options.framerateLimit().set(v.getAsInt());
                    mc.getWindow().setFramerateLimit(v.getAsInt());
                }
                case "frames" -> {
                    if (v.getAsString().equals("start")) Frames.start();
                    else result.add("frames", Frames.report());
                }
                case "bot" -> result.add("bot", Bot.run(mc, v.getAsJsonObject()));
                case "menuClick" -> {
                    // A click on a slot of the open menu, as the mouse makes one: {"slot": 1, "button": 1, "type": "PICKUP"}.
                    JsonObject c = v.getAsJsonObject();
                    LocalPlayer p = requirePlayer(player);
                    mc.gameMode.handleInventoryMouseClick(p.containerMenu.containerId, c.get("slot").getAsInt(),
                            c.has("button") ? c.get("button").getAsInt() : 0,
                            net.minecraft.world.inventory.ClickType.valueOf(c.has("type") ? c.get("type").getAsString() : "PICKUP"), p);
                    result.addProperty("menu", p.containerMenu.getClass().getSimpleName());
                }
                default -> throw new IllegalArgumentException("unknown step: " + kind);
            }
        }
        return 0;
    }

    private static LocalPlayer requirePlayer(LocalPlayer player) {
        if (player == null) {
            throw new IllegalStateException("not in a world");
        }
        return player;
    }

    private static KeyMapping key(Minecraft mc, String name) {
        return switch (name) {
            case "forward" -> mc.options.keyUp;
            case "back" -> mc.options.keyDown;
            case "left" -> mc.options.keyLeft;
            case "right" -> mc.options.keyRight;
            case "jump" -> mc.options.keyJump;
            case "sneak" -> mc.options.keyShift;
            case "sprint" -> mc.options.keySprint;
            case "attack" -> mc.options.keyAttack;
            case "use" -> mc.options.keyUse;
            case "drop" -> mc.options.keyDrop;
            case "swap" -> mc.options.keySwapOffhand;
            case "inventory" -> mc.options.keyInventory;
            default -> {
                // Any other mapping by its translation key, e.g. Carry On's "key.carry.desc".
                for (KeyMapping k : mc.options.keyMappings) {
                    if (k.getName().equals(name)) {
                        yield k;
                    }
                }
                throw new IllegalArgumentException("unknown key: " + name);
            }
        };
    }

    /**
     * Presses or releases every mapping bound to the same physical key as {@code base}, as a real
     * key press would. Mods keep their own mappings on shared keys — Carry On picks up with its
     * "Carry" mapping on shift, which the vanilla sneak mapping alone never triggers.
     */
    private static void press(Minecraft mc, KeyMapping base, boolean down) {
        String bound = base.saveString();
        for (KeyMapping k : mc.options.keyMappings) {
            if (k == base || k.saveString().equals(bound)) {
                if (down) {
                    HELD.add(k);
                } else {
                    HELD.remove(k);
                }
                k.setDown(down);
            }
        }
    }

    private static JsonObject state(Minecraft mc) {
        JsonObject s = new JsonObject();
        s.addProperty("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        s.addProperty("camera", mc.options.getCameraType().name());
        s.addProperty("fps", mc.getFps());
        List<String> held = new ArrayList<>();
        HELD.forEach(k -> held.add(k.getName()));
        s.add("heldKeys", GSON.toJsonTree(held));
        LocalPlayer p = mc.player;
        if (p == null) {
            return s;
        }
        s.addProperty("name", p.getName().getString());
        s.addProperty("dimension", p.level().dimension().location().toString());
        s.add("pos", GSON.toJsonTree(new double[]{p.getX(), p.getY(), p.getZ()}));
        s.add("rot", GSON.toJsonTree(new float[]{p.getYRot(), p.getXRot()}));
        s.addProperty("pose", p.getPose().name());
        s.addProperty("crouching", p.isCrouching());
        s.addProperty("onGround", p.onGround());
        s.addProperty("usingItem", p.isUsingItem());
        s.addProperty("screenHidden", hideScreen);
        s.addProperty("slot", p.getInventory().selected);
        s.addProperty("mainHand", item(p.getMainHandItem()));
        s.addProperty("offHand", item(p.getOffhandItem()));
        s.addProperty("gameMode", mc.gameMode == null ? null : mc.gameMode.getPlayerMode().getName());
        s.add("target", target(mc, p));
        s.addProperty("dayTime", p.level().getDayTime());
        return s;
    }

    /** What the crosshair is on — Better Combat, for one, will not swing at a block it could mine. */
    private static JsonObject target(Minecraft mc, LocalPlayer p) {
        HitResult hit = mc.hitResult;
        if (hit == null) {
            return null;
        }
        JsonObject t = new JsonObject();
        t.addProperty("type", hit.getType().name());
        t.addProperty("distance", Math.round(Math.sqrt(hit.getLocation().distanceToSqr(p.getEyePosition())) * 100) / 100.0);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block) {
            t.addProperty("block", String.valueOf(BuiltInRegistries.BLOCK.getKey(
                    p.level().getBlockState(block.getBlockPos()).getBlock())));
            t.addProperty("pos", block.getBlockPos().toShortString());
            // Where the hit is in the world: on a Sable craft the block is in a plot far off.
            Vec3 world = hit.getLocation();
            try {
                Object helper = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
                world = (Vec3) helper.getClass().getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class,
                        net.minecraft.core.Position.class).invoke(helper, p.level(), world);
            } catch (ReflectiveOperationException ignored) {
            }
            t.addProperty("world", String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f", world.x, world.y, world.z));
            var entity = p.level().getBlockEntity(block.getBlockPos());
            if (entity != null) {
                for (String method : new String[]{"getIndependentAngle", "getRenderAngle", "getState"}) {
                    try {
                        Object value = method.equals("getState") ? entity.getClass().getMethod(method).invoke(entity)
                                : entity.getClass().getMethod(method, float.class)
                                .invoke(entity, mc.getTimer().getGameTimeDeltaPartialTick(false));
                        if (value instanceof Number number) t.addProperty(method, number);
                    } catch (NoSuchMethodException ignored) {
                    } catch (ReflectiveOperationException failure) {
                        t.addProperty("probeError", failure.toString());
                    }
                }
            }
        } else if (hit instanceof EntityHitResult entity) {
            t.addProperty("entity", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getEntity().getType())));
        }
        return t;
    }

    /**
     * Switches the resource packs on and reloads, the way the pack screen does: every pack named
     * here is enabled in the given order (later wins), everything else is off. A name is matched
     * against the pack ids, exactly or as a substring, so {@code "FreshAnimations"} is enough.
     *
     * <p>The reload runs after this returns and takes a while; the next script is only answered
     * once the client ticks again, so a following {@code mc_steps} call is the wait.</p>
     */
    private static JsonArray packs(Minecraft mc, JsonArray wanted) {
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        List<String> ids = new ArrayList<>();
        ids.add("vanilla");
        JsonArray chosen = new JsonArray();
        for (JsonElement e : wanted) {
            String name = e.getAsString();
            String id = repo.getAvailableIds().stream()
                    .filter(a -> a.equals(name) || a.equals("file/" + name))
                    .findFirst()
                    .orElseGet(() -> repo.getAvailableIds().stream()
                            .filter(a -> a.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "no pack matching " + name + " in " + repo.getAvailableIds())));
            if (!ids.contains(id)) {
                ids.add(id);
            }
            chosen.add(id);
        }
        repo.setSelected(ids);
        mc.options.updateResourcePacks(repo);  // saves the selection and reloads if it changed
        return chosen;
    }

    /**
     * The model the renderer will use for the nearest entity of a type ({@code "player"} for the
     * local player): every {@code ModelPart} field of the model class, with what the part actually
     * is, how many cubes it still has and its current transform.
     *
     * <p>This is the probe for attachments that sit in the wrong place under a resource pack: when
     * EMF loads a custom model, the part a mod reads stays as a container, its cubes move to a
     * custom child, and a part the pack replaced reports {@code cubes: 0} — which is what mods that
     * measure the model (a hat placed on top of the head cube) silently fall back from.</p>
     */
    private static JsonObject model(Minecraft mc, JsonElement arg) throws ReflectiveOperationException {
        JsonObject spec = arg.isJsonObject() ? arg.getAsJsonObject() : null;
        String wanted = spec != null ? spec.get("entity").getAsString() : arg.getAsString();
        int depth = spec != null && spec.has("depth") ? spec.get("depth").getAsInt() : 2;
        String only = spec != null && spec.has("part") ? spec.get("part").getAsString() : null;
        LivingEntity entity = nearest(mc, wanted);
        JsonObject out = new JsonObject();
        out.addProperty("entity", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
        Object renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        out.addProperty("renderer", renderer.getClass().getName());
        // The renderer's model, found by field TYPE rather than by name: the getter's type
        // parameters differ per version, and a name would not survive remapping.
        Object model = null;
        for (Class<?> c = renderer.getClass(); c != null && model == null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (EntityModel.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    model = f.get(renderer);
                    break;
                }
            }
        }
        if (model == null) {
            out.addProperty("model", "no model on this renderer");
            return out;
        }
        out.addProperty("model", model.getClass().getName());
        Object emfRoot = emfRoot(model);
        out.addProperty("emf", emfRoot != null);
        JsonObject parts = new JsonObject();
        // Prefer the model's root part: its children are named by the model definition, the same
        // on every mapping. Field names only survive where the game runs on official mappings.
        ModelPart root = emfRoot instanceof ModelPart p ? p : rootPartOf(model);
        if (root != null) {
            for (Map.Entry<String, ModelPart> e : Driver.<Map<String, ModelPart>>field(root, "children", Map.of()).entrySet()) {
                if (only == null || only.equals(e.getKey())) {
                    parts.add(e.getKey(), part(e.getValue(), depth));
                }
            }
        }
        for (Class<?> c = model.getClass(); root == null && c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                String name = f.getName();
                if (!ModelPart.class.isAssignableFrom(f.getType()) || parts.has(name)
                        || name.startsWith("emf$") || (only != null && !only.equals(name))) {
                    continue;
                }
                f.setAccessible(true);
                Object part = f.get(model);
                if (part != null) {
                    parts.add(name, part((ModelPart) part, depth));
                }
            }
        }
        out.add("parts", parts);
        return out;
    }

    /**
     * The one {@code ModelPart} field of a model that is nobody else's descendant — the root.
     * Null when the model keeps no root (pre-1.21.2 humanoids), which is also where field names
     * are readable, so the caller falls back to those.
     */
    private static ModelPart rootPartOf(Object model) throws ReflectiveOperationException {
        List<ModelPart> fields = new ArrayList<>();
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (ModelPart.class.isAssignableFrom(f.getType()) && !f.getName().startsWith("emf$")) {
                    f.setAccessible(true);
                    ModelPart part = (ModelPart) f.get(model);
                    if (part != null && fields.stream().noneMatch(p -> p == part)) {
                        fields.add(part);
                    }
                }
            }
        }
        Set<ModelPart> descendants = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ModelPart part : fields) {
            collect(part, descendants);
        }
        List<ModelPart> roots = fields.stream().filter(p -> !descendants.contains(p)).toList();
        return roots.size() == 1 ? roots.get(0) : null;
    }

    private static void collect(ModelPart part, Set<ModelPart> into) {
        for (ModelPart child : Driver.<Map<String, ModelPart>>field(part, "children", Map.of()).values()) {
            if (into.add(child)) {
                collect(child, into);
            }
        }
    }

    /** EMF's root for this model, or null when EMF is absent or leaves the model alone. */
    private static Object emfRoot(Object model) {
        try {
            if (!(Boolean) model.getClass().getMethod("emf$isEMFModel").invoke(model)) {
                return null;
            }
            return model.getClass().getMethod("emf$getEMFRootModel").invoke(model);
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
            return null;
        }
    }

    private static JsonObject part(ModelPart part, int depth) throws ReflectiveOperationException {
        JsonObject o = new JsonObject();
        o.addProperty("is", part.getClass().getSimpleName());
        List<Cube> cubes = field(part, "cubes", List.of());
        o.addProperty("cubes", cubes.size());
        if (!cubes.isEmpty()) {
            // What a mod measuring the model reads: a hat goes on top of maxY - minY, and is
            // scaled by the widest side. Gone the moment a pack replaces the part.
            Cube first = cubes.get(0);
            o.add("cube0", round(first.minX, first.minY, first.minZ, first.maxX, first.maxY, first.maxZ));
        }
        o.add("pos", round(part.x, part.y, part.z));
        o.add("rot", round(part.xRot, part.yRot, part.zRot));
        if (part.xScale != 1 || part.yScale != 1 || part.zScale != 1) {
            o.add("scale", round(part.xScale, part.yScale, part.zScale));
        }
        if (!part.visible) {
            o.addProperty("visible", false);
        }
        Map<String, ModelPart> children = field(part, "children", Map.of());
        if (depth > 0 && !children.isEmpty()) {
            JsonObject kids = new JsonObject();
            for (Map.Entry<String, ModelPart> e : children.entrySet()) {
                kids.add(e.getKey(), part(e.getValue(), depth - 1));
            }
            o.add("children", kids);
        } else if (!children.isEmpty()) {
            o.addProperty("children", String.join(", ", children.keySet()));
        }
        return o;
    }

    // A part's cubes and children are not reachable at compile time on every version, and their
    // field NAMES are only readable where the game runs on official mappings — so both are found
    // by their generic type, which names the real classes whatever the mappings are called.
    private static final Field CUBES = partField(List.class, Cube.class);
    private static final Field CHILDREN = partField(Map.class, ModelPart.class);

    private static Field partField(Class<?> raw, Class<?> element) {
        for (Field f : ModelPart.class.getDeclaredFields()) {
            if (!raw.isAssignableFrom(f.getType()) || !(f.getGenericType() instanceof ParameterizedType p)) {
                continue;
            }
            Type[] args = p.getActualTypeArguments();
            if (args.length > 0 && args[args.length - 1] == element) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(ModelPart part, String name, T fallback) {
        Field f = name.equals("cubes") ? CUBES : CHILDREN;
        if (f == null) {
            return fallback;
        }
        try {
            Object value = f.get(part);
            return value == null ? fallback : (T) value;
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    private static JsonArray round(float... values) {
        JsonArray a = new JsonArray();
        for (float value : values) {
            a.add(Math.round(value * 1000f) / 1000f);
        }
        return a;
    }

    private static LivingEntity nearest(Minecraft mc, String name) {
        LocalPlayer player = requirePlayer(mc.player);
        if (name.equals("player") || name.equals("self")) {
            return player;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living)) {
                continue;
            }
            String id = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
            if (!id.equals(name) && !id.endsWith(":" + name)) {
                continue;
            }
            double distance = e.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        if (best == null) {
            throw new IllegalStateException("no " + name + " nearby");
        }
        return best;
    }

    // --- Probes into the EMF Compat core, by reflection so the driver never depends on it. ---

    private static final String CORE = "strm.emfcompat.core.";

    /** Sets core config options in memory (not saved), e.g. {"core.smoothPoseTransitions": false}. */
    private static void config(JsonObject values) throws ReflectiveOperationException {
        Method set = Class.forName(CORE + "EMFCompatConfig").getMethod("setBoolean", String.class, boolean.class);
        for (Map.Entry<String, JsonElement> e : values.entrySet()) {
            set.invoke(null, e.getKey(), e.getValue().getAsBoolean());
        }
    }

    /**
     * Who poses the local player and how far each part's fade has got: {"sources": [...],
     * "parts": {"right_arm": "0.62 in"}} — "in" while a source holds the part, "out" while it is
     * being faded back into the pack's animation. No "parts" means nothing is fading.
     */
    private static JsonObject fade(Minecraft mc) throws ReflectiveOperationException {
        UUID uuid = requirePlayer(mc.player).getUUID();
        JsonObject out = new JsonObject();
        Class<?> pm = Class.forName(CORE + "PoseManager");
        JsonArray sources = new JsonArray();
        if (((Map<?, ?>) pm.getField("entitySavedPoses").get(null)).containsKey(uuid)) {
            sources.add("default");
        }
        Map<?, ?> bySource = (Map<?, ?>) ((Map<?, ?>) pm.getField("entitySavedPosesBySource").get(null)).get(uuid);
        if (bySource != null) {
            bySource.keySet().forEach(k -> sources.add(String.valueOf(k)));
        }
        out.add("sources", sources);
        Field statesField = Class.forName(CORE + "PoseInterpolator").getDeclaredField("STATES");
        statesField.setAccessible(true);
        Map<?, ?> mine = (Map<?, ?>) ((Map<?, ?>) statesField.get(null)).get(uuid);
        if (mine != null && !mine.isEmpty()) {
            JsonObject parts = new JsonObject();
            for (Map.Entry<?, ?> e : mine.entrySet()) {
                Object f = e.getValue();
                Field weight = f.getClass().getDeclaredField("weight");
                Field posed = f.getClass().getDeclaredField("posed");
                weight.setAccessible(true);
                posed.setAccessible(true);
                String text = String.format(java.util.Locale.ROOT, "%.2f %s", weight.getFloat(f), posed.getBoolean(f) ? "in" : "out");
                try {
                    // Newer cores: a self-blended pose taking a part over from another one.
                    Field handover = f.getClass().getDeclaredField("handover");
                    handover.setAccessible(true);
                    if (handover.getFloat(f) < 1f) {
                        text += String.format(java.util.Locale.ROOT, " handover %.2f", handover.getFloat(f));
                    }
                    Field self = f.getClass().getDeclaredField("selfBlended");
                    self.setAccessible(true);
                    if (self.getBoolean(f)) {
                        text += " self";
                    }
                } catch (NoSuchFieldException ignored) {
                    // an older core
                }
                parts.addProperty(String.valueOf(e.getKey()), text);
            }
            out.add("parts", parts);
        }
        return out;
    }

    /**
     * What ParCool 4 is animating on the local player: the running animation sets (newest last),
     * and the transform it hands the model this frame — overwriting or blending, the blend factor
     * and the parts it drives. By reflection, like the core probes.
     */
    private static JsonObject parcool(Minecraft mc) throws ReflectiveOperationException {
        LocalPlayer p = requirePlayer(mc.player);
        JsonObject out = new JsonObject();
        Object animator = call(p, "getParCoolPlayerAnimator");
        Object processor = read(animator, "animationProcessor");
        JsonArray running = new JsonArray();
        for (Object entry : (List<?>) read(processor, "animators")) {
            running.add(String.valueOf(call(call(entry, "registration"), "location")));
        }
        out.add("running", running);
        Object transform = call(animator, "getCurrentTransformation");
        if (transform != null) {
            out.addProperty("overwriting", (Boolean) call(transform, "isOverwriting"));
            out.addProperty("blend", Math.round((Float) call(transform, "blendFactor") * 100f) / 100f);
            Map<?, ?> parts = (Map<?, ?>) call(call(transform, "transformation"), "transforms");
            JsonArray names = new JsonArray();
            parts.keySet().forEach(k -> names.add(String.valueOf(k)));
            out.add("parts", names);
            for (Map.Entry<?, ?> part : parts.entrySet()) {
                if (!String.valueOf(part.getKey()).equals("BODY")) continue;
                // The torso transform ParCool puts on the whole pose stack: translation and rotation.
                out.addProperty("bodyTranslation", String.valueOf(call(part.getValue(), "translation")));
                Object q = call(part.getValue(), "rotation");
                org.joml.Vector3f euler = new org.joml.Quaternionf((org.joml.Quaternionfc) q).getEulerAnglesYXZ(new org.joml.Vector3f());
                out.addProperty("bodyEulerYXZdeg", String.format(Locale.ROOT, "x=%.1f y=%.1f z=%.1f",
                        Math.toDegrees(euler.x), Math.toDegrees(euler.y), Math.toDegrees(euler.z)));
            }
        }
        return out;
    }

    private static double barSwing(LocalPlayer p) {
        try {
            Class<?> pk = Class.forName("com.alrex.parcool.common.Parkourability");
            Object ability = pk.getMethod("get", net.minecraft.world.entity.player.Player.class).invoke(null, p);
            Object entry = Class.forName("com.alrex.parcool.common.action.ParCoolActions").getField("HANG_DOWN").get(null);
            Object action = pk.getMethod("get", Class.forName("com.alrex.parcool.api.action.ActionEntry")).invoke(ability, entry);
            if (!(Boolean) action.getClass().getMethod("isDoing").invoke(action)) return 0;
            return (Float) action.getClass().getMethod("getAngularSpeed", float.class).invoke(action, 1f);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** A no-argument method of a class another module keeps private. */
    private static Object call(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // keep looking up the hierarchy
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static String item(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount();
    }
}
