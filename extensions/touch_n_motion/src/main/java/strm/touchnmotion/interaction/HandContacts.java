package strm.touchnmotion.interaction;
import strm.emfcompat.core.ik.Skeleton;

import strm.emfcompat.core.ik.OneBoneIK;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.core.EMFCompatCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Contact targets reapplied from the drawn shoulder after breathing and torso layers. */
public final class HandContacts {
    private static final java.util.Set<String> EARLY_SOURCES = java.util.Set.of("DoorHold", "Furniture", "WallHand", "PlantReach");
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("EMFCompatContact");
    private record Key(String source, Effector hand) {}
    /** A retained point supplies its current rendered position; reading never advances its motion. */
    public enum Pass { CONTACT, LATE }
    public enum Rotation { XY, AXIS, KEEP_ROLL }

    public interface Target {
        Vec3 world();
        default Pass pass() { return Pass.CONTACT; }
        default Rotation rotation() { return Rotation.XY; }
        default float length() { return Skeleton.ARM_TO_FINGERTIPS; }
        default boolean valid() { return true; }
    }
    private record Anchor(SubLevels.Space space, Vec3 local, Vec3 normal, float length, Rotation rotation) implements Target {
        public Vec3 world() { return space.valid() ? space.refresh().toWorld(local) : null; }
    }
    private static final class State {
        final Map<Key, Target> targets = new HashMap<>();
        final Map<Key, float[]> drawnTargets = new HashMap<>();
        final java.util.EnumMap<Effector, net.minecraft.world.phys.AABB> plantBounds = new java.util.EnumMap<>(Effector.class);
        final strm.touchnmotion.torso.BraceSteps.State feet = new strm.touchnmotion.torso.BraceSteps.State();
        net.minecraft.client.player.AbstractClientPlayer player;
        final strm.touchnmotion.torso.LowReach.State reach = new strm.touchnmotion.torso.LowReach.State();
        final Vector3f lastReach = new Vector3f();
        boolean rightReach = true;
        /** When each wall contact's refined point was last tried against the wall itself, and whether it held. */
        final Map<Key, long[]> wallChecked = new HashMap<>();
        final Map<Key, Vec3> wallCheckedPoint = new HashMap<>();
    }
    /** The wall is asked again this often, not every frame: a ray a frame for each hand and again for the armour is too dear. */
    private static final long WALL_CHECK_NANOS = 150_000_000L;
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    private HandContacts() {}

    public static void remember(InteractionContext context, String source, Effector hand, Vec3 world) {
        remember(context, source, hand, world, SubLevels.WORLD);
    }

    public static void remember(InteractionContext context, String source, Effector hand, Vec3 world, SubLevels.Space space) {
        remember(context, source, hand, world, space, null);
    }

    public static void remember(InteractionContext context, String source, Effector hand, Vec3 world, SubLevels.Space space, Vec3 normal) {
        State state = STATES.seen(context.player().getUUID(), context.now()).value;
        state.player = context.player();
        Key key = new Key(source, hand);
        float length = source.equals("PlantReach") || source.equals("WallHand") ? Skeleton.ARM_TO_PALM : Skeleton.ARM_TO_FINGERTIPS;
        Rotation rotation = source.equals("HeavyThrottle") ? Rotation.KEEP_ROLL : Rotation.XY;
        state.targets.put(key, new Anchor(space, space.toLocal(world), normal == null ? null : space.directionToLocal(normal), length, rotation));
    }

    /** Moving mechanisms (including deforming ropes) keep their own stable local/segment identity. */
    public static void remember(InteractionContext context, String source, Effector hand, Target target) {
        State state = STATES.seen(context.player().getUUID(), context.now()).value;
        state.player = context.player();
        state.targets.put(new Key(source, hand), target);
    }

    public static void rememberPlant(InteractionContext context, Effector hand, Vec3 world, net.minecraft.world.phys.AABB box) {
        remember(context, "PlantReach", hand, world);
        STATES.fresh(context.player().getUUID()).plantBounds.put(hand, box);
    }

    /** The hand keeps its owner but no longer has a point to be brought on to: the runtime's own aim stands. */
    public static void forget(InteractionContext context, String source, Effector hand) {
        State state = STATES.fresh(context.player().getUUID());
        if (state == null) return;
        Key key = new Key(source, hand);
        state.targets.remove(key);
        state.drawnTargets.remove(key);
        state.wallChecked.remove(key);
        state.wallCheckedPoint.remove(key);
        if (source.equals("PlantReach")) state.plantBounds.remove(hand);
    }

