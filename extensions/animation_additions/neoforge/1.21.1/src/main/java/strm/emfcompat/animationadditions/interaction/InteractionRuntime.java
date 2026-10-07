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
import strm.emfcompat.animationadditions.buttonpress.aeronautics.HeavyThrottle;

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
        Object target;
        boolean quietSwing;
        boolean releasing;
        String releaseReason;
        final ContactRelease release = new ContactRelease();
        // Additive offsets of the complete final hand pose, including winding, roll and contact IK.
        float[] finalDelta;
        float[] finalRotation;
        float[] drawnRotation;
        float handoffBlend = 1;
        double poseHandoff;

    }

    private static final class PlayerState {
        final Map<Effector, Slot> slots = new EnumMap<>(Effector.class);
        final Map<String, Object> data = new HashMap<>();
        final Map<String, String> decided = new HashMap<>();
        final Map<String, String> logged = new HashMap<>();
        String reservedLogged = "";
        float capturedFrame = Float.NaN;
        IKFrame frame;
        String bodyOwner;
        int claimedSwingStart = Integer.MIN_VALUE;
        boolean paused;
        float bodyFrame = Float.NaN;
        final Map<String, float[]> bodyDelta = new HashMap<>();


        PlayerState() {
            for (Effector effector : Effector.values()) slots.put(effector, new Slot());
        }
    }

    /** Parkour owns the whole pose immediately: no residual hand/body release over its animation. */
    public static void suspend(UUID uuid) { STATES.forget(uuid); }

    /** Called with the model's space right before it is animated. */
    public static void modelPose(AbstractClientPlayer player, IKFrame frame) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<PlayerState> entry = STATES.seen(uuid, now);
        double dt = EntityStates.due(entry, now);
        if (dt < 0) return;
        PlayerState state = entry.value;
        state.frame = frame;
        state.paused = net.minecraft.client.Minecraft.getInstance().isPaused();
        if (state.paused) return;

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

        int swingStart = player.tickCount - player.swingTime;
        if (player.swinging && context.armsClaimed()) state.claimedSwingStart = swingStart;
        // Only the local container gesture supersedes WATUT's generic GUI hands.
        // Weapons, other addon poses and ordinary inventory screens keep their reservation.
        boolean containerSearch = player == net.minecraft.client.Minecraft.getInstance().player
                && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                && candidates.stream().anyMatch(candidate -> candidate.source().equals("ContainerSearch"));
        Set<Effector> reserved = reserved(player, containerSearch, context.armsClaimed()
                || player.swinging && state.claimedSwingStart == swingStart);
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
                if (!owner.source().equals(slot.owner) || !java.util.Objects.equals(owner.target(), slot.target)
                        || slot.releasing) {
                    if (slot.owner != null && slot.weight > 1e-3f) {
                        slot.handoff = HANDOFF_SECONDS;
                        slot.poseHandoff = HANDOFF_SECONDS * Math.log(1000);
                        if (slot.releasing && slot.finalDelta != null) {
                            float k = slot.release.remaining();
                            for (int i = 3; i < 6; i++) slot.finalDelta[i] *= k;
                            if (slot.drawnRotation != null) slot.finalRotation = slot.drawnRotation.clone();
                        }
                        log(player, effector.part + ": " + slot.owner + " -> " + owner.source());
                    }
                    slot.owner = owner.source();
                    slot.target = owner.target();
                }
                slot.releasing = false;
                slot.release.cancel();
                slot.releaseReason = null;
                slot.handoffBlend = slot.poseHandoff > 0 ? Smoothing.follow(dt, HANDOFF_SECONDS) : 1;
                slot.timing = owner.timing();
                slot.quietSwing = owner.quietSwing();
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
                if (slot.owner != null && !slot.releasing) {
                    slot.releasing = true;
                    slot.releaseReason = reserved.contains(effector) ? "occupied" : state.decided.getOrDefault(slot.owner, "target-lost");
                    slot.release.start(fadeOut);
                }
                if (slot.releasing) slot.release.advance(dt);
                slot.weight -= slot.weight * Smoothing.fadeOut(dt, fadeOut / ContactRelease.SPEED);
                // An item-use pose or another addon must own an occupied arm immediately.
                boolean clearance = effector.isArm() && strm.emfcompat.animationadditions.wallhand.WallSqueeze.isActive(uuid);
                if (reserved.contains(effector) || off || clearance) {
                    if (clearance) slot.releaseReason = "clearance";
                    slot.release.cancel();
                    slot.weight = 0;
                }
                if (slot.weight < 1e-3f && slot.release.remaining() == 0) {
                    slot.weight = 0f;
                    slot.owner = null;
                    slot.target = null;
                    slot.finalDelta = null;
                    slot.finalRotation = null;
                    slot.drawnRotation = null;
                    slot.releasing = false;
                }
            }
            slot.handoff = Math.max(0, slot.handoff - dt);
            slot.poseHandoff = Math.max(0, slot.poseHandoff - dt);
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
    private static Set<Effector> reserved(AbstractClientPlayer player, boolean containerSearch, boolean swingClaimed) {
        Set<Effector> reserved = EnumSet.noneOf(Effector.class);
        // A swing, an item in use or another addon's arm pose owns the arms.
        if (player.swinging && !swingClaimed || player.isUsingItem() || PoseManager.hasArmPoseExcept(player.getUUID(), containerSearch ? "watut" : "")) {
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
            if (slot.releasing || slot.aim == null || slot.weight < 1e-3f) continue;
            ModelPart part = parts.apply(e.getKey().part);
            if (part == null) continue;
            part.xRot += IKMath.wrap(slot.aim[0] - part.xRot) * slot.weight;
            part.yRot += IKMath.wrap(slot.aim[1] - part.yRot) * slot.weight;
            if (e.getKey().levelsRoll) part.zRot += IKMath.wrap(0f - part.zRot) * slot.weight;
        }
    }

    private static boolean hasHands(PlayerState state) {
        return state.slots.get(Effector.RIGHT_ARM).owner != null || state.slots.get(Effector.LEFT_ARM).owner != null;
    }

    private static final String[] SUPPORT_PARTS = {"body", "head", "right_leg", "left_leg", "right_arm", "left_arm"};
    private static final Set<String> BODY_PROVIDERS = Set.of("BlockUse", "ButtonPress", "HeavyThrottle", "TransportGrip", "LeadHold");

    public static Map<String, float[]> beginSupport(UUID uuid, Function<String, ModelPart> parts) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null || !hasHands(state)) return Map.of();
        Map<String, float[]> base = new HashMap<>();
        for (String name : SUPPORT_PARTS) {
            ModelPart p = parts.apply(name);
            if (p != null) base.put(name, new float[]{p.xRot, p.yRot, p.zRot, p.x, p.y, p.z});
        }
        return base;
    }

    /** Unload the final additive pelvis/feet/torso offsets before dropping the hand. The pack's
     * current locomotion is the base, so this never freezes a walking pose or the planted IK soles. */
    public static void finishSupport(UUID uuid, Function<String, ModelPart> parts, Map<String, float[]> base,
                                     float frame, boolean mainModel) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null) return;
        boolean capture = mainModel && !state.paused && state.bodyFrame != frame;
        // Collision clearance is always evaluated at the current wall, never replayed from a lost grip.
        if (!state.paused && strm.emfcompat.animationadditions.wallhand.WallSqueeze.isActive(uuid)) {
            if (capture) { state.bodyDelta.clear(); state.bodyOwner = null; state.bodyFrame = frame; }
            return;
        }
        Slot held = null, retiring = null;
        for (Effector hand : new Effector[]{Effector.RIGHT_ARM, Effector.LEFT_ARM}) {
            Slot slot = state.slots.get(hand);
            if (slot.owner == null) continue;
            if (!slot.releasing) held = slot;
            else if (slot.owner.equals(state.bodyOwner) && (retiring == null || slot.release.supportRemaining() > retiring.release.supportRemaining())) retiring = slot;
        }
        if (!state.paused && held != null && BODY_PROVIDERS.contains(held.owner)) {
            if (capture) {
                state.bodyOwner = held.owner;
                for (var e : base.entrySet()) {
                    ModelPart p = parts.apply(e.getKey());
                    float[] b = e.getValue();
                    state.bodyDelta.put(e.getKey(), new float[]{IKMath.wrap(p.xRot - b[0]), IKMath.wrap(p.yRot - b[1]), IKMath.wrap(p.zRot - b[2]), p.x - b[3], p.y - b[4], p.z - b[5]});
                }
            }
        } else if ((state.paused || held == null && retiring != null) && !state.bodyDelta.isEmpty()) {
            float k = state.paused ? (retiring == null ? 1 : retiring.release.supportRemaining()) : retiring.release.supportRemaining();
            for (var e : base.entrySet()) {
                float[] d = state.bodyDelta.get(e.getKey()), b = e.getValue();
                ModelPart p = parts.apply(e.getKey());
                if (d == null || p == null) continue;
                if (!e.getKey().endsWith("arm")) { p.xRot = b[0] + d[0] * k; p.yRot = b[1] + d[1] * k; p.zRot = b[2] + d[2] * k; }
                p.x = b[3] + d[3] * k;
                p.y = b[4] + d[4] * k;
                p.z = b[5] + d[5] * k;
            }
        } else if (capture) { state.bodyDelta.clear(); state.bodyOwner = null; }
        if (capture) state.bodyFrame = frame;
    }

    /** Pose just before the arm solvers. This includes the current breathing/torso/feet pose. */
    public static Map<Effector, float[]> beginHands(UUID uuid, Function<String, ModelPart> parts) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null || !hasHands(state)) return Map.of();
        Map<Effector, float[]> base = new EnumMap<>(Effector.class);
        for (Effector hand : new Effector[]{Effector.RIGHT_ARM, Effector.LEFT_ARM}) {
            ModelPart p = parts.apply(hand.part);
            if (p != null) base.put(hand, new float[]{p.xRot, p.yRot, p.zRot, p.x, p.y, p.z});
        }
        return base;
    }

    /** Last writer for the hands: disabled/expired providers cannot truncate a release, and
     * a new target cannot bypass handoff smoothing with a late, unsmoothed contact solve. */
    public static void finishHands(UUID uuid, Function<String, ModelPart> parts,
                                   Map<Effector, float[]> base, float frame, boolean mainModel) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null) return;
        boolean capture = mainModel && !state.paused && state.capturedFrame != frame;
        for (var entry : base.entrySet()) {
            Slot slot = state.slots.get(entry.getKey());
            ModelPart p = parts.apply(entry.getKey().part);
            float[] b = entry.getValue();
            if (p == null || slot.owner == null) continue;
            float[] delta = {IKMath.wrap(p.xRot - b[0]), IKMath.wrap(p.yRot - b[1]), IKMath.wrap(p.zRot - b[2]), p.x - b[3], p.y - b[4], p.z - b[5]};
            if ((slot.releasing || state.paused) && slot.finalDelta != null) {
                float k = slot.releasing ? slot.release.remaining() : 1;
                for (int i = 0; i < 6; i++) delta[i] = i < 3 && slot.finalRotation != null
                        ? IKMath.wrap(slot.finalRotation[i] - b[i]) * k : slot.finalDelta[i] * k;
            } else if (slot.poseHandoff > 0 && slot.finalDelta != null) {
                if (!capture) delta = slot.finalDelta.clone();
                else {
                    float[] previous = slot.finalDelta.clone();
                    if (slot.finalRotation != null) for (int i = 0; i < 3; i++) previous[i] = IKMath.wrap(slot.finalRotation[i] - b[i]);
                    delta = ContactPose.follow(previous, delta, slot.handoffBlend);
                }
            }
            if (slot.releasing || slot.poseHandoff > 0 || state.paused) {
                p.xRot = b[0] + delta[0];
                p.yRot = b[1] + delta[1];
                p.zRot = b[2] + delta[2];
                p.x = b[3] + delta[3];
                p.y = b[4] + delta[4];
                p.z = b[5] + delta[5];
            }
            if (capture && !slot.releasing && slot.weight > 1e-3f) slot.finalDelta = delta;
            // Track the drawn hand during withdrawal too, so reacquisition cannot restore an old pose.
            if (capture) {
                slot.drawnRotation = new float[]{p.xRot, p.yRot, p.zRot};
                if (!slot.releasing) slot.finalRotation = slot.drawnRotation.clone();
            }
        }
        if (capture) state.capturedFrame = frame;
    }

    /** The suppression belongs to the retained contact too. Keep the pack's ordinary swing
     * out of a mechanical grip until withdrawal finishes, without changing acquisition timing. */
    public static boolean quietsSwing(UUID uuid) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null) return false;
        for (Effector hand : new Effector[]{Effector.RIGHT_ARM, Effector.LEFT_ARM}) {
            Slot slot = state.slots.get(hand);
            if (slot.owner != null && slot.quietSwing && slot.weight > (slot.releasing ? 1e-3f : .5f)) return true;
        }
        return false;
    }

    public static boolean holds(UUID uuid, String source, Effector hand, Object target) {
        PlayerState state = STATES.fresh(uuid);
        if (state == null) return false;
        Slot slot = state.slots.get(hand);
        return source.equals(slot.owner) && !slot.releasing && slot.weight > .05f && java.util.Objects.equals(target, slot.target);
    }

    public static IKFrame frame(UUID uuid) {
        PlayerState state = STATES.fresh(uuid);
        return state == null ? null : state.frame;
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
