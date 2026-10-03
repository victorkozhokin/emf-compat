package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.torso.PelvisFollow;
import traben.entity_model_features.models.animation.state.EMFState;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;

/** Shared supported upper-body pose, independent of any table mod or item gesture. */
final class TableSupport {
    static final class State {
        final TableSupportMotion motion=new TableSupportMotion();
        final Quaternionf turn=new Quaternionf();
        float frame=Float.NaN;
        long at,fitAt;
        float fitFrame=Float.NaN;
        float supportGap=Float.POSITIVE_INFINITY;
        final Map<String,Object> snapshot=new LinkedHashMap<>();
    }
    static void apply(State s,Function<String,ModelPart> parts,AbstractClientPlayer player,
                      boolean engaged,boolean right,Vector3f main,Vector3f support,float owned) {
        apply(s,parts,player,engaged,right,main,support,owned,0);
    }
    static void apply(State s,Function<String,ModelPart> parts,AbstractClientPlayer player,
                      boolean engaged,boolean right,Vector3f main,Vector3f support,float owned,float press) {
        var r=parts.apply("right_arm");var l=parts.apply("left_arm");
        var rl=parts.apply("right_leg");var ll=parts.apply("left_leg");
        if(r==null || l==null || rl==null || ll==null)return;
        boolean safe=player!=null && player.onGround() && !player.isPassenger()
                && player.getDeltaMovement().horizontalDistanceSqr()<.0004
                && (press>0 || support.y>(right?l:r).y+1);
        float frame=EMFState.getFrameCounter();
        if(s.frame!=frame) {
            long now=System.nanoTime();float dt=s.at==0?0:(float)Math.min(.1,(now-s.at)*1e-9);
            s.at=now;s.frame=frame;
            s.motion.advance(dt,engaged && safe,owned>.98f && s.supportGap<.13f);
            if(!safe)s.motion.load=0;
        }
        if(owned<.001f){s.turn.identity();s.snapshot.clear();return;}
        // Tiny grounded transfer; the existing leg solver preserves both already grounded soles.
        Vector3f rBefore=sole(rl),lBefore=sole(ll);
        PelvisFollow.shift(parts,0,(.9f-.65f*press)*s.motion.load*owned);
        float soleDrift=Math.max(rBefore.distance(sole(rl)),lBefore.distance(sole(ll)));
        Vector3f waist=new Vector3f((rl.x+ll.x)*.5f,(rl.y+ll.y)*.5f,(rl.z+ll.z)*.5f);
        Vector3f rt=right?main:support,lt=right?support:main;
        var desired=CockpitContact.fit(new Vector3f(r.x,r.y,r.z).sub(waist),new Vector3f(l.x,l.y,l.z).sub(waist),
                new Vector3f(rt).sub(waist),new Vector3f(lt).sub(waist));
        // Keep the correction synchronized across the main model and its clothing passes.
        if(s.fitFrame!=frame) {
            double dt=s.fitAt==0?0:Math.min(.1,(System.nanoTime()-s.fitAt)*1e-9);
            s.turn.set(CockpitContact.follow(s.turn,desired,Smoothing.follow(dt,.12),
                new Vector3f(r.x,r.y,r.z).sub(waist),new Vector3f(l.x,l.y,l.z).sub(waist),
                new Vector3f(rt).sub(waist),new Vector3f(lt).sub(waist)));
            s.fitFrame=frame;s.fitAt=System.nanoTime();
        }
        var q=new Quaternionf().slerp(s.turn,owned);
        for(String name:new String[]{"body","head","hat","right_arm","left_arm"}) {
            var p=parts.apply(name);if(p==null)continue;
            var at=q.transform(new Vector3f(p.x,p.y,p.z).sub(waist)).add(waist);p.setPos(at.x,at.y,at.z);
            if(!name.equals("head") && !name.equals("hat")) {
                var angles=new Quaternionf(q).mul(new Quaternionf().rotationZYX(p.zRot,p.yRot,p.xRot)).getEulerAnglesZYX(new Vector3f());
                p.setRotation(angles.x,angles.y,angles.z);
            }
        }
        s.snapshot.put("maxSoleDriftPixels",soleDrift);s.snapshot.put("mainRight",right);
        s.snapshot.put("engaged",engaged);s.snapshot.put("load",s.motion.load);s.snapshot.put("owned",owned);
        s.snapshot.put("leanDegrees",Math.toDegrees(s.turn.angle()));
        s.snapshot.put("rightTarget",new float[]{rt.x,rt.y,rt.z});s.snapshot.put("leftTarget",new float[]{lt.x,lt.y,lt.z});
    }
    private static Vector3f sole(ModelPart p) {
        return new Quaternionf().rotationZYX(p.zRot,p.yRot,p.xRot).transform(new Vector3f(0,12*p.yScale,0)).add(p.x,p.y,p.z);
    }
    static void capture(State s,Function<String,ModelPart> parts,boolean right,Vector3f main,Vector3f support) {
        if(s.snapshot.isEmpty())return;
        for(boolean hand:new boolean[]{true,false}) {
            var p=parts.apply(hand?"right_arm":"left_arm");if(p==null)continue;
            var target=hand==right?main:support;
            var palm=new Quaternionf().rotationZYX(p.zRot,p.yRot,p.xRot).transform(new Vector3f(0,11*p.yScale,0)).add(p.x,p.y,p.z);
            float gap=palm.distance(target)/16;
            s.snapshot.put(hand?"rightGap":"leftGap",gap);
            if(hand!=right)s.supportGap=gap;
        }
    }
}
