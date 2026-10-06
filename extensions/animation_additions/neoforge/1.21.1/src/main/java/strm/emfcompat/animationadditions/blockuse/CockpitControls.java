package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.buttonpress.ButtonPress;
import strm.emfcompat.animationadditions.buttonpress.ThrottleLever;
import strm.emfcompat.animationadditions.interaction.*;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.OneBoneIK;
import strm.emfcompat.core.ik.IKMath;
import java.util.*;
import java.util.function.Function;

/** Seated wheel / side throttle handovers. The seat supports the pelvis and seated thigh volume. */
public final class CockpitControls implements InteractionProvider {
    public static final CockpitControls INSTANCE = new CockpitControls();
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    private static final SteeringWheel WHEEL = new SteeringWheel();
    private static final Typewriter TYPEWRITER = new Typewriter();
    private static final Vector3f[] SHOULDERS = {new Vector3f(-5, 2, 0), new Vector3f(5, 2, 0)};
    private static final Candidate.Timing TIMING = new Candidate.Timing(.12, .18, .06);
    private static final class State {
        UUID seat;
        BlockPos wheel;
        final BlockPos[] throttle = new BlockPos[2];
        final boolean[] typing = new boolean[2];
        final TypingMotion keys = new TypingMotion();
        final BlockTarget.Spot[] rim = new BlockTarget.Spot[2];
        final Vector3f[] grips = {new Vector3f(), new Vector3f()};
        final Vector3f[] reachGrips = {new Vector3f(), new Vector3f()};
        final Vector3f[] shoulders = {new Vector3f(-5, 2, 0), new Vector3f(5, 2, 0)};
        final CockpitMotion motion = new CockpitMotion();
        final Vector3f lean = new Vector3f();
        final Quaternionf contact = new Quaternionf();
        long contactAt;
        float contactFrame = -1;
        Vec3 right;
        strm.emfcompat.animationadditions.transport.TransportMotion transport = new strm.emfcompat.animationadditions.transport.TransportMotion();
        SubLevels.Space craft;
        Vec3 reference, pelvisLocal;
        final Vec3[] gripLocal = new Vec3[2];
        final Vec3[] reachLocal = new Vec3[2];
        strm.emfcompat.core.ik.IKFrame frame;
        final Vec3[] controlLocal = new Vec3[2];
        final Vector3f inertia = new Vector3f();
        final Map<String, Object> snapshot = new LinkedHashMap<>();
        double dt;
        float rightGap, leftGap, seatGap;
        int seatSince;
        float seatFrame = Float.NaN;
        long seatAt;
        double seatHeight = Double.NaN;
        double seatTop = Double.NaN;
        BlockPos seatBlock;
        java.util.List<net.minecraft.world.phys.AABB> seatBoxes = java.util.List.of();

