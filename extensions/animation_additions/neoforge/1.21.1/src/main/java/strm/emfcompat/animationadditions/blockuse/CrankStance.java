package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.torso.PelvisFollow;
import traben.entity_model_features.models.animation.state.EMFState;
import java.util.function.Function;

/** Small grounded setup steps; rotation transfers weight rather than walking every turn. */
final class CrankStance {
    static final class State {
        AbstractClientPlayer player;
        IKFrame space;
        Float angle;
        int direction;
        boolean eligible, turning;
        float activity, load, frame = -1;
        long at, motionAt, loggedAt;
        final Vector3f[] feet = {new Vector3f(), new Vector3f()};
        final Vector3f start = new Vector3f(), end = new Vector3f();
        int stepping = -1;
        float progress;
    }

    static void observe(State s, AbstractClientPlayer player, IKFrame space, Float angle) {
        s.player=player; s.space=space;
        s.eligible=player.onGround() && !player.isPassenger() && player.getDeltaMovement().horizontalDistanceSqr()<.0004;
        boolean moved=angle!=null && s.angle!=null && Math.abs(CrankStanceMath.delta(s.angle,angle))>.05f;
        if (moved) {s.direction=CrankStanceMath.delta(s.angle,angle)>0 ? 1 : -1;s.motionAt=System.nanoTime();}
        s.turning=s.eligible && angle!=null && System.nanoTime()-s.motionAt<150_000_000L;
        s.angle=angle;
    }

    static float apply(State s, Function<String,ModelPart> parts, float owned) {
        if (s.player==null || s.space==null) return 0;
        ModelPart[] legs={parts.apply("right_leg"),parts.apply("left_leg")};
        if (legs[0]==null || legs[1]==null) return 0;
        float frame=EMFState.getFrameCounter();
        if (s.frame!=frame) {
            long now=System.nanoTime(); double dt=s.at==0 ? 0 : Math.min(.1,(now-s.at)*1e-9); s.at=now;s.frame=frame;
            if (!s.eligible) {
                s.feet[0].zero();s.feet[1].zero();s.stepping=-1;s.activity=s.load=0;
            } else {
                s.activity+=(s.turning && owned>.5f ? 1-s.activity : -s.activity)*Smoothing.follow(dt,.18);
                boolean turning=s.activity>.15f;
                if (s.stepping<0) {
                    for (int i=0;i<2;i++) {
                        Vector3f desired=CrankStanceMath.stance(i==0,s.player.isCrouching(),s.direction,turning);
                        if (desired.distanceSquared(s.feet[i])>.01f && safe(s,legs[i],s.feet[i],desired)) {
                            s.stepping=i;s.start.set(s.feet[i]);s.end.set(desired);s.progress=0;break;
                        }
                    }
                }
                if (s.stepping>=0) {
                    // Recheck the landing before lifting: a changed block cancels the step.
                    if (!safe(s,legs[s.stepping],s.start,s.end)) {s.feet[s.stepping].set(s.start);s.stepping=-1;}
                    else {
                        s.progress=Math.min(1,s.progress+(float)dt/.32f);
                        s.feet[s.stepping].set(s.start).lerp(s.end,CrankStanceMath.ease(s.progress));
                        if (s.progress>=1) s.stepping=-1;
                    }
                }
                float desiredLoad=s.angle==null ? 0 : s.activity*s.direction*(float)Math.sin(Math.toRadians(s.angle))*.7f;
                s.load+=(desiredLoad-s.load)*Smoothing.follow(dt,.18);
            }
        }
        Vector3f r=new Vector3f(s.feet[0]), l=new Vector3f(s.feet[1]);
        if (s.stepping>=0) (s.stepping==0?r:l).y -= CrankStanceMath.lift(s.progress)*(s.player.isCrouching()?.4f:.65f);
        float twist=(float)Math.toRadians(4)*s.activity;
        PelvisFollow.step(parts,r,l,-twist,twist);
        if (strm.emfcompat.animationadditions.DebugLog.trace()
                && System.nanoTime()-s.loggedAt>100_000_000L) {
            s.loggedAt=System.nanoTime();
            org.slf4j.LoggerFactory.getLogger("EMFCompatBlockUse").info(
                    "[StanceTrace] turning={} direction={} activity={} step={} progress={} right={} left={} load={}",
                    s.turning,s.direction,s.activity,s.stepping,s.progress,r,l,s.load);
        }
        return s.load;
    }

    private static boolean safe(State s, ModelPart leg, Vector3f from, Vector3f to) {
        Vector3f sole=new Quaternionf().rotationZYX(leg.zRot,leg.yRot,leg.xRot)
                .transform(new Vector3f(0,12*leg.yScale,0)).add(leg.x,leg.y,leg.z);
        double supportY=Double.NaN;
        // Entire short path and a small sole footprint need nearly level collision support.
        for (float t : new float[]{0,.5f,1}) {
            Vec3 point=s.space.jointWorld(new Vector3f(sole).add(new Vector3f(from).lerp(to,t)));
            for (double x : new double[]{-.10,0,.10}) for (double z : new double[]{-.10,0,.10}) {
                Vec3 p=point.add(x,0,z);
                var hit=s.player.level().clip(new ClipContext(p.add(0,.3,0),p.add(0,-.3,0),
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,s.player));
                if (hit.getType()!=HitResult.Type.BLOCK || Math.abs(hit.getLocation().y-point.y)>.16) return false;
                if (Double.isNaN(supportY)) supportY=hit.getLocation().y;
                else if (Math.abs(hit.getLocation().y-supportY)>.04) return false;
            }
        }
        return true;
    }
}
