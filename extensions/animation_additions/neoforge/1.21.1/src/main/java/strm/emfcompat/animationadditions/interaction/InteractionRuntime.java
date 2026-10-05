package strm.emfcompat.animationadditions.interaction;

import strm.emfcompat.animationadditions.DebugLog;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Runs the interaction providers for each rendered player and puts the result on the model.
 *
 * <p>Per frame (at most every {@link EntityStates#SOLVE_EVERY_NANOS}): every enabled provider offers
 * candidates, the {@link Arbiter} gives each part to one of them, and each part's slot fades its
 * weight towards 1 for a part with an owner and towards 0 for one without, and moves its aim
 * towards the owner's. A part changing hands mid-way does not jump: the aim glides over for
 * {@link #HANDOFF_SECONDS}. After the pack has animated, {@link #apply} blends each part towards
 * its slot's aim by its weight - on the model, and again on the armour model.</p>
 *
 * <p>All state lives here, per player, in an {@link EntityStates}: providers keep theirs in it
 * too, and it goes with the player.</p>
 */
public final class InteractionRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatInteraction");

    private static final double HANDOFF_SECONDS = 0.12;

    private static final List<InteractionProvider> PROVIDERS = new ArrayList<>();
    private static final EntityStates<PlayerState> STATES = new EntityStates<>(PlayerState::new);

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

        PlayerState() {
            for (Effector effector : Effector.values()) slots.put(effector, new Slot());
        }
    }

    /** Called with the model's space right before it is animated. */
    public static void modelPose(AbstractClientPlayer player, IKFrame frame) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<PlayerState> entry = STATES.seen(uuid, now);
        double dt = EntityStates.due(entry, now);
        if (dt < 0) return;
        PlayerState state = entry.value;

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

        Set<Effector> reserved = reserved(player, context.armsClaimed());
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
                if (DebugLog.decisions()) {
                    LoggerFactory.getLogger("EMFCompat" + e.getKey())
                            .info("[{}] {} {}", e.getKey(), player.getName().getString(), e.getValue());
                }
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
    private static Set<Effector> reserved(AbstractClientPlayer player, boolean swingClaimed) {
        Set<Effector> reserved = EnumSet.noneOf(Effector.class);
        // A swing, an item in use or another addon's arm pose owns the arms.
        if (player.swinging && !swingClaimed || player.isUsingItem() || PoseManager.hasArmPoseExcept(player.getUUID(), "")) {
            reserved.add(Effector.RIGHT_ARM);
            reserved.add(Effector.LEFT_ARM);
        }
        return reserved;
    }

    /** Blends each owned part towards its aim, over whatever it was animated to. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null) return;
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

    /** A part's aim and how much of it shows, {xRot, yRot, weight}; {@code null} when nobody has it. */
    public static float[] aim(UUID uuid, Effector effector) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null) return null;
        Slot slot = state.slots.get(effector);
        if (slot.aim == null || slot.weight < 1e-3f) return null;
        return new float[]{slot.aim[0], slot.aim[1], slot.weight};
    }

    /** How much of a part the runtime has this frame, 0 to 1. */
    public static float weight(UUID uuid, Effector effector) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null) return 0f;
        return state.slots.get(effector).weight;
    }

    /** How much of a part the named provider has this frame, 0 when another has it or nobody. */
    public static float weight(UUID uuid, Effector effector, String source) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null) return 0f;
        Slot slot = state.slots.get(effector);
        return source.equals(slot.owner) ? slot.weight : 0f;
    }

    private static void log(AbstractClientPlayer player, String what) {
        if (DebugLog.decisions()) LOGGER.info("[Interaction] {} {}", player.getName().getString(), what);
    }

    private static final Set<String> FAILED = new java.util.HashSet<>();

    private static void logOnce(String provider, Throwable t) {
        if (FAILED.add(provider + t.getClass().getName())) {
            LOGGER.warn("[Interaction] provider {} failed; it sits out until it stops throwing", provider, t);
        }
    }
}