        final Vector3f[] lever = {new Vector3f(), new Vector3f()};
        boolean shown, held;
        int request = -1;
        long traceAt;
        AbstractClientPlayer player;
        float headYaw, headPitch;
    }
    public String id() { return "CockpitControls"; }
    public boolean isEnabled() { return BlockUse.INSTANCE.isEnabled() && ButtonPress.INSTANCE.isEnabled(); }

    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        State state = STATES.seen(player.getUUID(), context.now()).value;
        state.shown = false;
        state.held = false;
        state.request = -1;
        state.frame = context.frame();
        state.dt = context.dt();
        state.player = player;
        state.snapshot.put("shown", false);
        Vector3f wantedLean = new Vector3f();
        try {
            if (!Seated.seated(player) || player.isSleeping() || player.isInWaterOrBubble()) {
                state.seat = null;
                state.wheel = null;
                context.decide("off:seat");
                return;
            }
            UUID seat = player.getVehicle().getUUID();
            if (!seat.equals(state.seat)) {
                state.seat = seat;
                state.seatSince = player.tickCount;
                state.seatFrame = Float.NaN;
                state.seatAt = 0;
                state.seatHeight = Double.NaN;
                state.wheel = null;
                state.motion.away = state.motion.moving = -1;
                state.motion.progress = 1;
                Arrays.fill(state.throttle, null);
                Arrays.fill(state.rim, null);
                Arrays.fill(state.typing, false);
                state.keys.reset();
                state.pelvisLocal = null;
                state.craft = null;
                state.contact.identity();
                state.contactAt = 0;
                state.transport = new strm.emfcompat.animationadditions.transport.TransportMotion();
            }
            BlockHitResult hit = strm.emfcompat.animationadditions.net.Inputs.sight(player, 3, 1) instanceof BlockHitResult b ? b : null;
            if (hit != null && WHEEL.matches(player.level().getBlockState(hit.getBlockPos()))
                    && !hit.getBlockPos().equals(state.wheel)) {
                state.wheel = hit.getBlockPos().immutable();
                Arrays.fill(state.rim, null);
                state.motion.away = state.motion.moving = -1;
                state.motion.progress = 1;
                Vec3 forward = SubLevels.toWorld(player.level(), state.wheel,
                        WHEEL.swayCentre(player.level(), state.wheel, player.level().getBlockState(state.wheel))).subtract(player.position());
                state.right = SubLevels.at(player.level(), state.wheel).directionToLocal(new Vec3(-forward.z, 0, forward.x).normalize());
            }
            if (state.wheel == null || !WHEEL.matches(player.level().getBlockState(state.wheel))) {
                state.wheel = null;
                context.decide("none:wheel");
                return;
            }
            var mount = player.level().getBlockState(state.wheel);
            Vec3 centre = SubLevels.toWorld(player.level(), state.wheel, WHEEL.swayCentre(player.level(), state.wheel, mount));
            if (centre.distanceTo(player.getEyePosition()) > 2.25 || !Visibility.visible(player, state.wheel, centre)) {
                state.wheel = null;
                context.decide("off:wheel-range");
                return;
            }
            BlockPos requested = player == Minecraft.getInstance().player ? ThrottleLever.heldPosition() : strm.emfcompat.animationadditions.net.Inputs.throttle(player);
            state.held = requested != null;
            boolean typing = false;
            if (requested == null) {
                requested = Typewriter.activePosition(player);
                typing = requested != null && TYPEWRITER.matches(player.level().getBlockState(requested));
                if (!typing) requested = null;
            }
            if (requested == null && hit != null && ThrottleLever.is(player.level().getBlockState(hit.getBlockPos())))
                requested = hit.getBlockPos();
            state.keys.advance(typing ? Typewriter.pressedKey(player) : -1, context.dt());
            Vec3 knob = requested == null ? null : typing
                    ? Typewriter.cockpitSpot(requested, player.level().getBlockState(requested), -1, true).point()
                    : ThrottleLever.knob(player.level(), requested);
            if (knob != null) knob = SubLevels.toWorld(player.level(), requested, knob);
            int request = -1;
            if (knob != null && knob.distanceTo(centre) < 2.5 && (state.held || Visibility.visible(player, requested, knob))) {
                Vector3f model = context.frame().relativeToJoint(knob, new Vector3f());
                request = knob.subtract(player.position()).dot(SubLevels.at(player.level(), state.wheel).directionToWorld(state.right)) >= 0 ? 0 : 1;
                if (new Vector3f(model).sub(SHOULDERS[request]).length() > (state.held ? 24 : 22)) request = -1;
                if (request >= 0 && (state.motion.working() < 0 || state.motion.working() != request
                        || state.motion.moving < 0)) {
                    if (state.motion.mix(request) == 0) { state.lever[request].set(model); state.controlLocal[request] = null; }
                    state.throttle[request] = requested.immutable();
                    state.typing[request] = typing;
                }
            }
            Vec3 origin = context.frame().jointWorld(new Vector3f());
            Vector3f view = context.frame().relativeToJoint(origin.add(player.getViewVector(1)), new Vector3f());
            float head = (float) Math.toRadians(CockpitFacing.head((float) Math.toDegrees(CockpitFacing.angle(view.x, view.z)), 0));
            state.headYaw += IKMath.wrap(head - state.headYaw) * Smoothing.follow(context.dt(), .12);
            float pitch = (float) Math.atan2(view.y, Math.sqrt(view.x * view.x + view.z * view.z));
            state.headPitch += IKMath.wrap(pitch - state.headPitch) * Smoothing.follow(context.dt(), .12);
            state.request = request;
            // Refresh the wheel every solve, including while the other hand operates a side control.
            // A stored block-space point follows the craft, but not rotation of the wheel itself.
            boolean rightMain = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
            state.rim[rightMain ? 0 : 1] = WHEEL.hover(player, state.wheel, mount, hit);
            state.rim[rightMain ? 1 : 0] = WHEEL.supportHand(player, state.wheel, mount);
            if (state.rim[0] == null || state.rim[1] == null) { context.decide("none:rim"); return; }
            state.motion.advance(request, (float) context.dt());
            var space = SubLevels.at(player.level(), state.wheel);
            if (state.craft == null || !space.same(state.craft)) {
                state.craft = space;
                state.reference = space.tickToLocal(player.position());
                state.transport = new strm.emfcompat.animationadditions.transport.TransportMotion();
                state.pelvisLocal = null;
            }
            state.craft = space;
            var worldReference = space.tickToWorld(state.reference);
            state.transport.sample(player.tickCount, new org.joml.Vector3d(worldReference.x, worldReference.y, worldReference.z));
            var a = state.transport.acceleration;
            Vec3 originPoint = context.frame().jointWorld(new Vector3f());
            Vector3f force = context.frame().relativeToJoint(originPoint.add(a.x, 0, a.z), new Vector3f()).div(16);
            Vector3f reaction = new Vector3f(Math.max(-.14f, Math.min(.14f, force.z * .025f)), 0, Math.max(-.14f, Math.min(.14f, -force.x * .025f)));
            if (state.transport.warped) { reaction.zero(); state.pelvisLocal = null; }
            state.inertia.lerp(reaction, Smoothing.follow(context.dt(), .2));
            wantedLean.add(state.inertia);
            Map<Effector, float[]> aims = new EnumMap<>(Effector.class);
            for (int hand = 0; hand < 2; hand++) {
                Vec3 rim = SubLevels.toWorld(player.level(), state.wheel, state.rim[hand].point());
                Vector3f target = context.frame().relativeToJoint(rim, new Vector3f());
                float mix = state.motion.mix(hand);
                if (mix > 0 && state.throttle[hand] != null) {
                    BlockPos control = state.throttle[hand];
                    var controlBlock = player.level().getBlockState(control);
                    Vec3 local = state.typing[hand]
                            ? TYPEWRITER.matches(controlBlock) ? Typewriter.cockpitSpot(control, controlBlock, state.keys.key, hand == 0).point() : null
                            : ThrottleLever.knob(player.level(), control);
                    if (local != null) {
                        // Smooth key/knob motion in the block's own space, never behind a moving craft.
                        state.controlLocal[hand] = state.controlLocal[hand] == null ? local : state.controlLocal[hand].lerp(local, Smoothing.follow(context.dt(), .06));
                        state.lever[hand].set(context.frame().relativeToJoint(SubLevels.toWorld(player.level(), control, state.controlLocal[hand]), new Vector3f()));
                    }
                    target.lerp(state.lever[hand], mix);
                    wantedLean.y += (hand == 0 ? 1 : -1) * (float) Math.toRadians(10) * mix;
                    wantedLean.z += (hand == 0 ? -1 : 1) * (float) Math.toRadians(state.typing[hand] ? 3 + 5 * state.keys.effort : 3) * mix;
                    if (state.typing[hand]) wantedLean.x += (float) Math.toRadians(5) * state.keys.effort * mix;
                }
                // Prepare the torso against the destination path before the hand finishes its arc.
                // Fitting only the shortened transfer arc defers all reach until the final frame.
                state.reachGrips[hand].set(target);
                state.reachLocal[hand] = space.toLocal(context.frame().jointWorld(target));
                target.y -= state.motion.lift(hand);
                // A rigid FA arm transfers on an arc, rather than cutting through its shoulder.
                if (mix > .001f && mix < .999f) {
                    Vector3f direction = new Vector3f(target).sub(state.shoulders[hand]);
                    if (direction.lengthSquared() > 1e-5f) target.set(direction.normalize(11)).add(state.shoulders[hand]);
                }
                state.grips[hand].set(target);
                Vec3 point = context.frame().jointWorld(target);
                state.gripLocal[hand] = space.toLocal(point);
                var aim = OneBoneIK.solveXY(context.frame(), SHOULDERS[hand], point, 11, 0, 0);
                if (aim == null || aim.reach() > 2.25f) { context.decide("off:reach"); return; }
                aims.put(hand == 0 ? Effector.RIGHT_ARM : Effector.LEFT_ARM, new float[]{aim.x(), aim.y()});
            }
            if (state.motion.working() < 0) wantedLean.z += WheelGeometry.steeringRoll(state.grips[0].y, state.grips[1].y);
            // Native steering repeats use clicks. The pack's attack swing must not twist this grip.
            out.add(Candidate.of(id(), Category.USE, 14, 1, TIMING, aims).withQuietSwing(true));
            context.claimArms();
            state.shown = true;
            context.decide(request < 0 ? "wheel" : request == 0 ? typing ? "typing-R" : "throttle-R" : typing ? "typing-L" : "throttle-L");
        } finally {
            state.lean.lerp(wantedLean, Smoothing.follow(context.dt(), .18));
            if (strm.emfcompat.animationadditions.DebugLog.trace()
                    && context.now() - state.traceAt > 100_000_000L) {
                state.traceAt = context.now();
                org.slf4j.LoggerFactory.getLogger("EMFCompatCockpit").info(
                        "[CockpitTrace] seated={} shown={} moving={} away={} returning={} progress={} rightMix={} leftMix={} rightWeight={} leftWeight={} throttleHeld={} request={} rimRight={} rimLeft={} typing={} key={} effort={} rightTarget={} leftTarget={}",
                        Seated.seated(player), state.shown, state.motion.moving, state.motion.away, state.motion.returning,
                        state.motion.progress, state.motion.mix(0), state.motion.mix(1),
                        InteractionRuntime.weight(player.getUUID(), Effector.RIGHT_ARM, id()),
                        InteractionRuntime.weight(player.getUUID(), Effector.LEFT_ARM, id()), state.held, state.request,
                        state.rim[0] == null || state.wheel == null ? -1 : state.rim[0].point().distanceTo(WHEEL.swayCentre(player.level(), state.wheel, player.level().getBlockState(state.wheel))),
                        state.rim[1] == null || state.wheel == null ? -1 : state.rim[1].point().distanceTo(WHEEL.swayCentre(player.level(), state.wheel, player.level().getBlockState(state.wheel))),
                        state.request >= 0 && state.typing[state.request], state.keys.key, state.keys.effort, state.grips[0], state.grips[1]);
            }
        }
    }

    /** Rotate only this rendered model toward the wheel; camera and gameplay yaw stay free. */
    public static float orient(AbstractClientPlayer player, com.mojang.blaze3d.vertex.PoseStack stack) {
        State state = STATES.fresh(player.getUUID());
        if (state == null || !state.shown || !INSTANCE.isEnabled() || !Seated.seated(player)
                || !player.getVehicle().getUUID().equals(state.seat) || state.wheel == null
                || !EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return 0;
        var block = player.level().getBlockState(state.wheel);
        if (!WHEEL.matches(block)) return 0;
        Vec3 centre = SubLevels.toWorld(player.level(), state.wheel, WHEEL.swayCentre(player.level(), state.wheel, block));
        if (centre.distanceTo(player.getEyePosition()) > 2.25) return 0;
        var frame = strm.emfcompat.core.ik.IKFrame.capture(stack.last().pose(),
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        Vec3 origin = frame.jointWorld(new Vector3f());
        // A seated rider follows the deck's normal, including pitch and roll.
        Vec3 direction = centre.subtract(seatWorld(player));
        var space = SubLevels.at(player.level(), state.wheel);
        Vec3 up = space.directionToWorld(new Vec3(0, 1, 0));
        Vector3f local = frame.relativeToJoint(origin.add(direction), new Vector3f());
        Vector3f normal = frame.relativeToJoint(origin.add(up), new Vector3f());
        stack.mulPose(CockpitFacing.orientation(local, normal));
        return CockpitFacing.angle(local.x, local.z);
    }

    private static Vec3 seatWorld(AbstractClientPlayer player) {
        Vec3 at = player.getVehicle().getPosition(1);
        return SubLevels.toWorld(player.level(), BlockPos.containing(at), at);
    }
    public static boolean active(UUID uuid) {
        State s = STATES.fresh(uuid);
        return s != null && s.shown && INSTANCE.isEnabled() && s.player != null && Seated.seated(s.player)
                && s.player.getVehicle().getUUID().equals(s.seat) && EMFCompatCore.isCompatEnabled();
    }

    /** Keep contacts in the current draw's coordinates even when the bounded provider solve is skipped. */
    public static void frame(AbstractClientPlayer player, strm.emfcompat.core.ik.IKFrame frame) {
        State s = STATES.fresh(player.getUUID());
        if (s == null) return;
        s.frame = frame;
        if (s.craft != null) s.craft = s.craft.refresh();
        if (s.craft != null && s.shown) for (int hand = 0; hand < 2; hand++) {
            if (s.gripLocal[hand] != null) s.grips[hand].set(frame.relativeToJoint(s.craft.toWorld(s.gripLocal[hand]), new Vector3f()));
            if (s.reachLocal[hand] != null) s.reachGrips[hand].set(frame.relativeToJoint(s.craft.toWorld(s.reachLocal[hand]), new Vector3f()));
        }
    }

    /** Bounded turn above the seat, then aim from the actual pack shoulders. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !active(uuid) || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        float weight = Math.min(InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, INSTANCE.id()),
                InteractionRuntime.weight(uuid, Effector.LEFT_ARM, INSTANCE.id()));
        if (weight < 1e-3f) return;
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (r == null || l == null) return;
        Vector3f waist = new Vector3f((r.x + l.x) * .5f, (r.y + l.y) * .5f, (r.z + l.z) * .5f);
        Vector3f sourceWaist = new Vector3f(waist), seatDelta = new Vector3f();
        ModelPart sourceBody = parts.apply("body");
        Vector3f sourceRotation = sourceBody == null ? new Vector3f() : new Vector3f(sourceBody.xRot, sourceBody.yRot, sourceBody.zRot);
        if (state.frame != null && state.craft != null) {
            // Preserve the pack's mounting offset relative to the actual seat entity, not a fixed world point.
            // Native chairs supply their own passenger heights; the same anchor follows a moving sub-level.
            Vec3 nativeSeat = state.player.getVehicle().getPosition(1);
            var seatSpace = SubLevels.at(state.player.level(), BlockPos.containing(nativeSeat));
            // A seat entity already lives in the sub-level plot: do not round-trip its position
            // through independently interpolated render transforms to choose its support block.
            Vec3 seat = seatSpace.same(state.craft) ? nativeSeat : state.craft.toLocal(seatSpace.toWorld(nativeSeat));
            seatSurface(state, seat);
            // Finish the native mounting transition, then keep its horizontal mounting offset.
            // A tall chair must support the thigh volume, not bury the captured hip in its cushion.
            if (state.pelvisLocal == null || state.player.tickCount - state.seatSince < 12)
                state.pelvisLocal = state.craft.toLocal(state.frame.jointWorld(waist)).subtract(seat);
            Vec3 hip = seat.add(state.pelvisLocal);
            float draw = traben.entity_model_features.models.animation.state.EMFState.getFrameCounter();
            if (!Double.isNaN(state.seatTop)) {
                double height = state.seatTop + 2.25 / 16;
                if (Double.isNaN(state.seatHeight)) state.seatHeight = height;
                if (state.seatFrame != draw) {
                    long now = System.nanoTime();
                    double dt = state.seatAt == 0 ? .05 : Math.min(.1, (now - state.seatAt) * 1e-9);
                    state.seatFrame = draw;
                    state.seatAt = now;
                    state.seatHeight += (height - state.seatHeight) * Smoothing.follow(dt, .12);
                }
                hip = new Vec3(hip.x, state.seatHeight, hip.z);
            }
            Vector3f anchor = state.frame.relativeToJoint(state.craft.toWorld(hip), new Vector3f());
            // Smooth only a change of seat height in deck coordinates. Render-frame compensation
            // must follow the actual moving seat immediately, otherwise the thighs lag through it.
            Vector3f delta = new Vector3f(anchor).sub(waist);
            float lateral = (float) Math.hypot(delta.x, delta.z);
            if (lateral > 1.5f) { delta.x *= 1.5f / lateral; delta.z *= 1.5f / lateral; }
            delta.y = Math.max(-8, Math.min(8, delta.y));
            seatDelta.set(delta);
            state.seatGap = new Vector3f(anchor).sub(new Vector3f(waist).add(new Vector3f(delta).mul(weight))).length() / 16;
            for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm", "right_leg", "left_leg"}) {
                var part = parts.apply(name);
                if (part != null) part.setPos(part.x + delta.x * weight, part.y + delta.y * weight, part.z + delta.z * weight);
            }
            waist.add(delta.mul(weight));
            // FA+Player has rigid legs: lowering the whole thigh to plant a foot drives it through the chair.
            // Keep a seated thigh above the cushion until an articulated lower leg can reach a footrest.
            if (!Double.isNaN(state.seatTop)) for (ModelPart leg : new ModelPart[]{r, l}) {
                leg.xRot += IKMath.wrap(-(float) Math.PI / 2 - leg.xRot) * weight;
                leg.zRot *= 1 - weight;
            }
        }
        // The render stack already faces the seat's wheel. FA's source torso yaw still follows
        // native camera/body lag; undo that local yaw before fitting mechanical contacts.
        // Otherwise the reach solver chases the camera's twist even on a stationary cushion.
        ModelPart body = parts.apply("body");
        if (body != null) {
            float unwind = -body.yRot * weight;
            Quaternionf neutral = new Quaternionf().rotationY(unwind);
            for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
                ModelPart part = parts.apply(name);
                if (part == null) continue;
                Vector3f pos = neutral.transform(new Vector3f(part.x, part.y, part.z).sub(waist)).add(waist);
                part.setPos(pos.x, pos.y, pos.z);
            }
            body.yRot += unwind;
        }
        Quaternionf turn = new Quaternionf().rotationZYX(state.lean.z * weight, state.lean.y * weight, state.lean.x * weight);
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
            ModelPart part = parts.apply(name);
            if (part == null) continue;
            Vector3f pos = turn.transform(new Vector3f(part.x, part.y, part.z).sub(waist)).add(waist);
            part.setPos(pos.x, pos.y, pos.z);
            if (!name.equals("head") && !name.equals("hat")) {
                part.xRot += state.lean.x * weight;
                part.yRot += state.lean.y * weight;
                part.zRot += state.lean.z * weight;
            }
        }
        if (state.contactFrame != traben.entity_model_features.models.animation.state.EMFState.getFrameCounter()) {
            long now = System.nanoTime();
            double dt = state.contactAt == 0 ? 0 : Math.min(.1, (now - state.contactAt) * 1e-9);
            state.contactAt = now;
            state.contactFrame = traben.entity_model_features.models.animation.state.EMFState.getFrameCounter();
            var ra = parts.apply("right_arm");
            var la = parts.apply("left_arm");
            Quaternionf wanted = new Quaternionf();
            // Fit the actual pack shoulders to both contacts for either side control.
            // A low throttle needs the same seated lean as the keyboard.
            if (ra != null && la != null)
                wanted = CockpitContact.fit(state.contact, new Vector3f(ra.x, ra.y, ra.z).sub(waist), new Vector3f(la.x, la.y, la.z).sub(waist),
                        new Vector3f(state.reachGrips[0]).sub(waist), new Vector3f(state.reachGrips[1]).sub(waist), state.motion.working() >= 0);
            if (ra != null && la != null) state.contact.set(CockpitContact.follow(state.contact, wanted, Smoothing.follow(dt, state.motion.working() >= 0 ? .06 : .2),
                    new Vector3f(ra.x, ra.y, ra.z).sub(waist), new Vector3f(la.x, la.y, la.z).sub(waist),
                    new Vector3f(state.reachGrips[0]).sub(waist), new Vector3f(state.reachGrips[1]).sub(waist)));
        }
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
            var p = parts.apply(name);
            if (p == null) continue;
            Vector3f at = state.contact.transform(new Vector3f(p.x, p.y, p.z).sub(waist)).add(waist);
            p.setPos(at.x, at.y, at.z);
            if (!name.equals("head") && !name.equals("hat")) {
                var angles = new Quaternionf(state.contact).mul(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot)).getEulerAnglesZYX(new Vector3f());
                p.setRotation(angles.x, angles.y, angles.z);
            }
        }
        ModelPart head = parts.apply("head");
        if (head != null && InteractionRuntime.aim(uuid, Effector.HEAD) == null) {
            head.yRot += IKMath.wrap(state.headYaw - head.yRot) * weight;
            head.xRot += IKMath.wrap(state.headPitch - head.xRot) * weight;
        }
        for (int hand = 0; hand < 2; hand++) {
            ModelPart arm = parts.apply(hand == 0 ? "right_arm" : "left_arm");
            if (arm == null) continue;
            state.shoulders[hand].set(arm.x, arm.y, arm.z);
            Vector3f to = new Vector3f(state.grips[hand]).sub(arm.x, arm.y, arm.z).normalize();
            float pitch = -(float) Math.acos(Math.max(-1, Math.min(1, to.y)));
            float yaw = (float) Math.atan2(-to.x, -to.z);
            arm.xRot += IKMath.wrap(pitch - arm.xRot) * weight;
            arm.yRot += IKMath.wrap(yaw - arm.yRot) * weight;
            arm.zRot *= 1 - weight;
            Vector3f palm = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(new Vector3f(0, 11 * arm.yScale, 0)).add(arm.x, arm.y, arm.z);
            float gap = palm.distance(state.grips[hand]) / 16;
            if (hand == 0) state.rightGap = gap;
            else state.leftGap = gap;
        }
        state.snapshot.clear();
        if (!strm.emfcompat.animationadditions.DebugLog.trace()) return;
        state.snapshot.put("sourceBodyRotation", java.util.List.of(sourceRotation.x, sourceRotation.y, sourceRotation.z));
        state.snapshot.put("sourceWaist", java.util.List.of(sourceWaist.x, sourceWaist.y, sourceWaist.z));
        state.snapshot.put("seatDelta", java.util.List.of(seatDelta.x, seatDelta.y, seatDelta.z));
        Vector3f contactAngles = new Quaternionf(state.contact).getEulerAnglesZYX(new Vector3f());
        state.snapshot.put("contactRotation", java.util.List.of(contactAngles.x, contactAngles.y, contactAngles.z));
        state.snapshot.put("leanRotation", java.util.List.of(state.lean.x, state.lean.y, state.lean.z));
        state.snapshot.put("shown", state.shown);
        state.snapshot.put("rightGap", state.rightGap);
        state.snapshot.put("leftGap", state.leftGap);
        state.snapshot.put("rightMix", state.motion.mix(0));
        state.snapshot.put("leftMix", state.motion.mix(1));
        state.snapshot.put("seatGap", state.seatGap);
        state.snapshot.put("inertiaPitch", state.inertia.x);
        state.snapshot.put("inertiaRoll", state.inertia.z);
        state.snapshot.put("speed", state.transport.speed);
        state.snapshot.put("seat", state.seat.toString());
        state.snapshot.put("craft", state.craft != null && !state.craft.isWorld());
        if (!Double.isNaN(state.seatTop)) {
            state.snapshot.put("seatSurfaceY", state.seatTop);
            state.snapshot.put("seatedHipY", state.seatHeight);
            state.snapshot.put("pelvisAboveSeat", state.craft.toLocal(state.frame.jointWorld(waist)).y - state.seatTop);
            state.snapshot.put("rightSeatPenetration", seatPenetration(state, r));
            state.snapshot.put("leftSeatPenetration", seatPenetration(state, l));
        }
        state.snapshot.put("rightThighPitch", r.xRot);
        state.snapshot.put("leftThighPitch", l.xRot);
        state.snapshot.put("key", state.keys.key);
        state.snapshot.put("request", state.request);
        Vec3 origin = state.frame.jointWorld(new Vector3f());
        Vec3 wheel = SubLevels.toWorld(state.player.level(), state.wheel, WHEEL.swayCentre(state.player.level(), state.wheel, state.player.level().getBlockState(state.wheel)));
        Vector3f forward = state.frame.relativeToJoint(origin.add(wheel.subtract(seatWorld(state.player))), new Vector3f());
        state.snapshot.put("facingErrorDegrees", Math.toDegrees(Math.atan2(forward.x, -forward.z)));
        Vector3f up = state.frame.relativeToJoint(origin.add(state.craft.directionToWorld(new Vec3(0, 1, 0))), new Vector3f()).normalize();
        state.snapshot.put("deckUpErrorDegrees", Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, -up.y)))));
    }
    /** Render-frame measurements: targets and palms are sampled together, without stale trace pairing. */
    public static Map<String, Object> snapshot(UUID player) {
        State s = STATES.fresh(player);
        return s == null ? Map.of() : new LinkedHashMap<>(s.snapshot);
    }
    private static void seatSurface(State s, Vec3 seat) {
        s.seatBlock = BlockPos.containing(seat);
        var block = s.player.level().getBlockState(s.seatBlock);
        s.seatBoxes = block.getCollisionShape(s.player.level(), s.seatBlock).toAabbs();
        s.seatTop = Double.NaN;
        for (var box : s.seatBoxes) {
            double x = seat.x - s.seatBlock.getX(), z = seat.z - s.seatBlock.getZ();
            if (x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ)
                s.seatTop = Double.isNaN(s.seatTop) ? s.seatBlock.getY() + box.maxY : Math.max(s.seatTop, s.seatBlock.getY() + box.maxY);
        }
    }
    private static float seatPenetration(State s, ModelPart leg) {
        var q = new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot);
        var centre = q.transform(new Vector3f(0, 6 * leg.yScale, 0)).add(leg.x, leg.y, leg.z);
        Vec3 local = s.craft.toLocal(s.frame.jointWorld(centre));
        Vector3f at = new Vector3f((float)(local.x - s.seatBlock.getX()), (float)(local.y - s.seatBlock.getY()), (float)(local.z - s.seatBlock.getZ()));
        Vector3f[] half = {new Vector3f(2.25f * leg.xScale, 0, 0), new Vector3f(0, 6.25f * leg.yScale, 0), new Vector3f(0, 0, 2.25f * leg.zScale)};
        for (int i = 0; i < 3; i++) {
            Vec3 endpoint = s.craft.toLocal(s.frame.jointWorld(q.transform(half[i]).add(centre)));
            half[i].set((float)(endpoint.x - local.x), (float)(endpoint.y - local.y), (float)(endpoint.z - local.z));
        }
        float penetration = 0;
        for (var box : s.seatBoxes) penetration = Math.max(penetration, SeatLegClearance.penetration(at, half,
                new Vector3f((float) box.minX, (float) box.minY, (float) box.minZ), new Vector3f((float) box.maxX, (float) box.maxY, (float) box.maxZ)));
        return penetration;
    }
}
