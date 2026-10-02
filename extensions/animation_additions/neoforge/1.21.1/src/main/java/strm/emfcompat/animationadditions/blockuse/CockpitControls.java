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

/** Seated wheel / side throttle handovers. The seat keeps the pelvis and legs fixed. */
public final class CockpitControls implements InteractionProvider {
    public static final CockpitControls INSTANCE=new CockpitControls();
    private static final EntityStates<State> STATES=new EntityStates<>(State::new);
    private static final SteeringWheel WHEEL=new SteeringWheel();
    private static final Vector3f[] SHOULDERS={new Vector3f(-5,2,0),new Vector3f(5,2,0)};
    private static final Candidate.Timing TIMING=new Candidate.Timing(.12,.18,.06);
    private static final class State {
        UUID seat;
        BlockPos wheel;
        final BlockPos[] throttle=new BlockPos[2];
        final BlockTarget.Spot[] rim=new BlockTarget.Spot[2];
        final Vector3f[] grips={new Vector3f(),new Vector3f()};
        final CockpitMotion motion=new CockpitMotion();
        final Vector3f lean=new Vector3f();
        Vec3 right;
        final Vector3f[] lever={new Vector3f(),new Vector3f()};
        boolean shown, held;
        int request=-1;
        long traceAt;
    }
    public String id() {return "CockpitControls";}
    public boolean isEnabled() {return BlockUse.INSTANCE.isEnabled() && ButtonPress.INSTANCE.isEnabled();}

    public void collect(InteractionContext context,List<Candidate> out) {
        AbstractClientPlayer player=context.player();
        State state=STATES.seen(player.getUUID(),context.now()).value;
        state.shown=false;state.held=false;state.request=-1;
        Vector3f wantedLean=new Vector3f();
        try {
            if (!Seated.seated(player) || player.isSleeping() || player.isInWaterOrBubble()) {
                state.seat=null;state.wheel=null;context.decide("off:seat");return;
            }
            UUID seat=player.getVehicle().getUUID();
            if (!seat.equals(state.seat)) {
                state.seat=seat;state.wheel=null;state.motion.away=state.motion.moving=-1;state.motion.progress=1;
                Arrays.fill(state.throttle,null);Arrays.fill(state.rim,null);
            }
            BlockHitResult hit=player==Minecraft.getInstance().player && Minecraft.getInstance().hitResult instanceof BlockHitResult b
                    ? b : player.pick(3,1,false) instanceof BlockHitResult b ? b : null;
            if (hit!=null && WHEEL.matches(player.level().getBlockState(hit.getBlockPos()))
                    && !hit.getBlockPos().equals(state.wheel)) {
                state.wheel=hit.getBlockPos().immutable();Arrays.fill(state.rim,null);
                state.motion.away=state.motion.moving=-1;state.motion.progress=1;
                Vec3 forward=SubLevels.toWorld(player.level(),state.wheel,
                        WHEEL.swayCentre(player.level(),state.wheel,player.level().getBlockState(state.wheel))).subtract(player.position());
                state.right=SubLevels.at(player.level(),state.wheel).directionToLocal(new Vec3(-forward.z,0,forward.x).normalize());
            }
            if (state.wheel==null || !WHEEL.matches(player.level().getBlockState(state.wheel))) {
                state.wheel=null;context.decide("none:wheel");return;
            }
            var mount=player.level().getBlockState(state.wheel);
            Vec3 centre=SubLevels.toWorld(player.level(),state.wheel,WHEEL.swayCentre(player.level(),state.wheel,mount));
            if (centre.distanceTo(player.getEyePosition())>2.25 || !Visibility.visible(player,state.wheel,centre)) {
                state.wheel=null;context.decide("off:wheel-range");return;
            }
            BlockPos requested=player==Minecraft.getInstance().player ? ThrottleLever.heldPosition() : null;
            if (requested==null && hit!=null && ThrottleLever.is(player.level().getBlockState(hit.getBlockPos()))) requested=hit.getBlockPos();
            state.held=requested!=null;
            Vec3 knob=requested==null ? null : ThrottleLever.knob(player.level(),requested);
            if (knob!=null) knob=SubLevels.toWorld(player.level(),requested,knob);
            int request=-1;
            if (knob!=null && knob.distanceTo(centre)<2.5 && (state.held || Visibility.visible(player,requested,knob))) {
                Vector3f model=context.frame().relativeToJoint(knob,new Vector3f());
                request=knob.subtract(player.position()).dot(SubLevels.at(player.level(),state.wheel).directionToWorld(state.right))>=0 ? 0 : 1;
                if (new Vector3f(model).sub(SHOULDERS[request]).length()>(state.held ? 24 : 22)) request=-1;
                if (request>=0 && (state.motion.working()<0 || state.motion.working()!=request
                        || state.motion.moving<0)) {
                    if (state.motion.mix(request)==0) state.lever[request].set(model);
                    state.throttle[request]=requested.immutable();
                }
            }
            state.request=request;
            // Freeze the remaining rim contact during a handover. The stored point is in the
            // wheel's block space, so it still follows a moving craft rather than the world.
            if ((state.motion.working()<0 && request<0) || state.rim[0]==null || state.rim[1]==null) {
                boolean rightMain=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT;
                state.rim[rightMain?0:1]=WHEEL.hover(player,state.wheel,mount,hit);
                state.rim[rightMain?1:0]=WHEEL.supportHand(player,state.wheel,mount);
            }
            if (state.rim[0]==null || state.rim[1]==null) {context.decide("none:rim");return;}
            state.motion.advance(request,(float)context.dt());
            Map<Effector,float[]> aims=new EnumMap<>(Effector.class);
            for (int hand=0;hand<2;hand++) {
                Vec3 rim=SubLevels.toWorld(player.level(),state.wheel,state.rim[hand].point());
                Vector3f target=context.frame().relativeToJoint(rim,new Vector3f());
                float mix=state.motion.mix(hand);
                if (mix>0 && state.throttle[hand]!=null) {
                    Vec3 local=ThrottleLever.knob(player.level(),state.throttle[hand]);
                    if (local!=null) {
                        Vector3f lever=context.frame().relativeToJoint(SubLevels.toWorld(player.level(),state.throttle[hand],local),new Vector3f());
                        state.lever[hand].lerp(lever,Smoothing.follow(context.dt(),.06));
                    }
                    target.lerp(state.lever[hand],mix);
                    wantedLean.y+=(hand==0 ? 1 : -1)*(float)Math.toRadians(10)*mix;
                    wantedLean.z+=(hand==0 ? -1 : 1)*(float)Math.toRadians(3)*mix;
                }
                target.y-=state.motion.lift(hand);
                state.grips[hand].set(target);
                Vec3 point=context.frame().jointWorld(target);
                var aim=OneBoneIK.solveXY(context.frame(),SHOULDERS[hand],point,11,0,0);
                if (aim==null || aim.reach()>2.25f) {context.decide("off:reach");return;}
                aims.put(hand==0?Effector.RIGHT_ARM:Effector.LEFT_ARM,new float[]{aim.x(),aim.y()});
            }
            if (state.motion.working()<0) wantedLean.z=WheelGeometry.steeringRoll(state.grips[0].y,state.grips[1].y);
            out.add(Candidate.of(id(),Category.USE,14,1,TIMING,aims));
            context.claimArms();state.shown=true;
            context.decide(request<0 ? "wheel" : request==0 ? "throttle-R" : "throttle-L");
        } finally {
            state.lean.lerp(wantedLean,Smoothing.follow(context.dt(),.18));
            if (strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature.isTrace()
                    && context.now()-state.traceAt>100_000_000L) {
                state.traceAt=context.now();
                org.slf4j.LoggerFactory.getLogger("EMFCompatCockpit").info(
                        "[CockpitTrace] seated={} shown={} moving={} away={} returning={} progress={} rightMix={} leftMix={} rightWeight={} leftWeight={} throttleHeld={} request={}",
                        Seated.seated(player),state.shown,state.motion.moving,state.motion.away,state.motion.returning,
                        state.motion.progress,state.motion.mix(0),state.motion.mix(1),
                        InteractionRuntime.weight(player.getUUID(),Effector.RIGHT_ARM,id()),
                        InteractionRuntime.weight(player.getUUID(),Effector.LEFT_ARM,id()),state.held,state.request);
            }
        }
    }

