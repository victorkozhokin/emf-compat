package strm.emfcompat.animationadditions.compat;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.ik.IKMath;
import traben.entity_model_features.models.animation.state.EMFState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Optional ParCool bridge. ParCool animates the whole body for its moves; in the moves listed in
 * {@link ParCoolMoves#WEAK} what this addon adds on top fights that animation, so there - and only there -
 * the addon stands down for that player. Everywhere else it goes on as usual: a fast run is still
 * a run over real ground.
 */
public final class ParCoolActivity {
    public static final String KEY_YIELD = "parcool.yield", KEY_OVERLAP = "debug.parcoolOverlap";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatAnimationAdditions");

    public static void register(ConfigRegistry.Group movement, ConfigRegistry.Group debug) {
        movement.addBoolean(KEY_YIELD, "Give way to ParCool moves", true,
                "On", "In the ParCool moves that animate the whole body their own way - hanging, climbing, rolling and the like - this addon adds nothing.",
                "Off", "This addon's animations stay on during every ParCool move.");
        debug.addBoolean(KEY_OVERLAP, "Measure ParCool overlap", false,
                "On", "Log, for each ParCool animation, how much this addon changed the pose while it ran.",
                "Off", "Nothing is measured.");
    }

    private record V4(Method get, Field processor, Field animators, Method registration, Method location) {
    }

    private record AnimationProbe(Method get, Method state, boolean idle) {
        boolean active(AbstractClientPlayer player) throws ReflectiveOperationException {
            Object animation = get.invoke(null, player);
            return animation != null && ((Boolean) state.invoke(animation) != idle);
        }
    }

    private record Seen(float frame, List<String> running) {
    }

    private static final V4 FOUR = four();
    private static final AnimationProbe THREE = FOUR == null ? three() : null;
    private static final Map<UUID, Seen> SEEN = new HashMap<>();
    private static boolean warned;

    private ParCoolActivity() {
    }

    /** Whether this player is in a ParCool move the addon keeps out of. */
    public static boolean active(AbstractClientPlayer player) {
        if (player == null || FOUR == null && THREE == null || !EMFCompatConfig.getBoolean(KEY_YIELD, true)) return false;
        if (FOUR != null) {
            return ParCoolMoves.yields(running(player));
        }
        // ParCool 3 names nothing: any animation of its own is given way to.
        try {
            if (THREE.active(player)) return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
        }
        var sources = PoseManager.entitySavedPosesBySource.get(player.getUUID());
        return sources != null && sources.containsKey("parcool");
    }

    /**
     * A fast run flush against a wall is the run-up to a wall run: turning the body to "fit the
     * gap" there twists it by half a right angle a moment before ParCool takes it over.
     */
    public static boolean fastRun(AbstractClientPlayer player) {
        return FOUR != null && EMFCompatConfig.getBoolean(KEY_YIELD, true) && running(player).contains(ParCoolMoves.FAST_RUN);
    }

    /** ParCool 4's animations running on this player, read once a frame. */
    public static List<String> running(AbstractClientPlayer player) {
        if (FOUR == null) return List.of();
        float frame = EMFState.getFrameCounter();
        Seen seen = SEEN.get(player.getUUID());
        if (seen != null && seen.frame == frame) return seen.running;
        List<String> names = List.of();
        try {
            Object animator = FOUR.get.invoke(null, player);
            if (animator != null) {
                List<?> entries = (List<?>) FOUR.animators.get(FOUR.processor.get(animator));
                if (!entries.isEmpty()) {
                    names = new ArrayList<>(entries.size());
                    for (Object entry : entries)
                        names.add(((ResourceLocation) FOUR.location.invoke(FOUR.registration.invoke(entry))).getPath());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
        }
        if (SEEN.size() > 256) SEEN.clear();
        SEEN.put(player.getUUID(), new Seen(frame, names));
        return names;
    }

    private static void warn(Exception e) {
        if (warned) return;
        warned = true;
        LOGGER.warn("Could not read ParCool's animations; this addon stays on during its moves", e);
    }

    private static V4 four() {
        try {
            Class<?> animator = Class.forName("com.alrex.parcool.client.animation.system.PlayerAnimator");
            Class<?> processor = Class.forName("com.alrex.parcool.client.animation.system.AnimationProcessor");
            Class<?> entry = Class.forName("com.alrex.parcool.client.animation.system.AnimationProcessor$WorkingAnimationEntry");
            Class<?> registration = Class.forName("com.alrex.parcool.client.animation.system.registration.AnimationSets$Entry");
            Field processorOf = animator.getDeclaredField("animationProcessor"), animators = processor.getDeclaredField("animators");
            Method registrationOf = entry.getDeclaredMethod("registration");
            processorOf.setAccessible(true);
            animators.setAccessible(true);
            registrationOf.setAccessible(true);
            return new V4(animator.getMethod("get", AbstractClientPlayer.class), processorOf, animators, registrationOf, registration.getMethod("location"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }

    private static AnimationProbe three() {
        try {
            Class<?> type = Class.forName("com.alrex.parcool.common.attachment.client.Animation");
            return new AnimationProbe(type.getMethod("get", Player.class), type.getMethod("hasAnimator"), false);
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }

    // ---- measuring what the addon changes under each ParCool animation

    private static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    private static final class Overlap {
        String key = "";
        int frames;
        /** The most this addon turned the head, the body, an arm, a leg, degrees; and moved a part, pixels. */
        final float[] most = new float[5];
    }

    private static final Map<UUID, Overlap> OVERLAPS = new HashMap<>();

    /** The pose as the pack and ParCool left it, to be compared after the addon has run; {@code null} when not measuring. */
    public static float[] before(Function<String, ModelPart> parts) {
        if (FOUR == null || !EMFCompatConfig.getBoolean(KEY_OVERLAP, false)) return null;
        float[] pose = new float[PARTS.length * 6];
        for (int i = 0; i < PARTS.length; i++) {
            ModelPart part = parts.apply(PARTS[i]);
            if (part == null) return null;
            pose[i * 6] = part.xRot;
            pose[i * 6 + 1] = part.yRot;
            pose[i * 6 + 2] = part.zRot;
            pose[i * 6 + 3] = part.x;
            pose[i * 6 + 4] = part.y;
            pose[i * 6 + 5] = part.z;
        }
        return pose;
    }

    public static void after(AbstractClientPlayer player, Function<String, ModelPart> parts, float[] before) {
        if (before == null) return;
        Overlap overlap = OVERLAPS.computeIfAbsent(player.getUUID(), u -> new Overlap());
        List<String> running = new ArrayList<>(running(player));
        java.util.Collections.sort(running);
        String key = String.join("+", running);
        if (!key.equals(overlap.key)) {
            if (overlap.frames > 0)
                LOGGER.info("[ParCoolOverlap] {} {} frames={} head={} body={} arm={} leg={} shift={}", player.getName().getString(),
                        overlap.key.isEmpty() ? "(none)" : overlap.key, overlap.frames, round(overlap.most[0]), round(overlap.most[1]),
                        round(overlap.most[2]), round(overlap.most[3]), round(overlap.most[4]));
            overlap.key = key;
            overlap.frames = 0;
            java.util.Arrays.fill(overlap.most, 0);
        }
        overlap.frames++;
        for (int i = 0; i < PARTS.length; i++) {
            ModelPart part = parts.apply(PARTS[i]);
            float turn = Math.max(Math.abs(IKMath.wrap(part.xRot - before[i * 6])),
                    Math.max(Math.abs(IKMath.wrap(part.yRot - before[i * 6 + 1])), Math.abs(IKMath.wrap(part.zRot - before[i * 6 + 2]))));
            float shift = (float) Math.sqrt(sq(part.x - before[i * 6 + 3]) + sq(part.y - before[i * 6 + 4]) + sq(part.z - before[i * 6 + 5]));
            int group = i < 2 ? i : i < 4 ? 2 : 3;
            overlap.most[group] = Math.max(overlap.most[group], (float) Math.toDegrees(turn));
            overlap.most[4] = Math.max(overlap.most[4], shift);
        }
    }

    private static float sq(float v) {
        return v * v;
    }

    private static String round(float v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