    private record Support(ContactStance pose, float weight) {}
    private static Support stance(UUID uuid, State state) {
        var frame = InteractionRuntime.frame(uuid);
        if (frame == null || state.player == null) return null;
        float weight = 0;
        String source = null;
        // Select one stance, even when two contacts are owned. Never add two body solvers.
        for (var key : state.targets.keySet()) {
            if (!EARLY_SOURCES.contains(key.source)) continue;
            float w = InteractionRuntime.weight(uuid, key.hand, key.source);
            if (w > weight) { weight = w; source = key.source; }
        }
        if (source == null || weight < .001f) return null;
        float side = 0, total = 0;
        for (var entry : state.targets.entrySet()) {
            if (!entry.getKey().source.equals(source)) continue;
            float w = InteractionRuntime.weight(uuid, entry.getKey().hand, source);
            Vec3 world = entry.getValue().world();
            if (world == null || w < .001f) continue;
            side += Body.model(frame, world).x * w;
            total += w;
        }
        if (total < .001f) return null;
        return new Support(ContactStance.forContact(source, side / total, weight, state.player.isCrouching()), weight);
    }

    public static strm.touchnmotion.torso.TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        Support support = state == null ? null : stance(uuid, state);
        if (support == null) return null;
        var pose = support.pose;
        return new strm.touchnmotion.torso.TorsoLean.Hint(pose.pitch(), pose.yaw(), 0, 0, pose.yaw());
    }

    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.player == null || !EMFCompatCore.isCompatEnabled()
                || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        Support support = stance(uuid, state);
        // No contact and the feet home again: nothing to step to, nothing to shift.
        if (support == null && state.feet.resting()) return;
        ContactStance pose = support == null ? new ContactStance(0, 0, 0, 0, 0) : support.pose;
        float weight = support == null ? 0 : support.weight;
        strm.touchnmotion.torso.BraceSteps.apply(state.feet, state.player, InteractionRuntime.frame(uuid), parts,
                new Vector3f(-pose.spread(), 0, pose.forward() * .5f), new Vector3f(pose.spread(), 0, -pose.forward() * .5f),
                weight, 0, LOGGER, "ContactStance");
        if (Body.planted(state.player))
            strm.touchnmotion.torso.PelvisFollow.shift(parts, pose.side(), pose.forward());
    }

    /** A bounded grounded fit for the fixed handles/books, after the torso layer. */
    public static void reach(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        var frame = InteractionRuntime.frame(uuid);
        if (state == null || state.player == null || frame == null || !EMFCompatCore.isCompatEnabled()
                || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        float weight = 0;
        Key chosen = null;
        boolean still = Body.planted(state.player);
        if (still) for (var entry : state.targets.entrySet()) {
            Key key = entry.getKey();
            if (!key.source.equals("DoorHold") && !key.source.equals("Furniture")) continue;
            float w = InteractionRuntime.weight(uuid, key.hand, key.source);
            if (w > weight && entry.getValue().world() != null) { weight = w; chosen = key; }
        }
        Vector3f other = null;
        if (chosen != null) {
            state.lastReach.set(Body.model(frame, state.targets.get(chosen).world()));
            state.rightReach = chosen.hand == Effector.RIGHT_ARM;
            Key opposite = new Key(chosen.source, Effector.arm(!state.rightReach));
            Target anchor = state.targets.get(opposite);
            if (anchor != null && anchor.world() != null && InteractionRuntime.weight(uuid, opposite.hand, opposite.source) > .1f)
                other = Body.model(frame, anchor.world());
        }
        state.reach.angleLimit = (float) Math.toRadians(state.player.isCrouching() ? 5 : 10);
        if (weight > .001f || state.reach.active())
            strm.touchnmotion.torso.LowReach.apply(parts, state.rightReach, state.lastReach, other, weight, state.reach);
    }

    /** Model-space targets for native contact verification; no extra solve or time advancement. */
    public static Map<String, float[]> snapshot(UUID uuid) {
        State state = STATES.fresh(uuid);
        var frame = InteractionRuntime.frame(uuid);
        Map<String, float[]> out = new HashMap<>();
        if (state == null || frame == null) return out;
        state.targets.forEach((key, anchor) -> {
            if (InteractionRuntime.weight(uuid, key.hand, key.source) < .001f || !anchor.valid()) return;
            float[] drawn = state.drawnTargets.get(key);
            if (drawn != null) {
                out.put(key.source+":"+key.hand, drawn);
                return;
            }
            Vec3 world = anchor.world();
            if (world == null) return;
            Vector3f p = Body.model(frame, world);
            out.put(key.source+":"+key.hand, new float[]{p.x, p.y, p.z, anchor.length()});
        });
        return out;
    }

    public static void apply(UUID uuid, Function<String, ModelPart> parts, Map<Effector, float[]> base, boolean mainModel) {
        applyTargets(uuid, parts, base, mainModel, Pass.CONTACT, null);
    }

    /** Late contact pass, after the source's torso/reach stages; also used for armour copies. */
    public static void applyLate(UUID uuid, String source, Function<String, ModelPart> parts, Map<Effector, float[]> base, boolean mainModel) {
        applyTargets(uuid, parts, base, mainModel, Pass.LATE, source);
    }

    public static float gap(UUID uuid, String source, Effector hand, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        var frame = InteractionRuntime.frame(uuid);
        Target target = state == null ? null : state.targets.get(new Key(source, hand));
        ModelPart arm = parts.apply(hand.part);
        Vec3 world = target == null || !target.valid() ? null : target.world();
        return frame == null || arm == null || world == null ? 1
                : (float) frame.jointWorld(Body.tip(arm, target.length())).distanceTo(world);
    }

    private static void applyTargets(UUID uuid, Function<String, ModelPart> parts, Map<Effector, float[]> base,
                                     boolean mainModel, Pass pass, String source) {
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        State state = STATES.fresh(uuid);
        if (state == null) return;
        var frame = InteractionRuntime.frame(uuid);
        if (frame == null) return;
        state.targets.forEach((key, anchor) -> {
            if (anchor.pass() != pass || source != null && !key.source.equals(source)) return;
            float w = InteractionRuntime.weight(uuid, key.hand, key.source);
            ModelPart arm = parts.apply(key.hand.part);
            if (w < 1e-3f || arm == null || InteractionRuntime.releasing(uuid, key.hand, key.source)) return;
            Vec3 world = anchor.valid() ? anchor.world() : null;
            if (world == null) return;
            Vector3f point = Body.model(frame, world);
            if (anchor instanceof Anchor fixed && fixed.normal != null && key.source.equals("WallHand")) {
                Vec3 normal = fixed.space.refresh().directionToWorld(fixed.normal);
                Vector3f modelNormal = Body.model(frame, world.add(normal)).sub(point).normalize();
                Vector3f fitted = PlaneContact.fit(new Vector3f(arm.x, arm.y, arm.z), point, modelNormal, Skeleton.ARM_TO_PALM);
                if (fitted != null) {
                    long now = System.nanoTime();
                    long[] checked = state.wallChecked.computeIfAbsent(key, k -> new long[2]);
                    Vec3 fitWorld = frame.jointWorld(fitted);
                    Vec3 localFit = fixed.space.refresh().toLocal(fitWorld);
                    Vec3 previousFit = state.wallCheckedPoint.get(key);
                    if (checked[0] == 0 || now - checked[0] > WALL_CHECK_NANOS
                            || previousFit == null || previousFit.distanceToSqr(localFit) > .04 * .04) {
                        state.wallCheckedPoint.put(key, localFit);
                        var face = strm.touchnmotion.wallhand.WallSurface.clip(state.player,
                                fitWorld.add(normal.scale(.08)), fitWorld.subtract(normal.scale(.08)),
                                java.util.List.of(fixed.space.refresh()));
                        checked[0] = now;
                        checked[1] = face != null && face.normal().dot(normal) > .9 && face.position().distanceTo(fitWorld) < .04 ? 1 : 0;
                    }
                    if (checked[1] != 0) point = fitted;
                }
            }
            if (key.source.equals("PlantReach")) {
                var box = state.plantBounds.get(key.hand);
                Vec3 shoulder = frame.jointWorld(new Vector3f(arm.x, arm.y, arm.z));
                double length = frame.jointWorld(new Vector3f(arm.x, arm.y + Skeleton.ARM_TO_PALM, arm.z)).distanceTo(shoulder);
                if (box != null) {
                    var edge = strm.touchnmotion.plantreach.CanopyContact.edge(
                            shoulder.x, shoulder.y, shoulder.z, length,
                            box.minX, box.maxX, box.minY, Math.min(shoulder.y - .25, box.maxY - .05), box.minZ, box.maxZ);
                    if (edge != null) point = Body.model(frame, new Vec3(edge.x(), edge.y(), edge.z()));
                }
            }
            if (mainModel) state.drawnTargets.put(key, new float[]{point.x, point.y, point.z,
                    anchor.length()});
            if (anchor.rotation() == Rotation.KEEP_ROLL) { OneBoneIK.turnTowards(arm, point, w, false); return; }
            float[] original = base.get(key.hand);
            if (original == null) return;
            if (anchor.rotation() == Rotation.AXIS) {
                // The runtime already aimed this arm. Correct from the pre-IK pose, not its
                // weighted result, so the contact's ownership weight is applied exactly once.
                float[] angles = ContactAim.palmRotation(original,
                        point.x - arm.x, point.y - arm.y, point.z - arm.z, w);
                arm.setRotation(angles[0], angles[1], angles[2]);
                return;
            }
            float[] angles = ContactAim.rotation(original, point.x - arm.x, point.y - arm.y, point.z - arm.z, w);
            arm.setRotation(angles[0], angles[1], angles[2]);
        });
    }
}