    /** Bounded turn above the seat, then aim from the actual pack shoulders. */
    public static void apply(UUID uuid,Function<String,ModelPart> parts) {
        State state=STATES.fresh(uuid);
        if (state==null || !EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        float weight=Math.min(InteractionRuntime.weight(uuid,Effector.RIGHT_ARM,INSTANCE.id()),
                InteractionRuntime.weight(uuid,Effector.LEFT_ARM,INSTANCE.id()));
        if (weight<1e-3f) return;
        ModelPart r=parts.apply("right_leg"),l=parts.apply("left_leg");
        if (r==null || l==null) return;
        Vector3f waist=new Vector3f((r.x+l.x)*.5f,(r.y+l.y)*.5f,(r.z+l.z)*.5f);
        Quaternionf turn=new Quaternionf().rotationZYX(state.lean.z*weight,state.lean.y*weight,state.lean.x*weight);
        for (String name:new String[]{"body","head","hat","right_arm","left_arm"}) {
            ModelPart part=parts.apply(name);if (part==null) continue;
            Vector3f pos=turn.transform(new Vector3f(part.x,part.y,part.z).sub(waist)).add(waist);
            part.setPos(pos.x,pos.y,pos.z);
            if (!name.equals("head") && !name.equals("hat")) {
                part.yRot+=state.lean.y*weight;part.zRot+=state.lean.z*weight;
            }
        }
        for(int hand=0;hand<2;hand++) {
            ModelPart arm=parts.apply(hand==0?"right_arm":"left_arm");if(arm==null) continue;
            Vector3f to=new Vector3f(state.grips[hand]).sub(arm.x,arm.y,arm.z).normalize();
            float pitch=-(float)Math.acos(Math.max(-1,Math.min(1,to.y)));
            float yaw=(float)Math.atan2(-to.x,-to.z);
            arm.xRot+=IKMath.wrap(pitch-arm.xRot)*weight;
            arm.yRot+=IKMath.wrap(yaw-arm.yRot)*weight;
        }
    }
}
