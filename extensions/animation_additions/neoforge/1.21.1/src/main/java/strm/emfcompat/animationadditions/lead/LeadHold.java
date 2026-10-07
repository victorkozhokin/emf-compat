package strm.emfcompat.animationadditions.lead;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.*;
import strm.emfcompat.core.*;
import strm.emfcompat.core.ik.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Ease;
import strm.emfcompat.animationadditions.interaction.Body;

/** An actual player-held lead owns one hand; other interactions can take that hand normally. */
public final class LeadHold implements InteractionProvider {
    public static final LeadHold INSTANCE = new LeadHold();
    public static final String KEY_ENABLED = "lead.enabled";
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    private static final Candidate.Timing TIMING = new Candidate.Timing(.16, .2, .08);
    private static final class State {
        final LeadMotion motion = new LeadMotion();
        final LeadStopGesture stop = new LeadStopGesture();
        final Vector3f palm = new Vector3f(), direction = new Vector3f();
        UUID animal, attachingAnimal;
        Effector attachingHand;
        Effector hand = Effector.RIGHT_ARM;
        boolean active, grounded, hasPalm;
        AbstractClientPlayer player;
        float effort, stopPull;
        final LeadStance.State stance = new LeadStance.State();
        int count;
        double distance;
        IKFrame frame;
        Vec3 playerPosition, drawnPalm;
        Vec3 relative, rawRelative;
        long settleUntil;
        double speed;
        long traceAt;
    }
    public String id() { return "LeadHold"; }
    public boolean isEnabled() { return EMFCompatConfig.getBoolean(KEY_ENABLED, true); }
    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Hold animal leads", true,
                "On", "Hold the real lead in one hand and brace against tension and outward jerks.",
                "Off", "Leave the lead and hand pose to Minecraft.");
    }

    public static void attachHand(AbstractClientPlayer player, Entity target, net.minecraft.world.InteractionHand hand) {
        if (!player.getItemInHand(hand).is(Items.LEAD) || !(target instanceof Leashable lead)
                || lead.getLeashHolder() == player) return;
        State s = STATES.seen(player.getUUID(), System.nanoTime()).value;
        s.attachingAnimal = target.getUUID();
        boolean right = Body.right(player, hand);
        s.attachingHand = right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
    }

    /** Same animal attachment transform as the vanilla lead renderer. */
    static Vec3 anchor(Entity animal, float partial) {
        Vec3 offset = animal.getLeashOffset(partial);
        float yaw = animal instanceof LivingEntity living
                ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : animal.getYRot();
        double angle = Math.toRadians(yaw) + Math.PI / 2;
        return animal.getPosition(partial).add(Math.cos(angle) * offset.z + Math.sin(angle) * offset.x,
                offset.y, Math.sin(angle) * offset.z - Math.cos(angle) * offset.x);
    }

    public void collect(InteractionContext context, List<Candidate> out) {
        var player = context.player();
        State s = STATES.seen(player.getUUID(), context.now()).value;
        s.active = false;
        s.count = 0;
        if (player.isSleeping() || player.isFallFlying() || player.isInWaterOrBubble() || player.isAutoSpinAttack()) {
            context.decide("off:pose");
            return;
        }
        Entity chosen = null;
        double farthest = -1;
        for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(12),
                e -> e instanceof Leashable lead && lead.getLeashHolder() == player && e.isAlive())) {
            s.count++;
            double distance = e.distanceTo(player);
            // Keep the previous animal through small distance changes instead of flickering between leads.
            double score = distance + (e.getUUID().equals(s.animal) ? .35 : 0);
            if (score > farthest) { farthest = score; chosen = e; }
        }
        if (chosen == null) { s.animal = null; s.hasPalm = false; context.decide("none"); return; }
        boolean fresh = s.animal == null || !chosen.getUUID().equals(s.animal);
        if (s.animal == null) {
            boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
            boolean off = player.getOffhandItem().is(Items.LEAD) && !player.getMainHandItem().is(Items.LEAD);
            s.hand = chosen.getUUID().equals(s.attachingAnimal) && s.attachingHand != null ? s.attachingHand
 : mainRight != off ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
        }
        s.animal = chosen.getUUID();
        s.frame = context.frame();
        s.player = player;
        s.distance = chosen.distanceTo(player);
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        s.playerPosition = player.getPosition(partial);
        Vec3 relative = chosen.getPosition(partial).subtract(s.playerPosition);
        Vec3 raw = chosen.position().subtract(player.position());
        double radial = relative.lengthSqr() < 1e-6 ? 0 : chosen.getDeltaMovement()
                .subtract(player.getDeltaMovement()).dot(relative.normalize()) * 20;
        // Interpolated relative motion also works when a mob's network velocity is stale.
        boolean warped = s.rawRelative != null && raw.distanceTo(s.rawRelative) > 2;
        s.rawRelative = raw;
        if (warped) s.settleUntil = context.now() + 350_000_000L;
        boolean settling = context.now() < s.settleUntil;
        if (!fresh && !settling && s.relative != null && context.dt() > 1e-4) {
            double observed = relative.subtract(s.relative).dot(relative.normalize()) / context.dt();
            s.speed += (observed - s.speed) * Smoothing.follow(context.dt(), .08);
            radial = Math.max(radial, s.speed);
        } else s.speed = 0;
        s.relative = relative;
        s.motion.advance(s.distance, radial, context.dt(), fresh || settling);
        Vector3f shoulder = new Vector3f(s.hand == Effector.RIGHT_ARM ? -5 : 5, 2, 0);
        Vector3f direction = context.frame().relativeToJoint(anchor(chosen, partial), shoulder);
        if (direction.lengthSquared() < 1e-6) direction.set(0, 0, -1);
        else direction.normalize();
        if (fresh) s.direction.set(direction);
        else s.direction.lerp(direction, Smoothing.follow(context.dt(), .12));
        // A following animal often stays short of vanilla's elastic distance. A small
        // walking effort shows the trailing grip without inventing an elastic impulse.
        double walkSpeed = player.getDeltaMovement().horizontalDistance() * 20;
        s.stopPull = s.stop.advance(walkSpeed, s.distance, context.dt(), fresh || warped || !player.onGround());
        float walking = (float) Math.min(1, player.getDeltaMovement().horizontalDistance() * 20 / 3);
        float trailing = Ease.smooth((s.direction.z + .1f) / .7f);
        float distance = Ease.smooth((float)(s.distance - 2) / 2);
        float wanted = Math.max(s.motion.load, walking * trailing * distance * .4f);
        s.effort += (wanted - s.effort) * Smoothing.follow(context.dt(), .16);
        Vector3f wantedPalm = LeadPose.grip(s.direction, s.hand == Effector.RIGHT_ARM, s.effort, s.motion.jerk);
        wantedPalm.z *= 1 - s.stopPull * .45f;
        wantedPalm.x *= 1 - s.stopPull * .2f;
        wantedPalm.y -= s.stopPull * .5f;
        if (!s.hasPalm) { s.palm.set(wantedPalm); s.hasPalm = true; }
        else s.palm.lerp(wantedPalm, Smoothing.follow(context.dt(), .1));
        Vector3f aim = LeadPose.angles(LeadPose.swing(s.palm));
        out.add(Candidate.single(id(), Category.PASSIVE, 30, 1, TIMING, s.hand, new float[]{aim.x, aim.y}).withTarget(s.animal));
        // This grip follows the actual animated shoulder in capture(), not a fixed
        // world contact. Generic world-target correction would undo that distinction.
        s.grounded = player.onGround() && !player.isPassenger();
        s.active = true;
        context.decide(s.motion.jerk > .15 ? "jerk" : s.motion.load > .1 ? "tension" : "slack");
    }

    public static float[] torsoHint(UUID uuid) {
        State s = STATES.fresh(uuid);
        if (s == null || !s.active || !s.grounded || !INSTANCE.isEnabled()) return null;
        float w = InteractionRuntime.weight(uuid, s.hand, INSTANCE.id());
        float effort = s.effort * (1 + s.motion.jerk * .3f) * w;
        return new float[]{s.direction.z * (float) Math.toRadians(9) * effort, 0,
                s.direction.x * (float) Math.toRadians(10) * effort};
    }

    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.player == null) return;
        float owned = s.active && INSTANCE.isEnabled() ? InteractionRuntime.weight(uuid, s.hand, INSTANCE.id()) : 0;
        LeadStance.apply(s.stance, s.player, s.frame, parts, s.direction, s.effort * owned);
    }

    /** Capture the final animated palm, after every torso and contact correction. */
    public static void capture(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || !s.active || s.frame == null) return;
        ModelPart arm = parts.apply(s.hand.part);
        if (arm == null) return;
        float owned = InteractionRuntime.weight(uuid, s.hand, INSTANCE.id());
        if (owned > 1e-3f) {
            // Shortest swing from a downward arm admits both forward and backward
            // grips without the negative-acos/180-degree-yaw pole flip.
            Quaternionf current = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot);
            Vector3f angles = LeadPose.angles(current.slerp(LeadPose.swing(s.palm), owned));
            arm.setRotation(angles.x, angles.y, angles.z);
        }
        Vector3f palm = Body.tip(arm, 11);
        s.drawnPalm = s.frame.jointWorld(palm);
        long now = System.nanoTime();
        if (strm.emfcompat.animationadditions.DebugLog.trace() && now - s.traceAt > 100_000_000L) {
            s.traceAt = now;
            org.slf4j.LoggerFactory.getLogger("EMFCompatLead").info(
                    "[LeadTrace] count={} distance={} load={} jerk={} effort={} stopPull={} right={} weight={} palmX={} palmY={} palmZ={}",
                    s.count, s.distance, s.motion.load, s.motion.jerk, s.effort, s.stopPull, s.hand == Effector.RIGHT_ARM,
                    InteractionRuntime.weight(uuid, s.hand, INSTANCE.id()), s.drawnPalm.x, s.drawnPalm.y, s.drawnPalm.z);
        }
    }

    /** Only the rendered endpoint changes: attaching, breaking and forces stay vanilla. */
    public static Vec3 rope(AbstractClientPlayer player, float partial, Vec3 vanilla) {
        State s = STATES.fresh(player.getUUID());
        if (s == null || !s.active || s.drawnPalm == null || !INSTANCE.isEnabled()
                || !EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return vanilla;
        float w = InteractionRuntime.weight(player.getUUID(), s.hand, INSTANCE.id());
        if (w < 1e-3) return vanilla;
        Vec3 corrected = s.drawnPalm.add(player.getPosition(partial).subtract(s.playerPosition));
        return vanilla.lerp(corrected, w);
    }
}
