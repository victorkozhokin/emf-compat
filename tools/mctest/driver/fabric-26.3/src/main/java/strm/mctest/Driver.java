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

    private Driver() {
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
        s.addProperty("screen", mc.gui.screen() == null ? null : mc.gui.screen().getClass().getName());
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
            while (index < steps.size()) {
                JsonObject step = steps.get(index++).getAsJsonObject();
                JsonObject result = new JsonObject();
                result.addProperty("step", index - 1);
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
                case "slot" -> requirePlayer(player).getInventory().setSelectedSlot(v.getAsInt());
                case "camera" -> mc.options.setCameraType(switch (v.getAsString()) {
                    case "first" -> CameraType.FIRST_PERSON;
                    case "back" -> CameraType.THIRD_PERSON_BACK;
                    case "front" -> CameraType.THIRD_PERSON_FRONT;
                    default -> throw new IllegalArgumentException("camera: first, back or front");
                });
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
                case "hideGui" -> throw new UnsupportedOperationException("hideGui is not available on 26.2");
                case "closeScreen" -> mc.gui.setScreen(null);
                case "screenshot" -> {
                    String name = v.getAsString().replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
                    Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
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
        s.addProperty("screen", mc.gui.screen() == null ? null : mc.gui.screen().getClass().getName());
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
        s.addProperty("dimension", p.level().dimension().identifier().toString());
        s.add("pos", GSON.toJsonTree(new double[]{p.getX(), p.getY(), p.getZ()}));
        s.add("rot", GSON.toJsonTree(new float[]{p.getYRot(), p.getXRot()}));
        s.addProperty("pose", p.getPose().name());
        s.addProperty("crouching", p.isCrouching());
        s.addProperty("onGround", p.onGround());
        s.addProperty("usingItem", p.isUsingItem());
        s.addProperty("slot", p.getInventory().getSelectedSlot());
        s.addProperty("mainHand", item(p.getMainHandItem()));
        s.addProperty("offHand", item(p.getOffhandItem()));
        s.addProperty("gameMode", mc.gameMode == null ? null : mc.gameMode.getPlayerMode().getName());
        s.add("target", target(mc, p));
        s.addProperty("dayTime", p.level().getOverworldClockTime());
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
                parts.addProperty(String.valueOf(e.getKey()),
                        String.format(java.util.Locale.ROOT, "%.2f %s", weight.getFloat(f), posed.getBoolean(f) ? "in" : "out"));
            }
            out.add("parts", parts);
        }
        return out;
    }

    private static String item(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount();
    }
}
