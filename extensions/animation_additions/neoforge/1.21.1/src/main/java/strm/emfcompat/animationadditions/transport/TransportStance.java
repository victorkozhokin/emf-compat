package strm.emfcompat.animationadditions.transport;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.torso.PelvisFollow;
import strm.emfcompat.animationadditions.torso.ClearanceOffset;
import strm.emfcompat.core.ik.IKFrame;
import traben.entity_model_features.models.animation.state.EMFState;
import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Ease;
import strm.emfcompat.animationadditions.interaction.Body;

/** One brace step at a time on the same deck; no extra gait over FA walking. */
final class TransportStance {
    final Vector3f[] feet = {new Vector3f(), new Vector3f()};
    final Vector3f start = new Vector3f(), end = new Vector3f();
    final ClearanceOffset hip = new ClearanceOffset();
    int step = -1;
    float progress = 1, frame = -1, stanceWeight, ropeLift;
    long at;
    void apply(AbstractClientPlayer player, IKFrame space, SupportSearch.Deck deck,
               Function<String, ModelPart> parts, Vector3f force, float load, float owned, boolean walking, float rope, boolean rightHand, boolean helper, net.minecraft.world.phys.Vec3 grip) {
        ModelPart[] legs = {parts.apply("right_leg"), parts.apply("left_leg")};
        if (space == null || legs[0] == null || legs[1] == null) return;
        float counter = EMFState.getFrameCounter();
        if (counter != frame) {
            long now = System.nanoTime();
            double dt = at == 0 ? 0 : Math.min(.1, (now - at) * 1e-9);
            at = now;
            frame = counter;
            stanceWeight += (owned - stanceWeight) * Smoothing.follow(dt, .16);
            boolean enabled = deck != null && player.onGround() && !player.isPassenger() && !walking && owned > .7f;
            boolean planted = enabled && safe(player, space, deck, legs[rightHand ? 1 : 0], feet[rightHand ? 1 : 0], feet[rightHand ? 1 : 0]);
            boolean edge = enabled && rope > .7f && grip != null && overEdge(player, space, deck, grip, ropeLift > .1f);
            float wantedLift = RopePoseMath.edgeLift(enabled, planted, edge, player.isCrouching(), helper) * rope * owned;
            boolean lower = false;
            if (!enabled) { step = -1; for (var f : feet) f.mul(1 - Smoothing.follow(dt, .14)); }
            else {
                for (int i = 0; i < 2; i++) if (!safe(player, space, deck, legs[i], new Vector3f(), feet[i])) {
                    feet[i].zero();
                    if (step == i) step = -1;
                }
                if (step < 0) for (int i = 0; i < 2; i++) {
                    // A held cord has a settled stance. Circular acceleration must not restart steps.
                    Vector3f target = (rope > .7f ? RopePoseMath.foot(i == 0) : BraceMath.foot(i == 0, force, load)).mul(owned);
                    if (target.distanceSquared(feet[i]) > .12f && safe(player, space, deck, legs[i], feet[i], target)) {
                        if (i != (rightHand ? 0 : 1) && ropeLift > .03f) { lower = true; break; }
                        step = i;
                        start.set(feet[i]);
                        end.set(target);
                        progress = 0;
                        break;
                    }
                }
                if (step >= 0) {
                    if (!safe(player, space, deck, legs[step], start, end)) { step = -1; }
                    else {
                        progress = Math.min(1, progress + (float) dt / .32f);
                        feet[step].set(start).lerp(end, Ease.smooth(progress));
                        if (progress >= 1) step = -1;
                    }
                }
            }
            if (lower || step >= 0 && step != (rightHand ? 0 : 1)) wantedLift = 0;
            if (step >= 0 && step != (rightHand ? 0 : 1)) ropeLift = 0; // Other foot is stepping: this sole stays planted.
            ropeLift += (wantedLift - ropeLift) * Smoothing.follow(dt, .14);
        }
        Vector3f r = new Vector3f(feet[0]), l = new Vector3f(feet[1]);
        (rightHand ? r : l).add(0, -ropeLift, ropeLift * 1.25f); // Lift and retract, rather than march forwards.
        if (step >= 0)(step == 0 ? r : l).y -= (float) Math.sin(Math.PI * progress) * (player.isCrouching() ? .2f : .4f);
        Vector3f before = new Vector3f((legs[0].x + legs[1].x) * .5f, (legs[0].y + legs[1].y) * .5f, (legs[0].z + legs[1].z) * .5f);
        float twist = stanceWeight * (float) Math.toRadians(5);
        PelvisFollow.step(parts, r, l, -twist, twist);
        Vector3f delta = new Vector3f((legs[0].x + legs[1].x) * .5f, (legs[0].y + legs[1].y) * .5f, (legs[0].z + legs[1].z) * .5f).sub(before);
        Vector3f correction = hip.sample(counter, System.nanoTime(), delta).sub(delta);
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
            var p = parts.apply(name);
            if (p != null) p.setPos(p.x + correction.x, p.y + correction.y, p.z + correction.z);
        }
    }
    /** Two short probes towards the held cord, in the supporting sub-level's space. */
    private static boolean overEdge(AbstractClientPlayer player, IKFrame frame, SupportSearch.Deck deck,
                                    net.minecraft.world.phys.Vec3 grip, boolean holding) {
        if (!deck.space().valid()) return false;
        // Grip, deck and feet must use the same render instant on a moving craft.
        var base = frame.jointWorld(new Vector3f(0, 24, 0));
        var direction = grip.subtract(base).multiply(1, 0, 1);
        if (direction.lengthSqr() < .01) return false;
        direction = direction.normalize();
        var space = deck.space().refresh();
        for (double distance : new double[]{holding ? .32 : .42, holding ? .42 : .52}) {
            var point = base.add(direction.scale(distance));
            var hit = player.level().clip(new ClipContext(space.toLocal(point.add(0, .15, 0)), space.toLocal(point.add(0, -.65, 0)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && space.toWorld(hit.getLocation()).y > base.y - .45) return false;
        }
        return true;
    }
    private static boolean safe(AbstractClientPlayer player, IKFrame frame, SupportSearch.Deck deck,
                                ModelPart leg, Vector3f from, Vector3f to) {
        if (!deck.space().valid()) return false;
        var space = deck.space().refresh();
        Vector3f sole = Body.tip(leg, 12);
        for (float t : new float[]{0, .5f, 1}) for (float x : new float[]{-.09f, 0, .09f}) for (float z : new float[]{-.09f, 0, .09f}) {
            var point = frame.jointWorld(new Vector3f(sole).add(new Vector3f(from).lerp(to, t))).add(x, 0, z);
            var hit = player.level().clip(new ClipContext(space.toLocal(point.add(0, .3, 0)), space.toLocal(point.add(0, -.3, 0)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) return false;
            var actual = space.toWorld(hit.getLocation());
            var normal = space.directionToWorld(net.minecraft.world.phys.Vec3.atLowerCornerOf(hit.getDirection().getNormal())).normalize();
            if (normal.y < .5 || actual.distanceTo(point) > .17) return false;
        }
        return true;
    }
}
