package strm.emfcompat.animationadditions.interaction;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Runs the interaction providers for each rendered player and puts the result on the model.
 *
 * <p>Per frame (at most every {@link #SOLVE_EVERY_NANOS}): every enabled provider offers
 * candidates, the {@link Arbiter} gives each part to one of them, and each part's slot fades its
 * weight towards 1 for a part with an owner and towards 0 for one without, and moves its aim
 * towards the owner's. A part changing hands mid-way does not jump: the aim glides over for
 * {@link #HANDOFF_SECONDS}. After the pack has animated, {@link #apply} blends each part towards
 * its slot's aim by its weight - on the model, and again on the armour model.</p>
 *
 * <p>All state lives here, per player, and is dropped when the player has not been drawn for
 * {@link #FORGET_NANOS}.</p>
 */
public final class InteractionRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatInteraction");

    private static final long SOLVE_EVERY_NANOS = 2_000_000L;
    /** Not drawn for this long, the result is no longer put on the model. */
    private static final long STALE_NANOS = 200_000_000L;
    /** Not drawn for this long, the player's state is dropped. */
    private static final long FORGET_NANOS = 5_000_000_000L;
    private static final double HANDOFF_SECONDS = 0.12;
    private static final int MAX_PLAYERS = 64;

    private static final List<InteractionProvider> PROVIDERS = new ArrayList<>();
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private InteractionRuntime() {
    }

    public static void register(InteractionProvider provider) {
        PROVIDERS.add(provider);
    }

    private static final class Slot {
        String owner;
        Candidate.Timing timing;
        float[] aim;
        float weight;
        double handoff;
    }

    private static final class PlayerState {
        final Map<Effector, Slot> slots = new EnumMap<>(Effector.class);
        final Map<String, Object> data = new HashMap<>();
        final Map<String, String> decided = new HashMap<>();
        final Map<String, String> logged = new HashMap<>();
        String reservedLogged = "";
        long solvedAt, seenAt;

        PlayerState() {
            for (Effector effector : Effector.values()) slots.put(effector, new Slot());
        }
    }

    /** Called with the model's space right before it is animated. */
    public static void modelPose(AbstractClientPlayer player, IKFrame frame) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        forgetOld(now);
        PlayerState state = STATES.computeIfAbsent(uuid, k -> new PlayerState());
        state.seenAt = now;
        if (state.solvedAt != 0 && now - state.solvedAt < SOLVE_EVERY_NANOS) return;
        double dt = state.solvedAt == 0 ? 0 : Math.min(0.1, (now - state.solvedAt) / 1e9);
        state.solvedAt = now;

        List<Candidate> candidates = new ArrayList<>();
        InteractionContext context = new InteractionContext(player, frame, now, dt, state.data, state.decided);
        boolean off = !EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid);
        for (InteractionProvider provider : PROVIDERS) {
            context.enter(provider.id());
            if (off || !provider.isEnabled()) {
                context.decide("off:disabled");
                continue;
            }
            try {
                provider.collect(context, candidates);
            } catch (Throwable t) {
                context.decide("off:error");
                logOnce(provider.id(), t);
            }
        }

        Set<Effector> reserved = reserved(player);
        Map<Effector, String> holders = new EnumMap<>(Effector.class);
        state.slots.forEach((effector, slot) -> {
            if (slot.owner != null) holders.put(effector, slot.owner);
        });
        Map<Effector, Candidate> owners = Arbiter.resolve(candidates, reserved, holders);

        for (Effector effector : Effector.values()) {
            Slot slot = state.slots.get(effector);
            Candidate owner = owners.get(effector);
            if (owner != null) {
                float[] target = owner.aims().get(effector);
                if (!owner.source().equals(slot.owner)) {
                    if (slot.owner != null && slot.weight > 1e-3f) {
                        slot.handoff = HANDOFF_SECONDS;
                        log(player, effector.part + ": " + slot.owner + " -> " + owner.source());
                    }
                    slot.owner = owner.source();
                }
                slot.timing = owner.timing();
                if (slot.aim == null || slot.weight < 1e-3f) {
                    slot.aim = target.clone();
                } else {
                    double tau = Math.max(owner.timing().aimSeconds(), slot.handoff);
                    float k = Smoothing.follow(dt, tau);
                    slot.aim[0] += IKMath.wrap(target[0] - slot.aim[0]) * k;
                    slot.aim[1] += IKMath.wrap(target[1] - slot.aim[1]) * k;
                }
                slot.weight += (1f - slot.weight) * Smoothing.fadeIn(dt, owner.timing().fadeIn());
            } else {
                double fadeOut = slot.timing == null ? 0.1 : slot.timing.fadeOut();
                slot.weight -= slot.weight * Smoothing.fadeOut(dt, fadeOut);
                if (slot.weight < 1e-3f) {
                    slot.weight = 0f;
                    slot.owner = null;
                }
            }
            slot.handoff = Math.max(0, slot.handoff - dt);
        }

        for (Map.Entry<String, String> e : state.decided.entrySet()) {
            if (!e.getValue().equals(state.logged.get(e.getKey()))) {
                LoggerFactory.getLogger("EMFCompat" + e.getKey())
                        .info("[{}] {} {}", e.getKey(), player.getName().getString(), e.getValue());
                state.logged.put(e.getKey(), e.getValue());
            }
        }
        String reservedNow = reserved.isEmpty() ? "" : reserved.toString();
        if (!reservedNow.equals(state.reservedLogged)) {
            log(player, reservedNow.isEmpty() ? "arms free" : "reserved " + reservedNow);
            state.reservedLogged = reservedNow;
        }
    }

    /** Parts something outside the runtime owns this frame. */
    private static Set<Effector> reserved(AbstractClientPlayer player) {
        Set<Effector> reserved = EnumSet.noneOf(Effector.class);
        // A swing, an item in use or another addon's arm pose owns the arms.
        if (player.swinging || player.isUsingItem() || PoseManager.hasArmPoseExcept(player.getUUID(), "")) {
            reserved.add(Effector.RIGHT_ARM);
            reserved.add(Effector.LEFT_ARM);
        }
        return reserved;
    }

    /** Blends each owned part towards its aim, over whatever it was animated to. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        PlayerState state = STATES.get(uuid);
        if (state == null || System.nanoTime() - state.seenAt > STALE_NANOS) return;
        for (Map.Entry<Effector, Slot> e : state.slots.entrySet()) {
            Slot slot = e.getValue();
            if (slot.aim == null || slot.weight < 1e-3f) continue;
            ModelPart part = parts.apply(e.getKey().part);
            if (part == null) continue;
            part.xRot += IKMath.wrap(slot.aim[0] - part.xRot) * slot.weight;
            part.yRot += IKMath.wrap(slot.aim[1] - part.yRot) * slot.weight;
            if (e.getKey().levelsRoll) part.zRot += IKMath.wrap(0f - part.zRot) * slot.weight;
        }
    }

    /** How much of a part the runtime has this frame, 0 to 1. */
    public static float weight(UUID uuid, Effector effector) {
        PlayerState state = STATES.get(uuid);
        if (state == null || System.nanoTime() - state.seenAt > STALE_NANOS) return 0f;
        return state.slots.get(effector).weight;
    }

    private static void forgetOld(long now) {
        if (STATES.size() <= MAX_PLAYERS / 2) return;
        Iterator<PlayerState> it = STATES.values().iterator();
        while (it.hasNext()) if (now - it.next().seenAt > FORGET_NANOS) it.remove();
        if (STATES.size() > MAX_PLAYERS) STATES.clear();
    }

    private static void log(AbstractClientPlayer player, String what) {
        LOGGER.info("[Interaction] {} {}", player.getName().getString(), what);
    }

    private static final Set<String> FAILED = new java.util.HashSet<>();

    private static void logOnce(String provider, Throwable t) {
        if (FAILED.add(provider + t.getClass().getName())) {
            LOGGER.warn("[Interaction] provider {} failed; it sits out until it stops throwing", provider, t);
        }
    }
}
