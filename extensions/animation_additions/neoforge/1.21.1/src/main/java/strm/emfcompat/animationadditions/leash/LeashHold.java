package strm.emfcompat.animationadditions.leash;

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

/** An actual player-held leash owns one hand; other interactions can take that hand normally. */
public final class LeashHold implements InteractionProvider {
    public static final LeashHold INSTANCE = new LeashHold();
    public static final String KEY_ENABLED = "leash.enabled";
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    private static final Candidate.Timing TIMING = new Candidate.Timing(.16,.2,.08);
    private static final class State {
        final LeashMotion motion = new LeashMotion();
        final Vector3f palm = new Vector3f(), direction = new Vector3f();
        UUID animal, attachingAnimal;
        Effector attachingHand;
        Effector hand = Effector.RIGHT_ARM;
        boolean active, grounded, hasPalm;
        int count;
        double distance;
        IKFrame frame;
        Vec3 playerPosition, drawnPalm;
        Vec3 relative, rawRelative;
        long settleUntil;
        double speed;
        long traceAt;
    }
    public String id() { return "LeashHold"; }
    public boolean isEnabled() { return EMFCompatConfig.getBoolean(KEY_ENABLED,true); }
    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED,"Hold animal leads",true,
                "On","Hold the real leash in one hand and brace against tension and outward jerks.",
                "Off","Leave the lead and hand pose to Minecraft.");
    }

    public static void attachHand(AbstractClientPlayer player,Entity target,net.minecraft.world.InteractionHand hand) {
        if(!player.getItemInHand(hand).is(Items.LEAD) || !(target instanceof Leashable leash)
                || leash.getLeashHolder()==player) return;
        State s=STATES.seen(player.getUUID(),System.nanoTime()).value;
        s.attachingAnimal=target.getUUID();
        boolean right=(player.getMainArm()==HumanoidArm.RIGHT)==(hand==net.minecraft.world.InteractionHand.MAIN_HAND);
        s.attachingHand=right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
    }

    /** Same animal attachment transform as the vanilla leash renderer. */
    static Vec3 anchor(Entity animal,float partial) {
        Vec3 offset=animal.getLeashOffset(partial);
        float yaw=animal instanceof LivingEntity living
                ? Mth.rotLerp(partial,living.yBodyRotO,living.yBodyRot) : animal.getYRot();
        double angle=Math.toRadians(yaw)+Math.PI/2;
        return animal.getPosition(partial).add(Math.cos(angle)*offset.z+Math.sin(angle)*offset.x,
                offset.y,Math.sin(angle)*offset.z-Math.cos(angle)*offset.x);
    }

    public void collect(InteractionContext context,List<Candidate> out) {
        var player=context.player();State s=STATES.seen(player.getUUID(),context.now()).value;
        s.active=false;s.count=0;
        if (player.isSleeping() || player.isFallFlying() || player.isInWaterOrBubble() || player.isAutoSpinAttack()) {
            context.decide("off:pose");return;
        }
        Entity chosen=null;double farthest=-1;
        for (Entity e:player.level().getEntities(player,player.getBoundingBox().inflate(12),
                e->e instanceof Leashable leash && leash.getLeashHolder()==player && e.isAlive())) {
            s.count++;double distance=e.distanceTo(player);
            // Keep the previous animal through small distance changes instead of flickering between leads.
            double score=distance+(e.getUUID().equals(s.animal) ? .35 : 0);
            if(score>farthest) {farthest=score;chosen=e;}
        }
        if(chosen==null) {s.animal=null;s.hasPalm=false;context.decide("none");return;}
        boolean fresh=s.animal==null || !chosen.getUUID().equals(s.animal);
        if(s.animal==null) {
            boolean mainRight=player.getMainArm()==HumanoidArm.RIGHT;
            boolean off=player.getOffhandItem().is(Items.LEAD) && !player.getMainHandItem().is(Items.LEAD);
            s.hand=chosen.getUUID().equals(s.attachingAnimal) && s.attachingHand!=null ? s.attachingHand
                    : mainRight!=off ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
        }
        s.animal=chosen.getUUID();s.frame=context.frame();
        s.distance=chosen.distanceTo(player);
        float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        s.playerPosition=player.getPosition(partial);
        Vec3 relative=chosen.getPosition(partial).subtract(s.playerPosition);
        Vec3 raw=chosen.position().subtract(player.position());
        double radial=relative.lengthSqr()<1e-6 ? 0 : chosen.getDeltaMovement()
                .subtract(player.getDeltaMovement()).dot(relative.normalize())*20;
        // Interpolated relative motion also works when a mob's network velocity is stale.
        boolean warped=s.rawRelative!=null && raw.distanceTo(s.rawRelative)>2;
        s.rawRelative=raw;
        if(warped) s.settleUntil=context.now()+350_000_000L;
        boolean settling=context.now()<s.settleUntil;
        if (!fresh && !settling && s.relative!=null && context.dt()>1e-4) {
            double observed=relative.subtract(s.relative).dot(relative.normalize())/context.dt();
            s.speed+=(observed-s.speed)*Smoothing.follow(context.dt(),.08);
            radial=Math.max(radial,s.speed);
        } else s.speed=0;
        s.relative=relative;
        s.motion.advance(s.distance,radial,context.dt(),fresh || settling);
        Vector3f shoulder=new Vector3f(s.hand==Effector.RIGHT_ARM ? -5 : 5,2,0);
        Vector3f direction=context.frame().relativeToJoint(anchor(chosen,partial),shoulder);
        if(direction.lengthSquared()<1e-6) direction.set(0,0,-1);else direction.normalize();
        s.direction.set(direction);
        float amount=Math.min(1,s.motion.load+s.motion.jerk*.3f);
        // The slack grip rests forward of its own hip; loading lifts and extends it.
        Vector3f relaxed=new Vector3f(0,8,-3);
        Vector3f loaded=new Vector3f(direction).mul(8);
        loaded.y-=s.motion.jerk*1.5f;loaded.z+=s.motion.jerk*.8f;
        // An animal behind the player must not pull the arm through the torso.
        loaded.x=s.hand==Effector.RIGHT_ARM ? Math.min(0,loaded.x) : Math.max(0,loaded.x);
        loaded.z=Math.min(-2,loaded.z);
        Vector3f wantedPalm=new Vector3f(relaxed).lerp(loaded,amount).add(shoulder);
        if(!s.hasPalm) {s.palm.set(wantedPalm);s.hasPalm=true;}
        else s.palm.lerp(wantedPalm,Smoothing.follow(context.dt(),.1));
        Vec3 target=context.frame().jointWorld(s.palm);
        var aim=OneBoneIK.solveXY(context.frame(),shoulder,target,11,0,0);
        if(aim==null) {context.decide("off:aim");return;}
        out.add(Candidate.single(id(),Category.PASSIVE,30,1,TIMING,s.hand,new float[]{aim.x(),aim.y()}));
        HandContacts.remember(context,id(),s.hand,target);
        s.grounded=player.onGround() && !player.isPassenger();s.active=true;
        context.decide(s.motion.jerk>.15 ? "jerk" : s.motion.load>.1 ? "tension" : "slack");
    }

    public static float[] torsoHint(UUID uuid) {
        State s=STATES.fresh(uuid);
        if(s==null || !s.active || !s.grounded || !INSTANCE.isEnabled()) return null;
        float w=InteractionRuntime.weight(uuid,s.hand,INSTANCE.id());
        float effort=s.motion.load*(1+s.motion.jerk*.3f)*w;
        return new float[]{s.direction.z*(float)Math.toRadians(5)*effort,0,
                s.direction.x*(float)Math.toRadians(6)*effort};
    }

    /** Capture the final animated palm, after every torso and contact correction. */
    public static void capture(UUID uuid,Function<String,ModelPart> parts) {
        State s=STATES.fresh(uuid);
        if(s==null || !s.active || s.frame==null) return;
        ModelPart arm=parts.apply(s.hand.part);if(arm==null) return;
        float owned=InteractionRuntime.weight(uuid,s.hand,INSTANCE.id());
        arm.zRot*=1-owned;
        Vector3f palm=new Quaternionf().rotationZYX(arm.zRot,arm.yRot,arm.xRot)
                .transform(new Vector3f(0,11*arm.yScale,0)).add(arm.x,arm.y,arm.z);
        s.drawnPalm=s.frame.jointWorld(palm);
        long now=System.nanoTime();
        if(strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature.isTrace() && now-s.traceAt>100_000_000L) {
            s.traceAt=now;
            org.slf4j.LoggerFactory.getLogger("EMFCompatLeash").info(
                    "[LeashTrace] count={} distance={} load={} jerk={} right={} weight={} palmX={} palmY={} palmZ={}",
                    s.count,s.distance,s.motion.load,s.motion.jerk,s.hand==Effector.RIGHT_ARM,
                    InteractionRuntime.weight(uuid,s.hand,INSTANCE.id()),s.drawnPalm.x,s.drawnPalm.y,s.drawnPalm.z);
        }
    }

    /** Only the rendered endpoint changes: attaching, breaking and forces stay vanilla. */
    public static Vec3 rope(AbstractClientPlayer player,float partial,Vec3 vanilla) {
        State s=STATES.fresh(player.getUUID());
        if(s==null || !s.active || s.drawnPalm==null || !INSTANCE.isEnabled()
                || !EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return vanilla;
        float w=InteractionRuntime.weight(player.getUUID(),s.hand,INSTANCE.id());
        if(w<1e-3) return vanilla;
        Vec3 corrected=s.drawnPalm.add(player.getPosition(partial).subtract(s.playerPosition));
        return vanilla.lerp(corrected,w);
    }
}
