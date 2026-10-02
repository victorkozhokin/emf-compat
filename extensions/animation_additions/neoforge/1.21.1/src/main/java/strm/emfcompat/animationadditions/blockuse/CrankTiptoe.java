package strm.emfcompat.animationadditions.blockuse;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import traben.entity_model_features.models.animation.state.EMFState;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
/** Bounded high-control extension; scales are restored before the next pack animation. */
public final class CrankTiptoe {
    private static final Map<ModelPart,Float> BASE=new WeakHashMap<>();
    private static final String[] NAMES={"body","right_leg","left_leg","jacket","right_pants","left_pants"};
    static final class State {float weight,frame=-1;long at,tracedAt;}
    public static void restore(Function<String,ModelPart> parts) {
        for(String n:NAMES){ModelPart p=parts.apply(n);if(p!=null){Float y=BASE.remove(p);if(y!=null)p.yScale=y;}}
    }
    public static void rememberOuter(ModelPart limb,ModelPart outer){if(BASE.containsKey(limb))BASE.put(outer,outer.yScale);}
    static void apply(Function<String,ModelPart> parts,float wanted,State state) {
        float frame=EMFState.getFrameCounter();
        if(frame!=state.frame){long now=System.nanoTime();double dt=state.at==0?0:Math.min(.1,(now-state.at)*1e-9);state.at=now;state.frame=frame;
            state.weight+=(wanted-state.weight)*Smoothing.follow(dt,wanted>state.weight?.18:.24);}
        float w=state.weight;if(w<1e-3f){trace(parts,state);return;}
        ModelPart body=parts.apply("body"),r=parts.apply("right_leg"),l=parts.apply("left_leg");if(body==null||r==null||l==null)return;
        Vector3f hips=new Vector3f((r.x+l.x)*.5f,(r.y+l.y)*.5f,(r.z+l.z)*.5f);
        for(ModelPart leg:new ModelPart[]{r,l}){
            Quaternionf old=new Quaternionf().rotationZYX(leg.zRot,leg.yRot,leg.xRot),toe=new Quaternionf(old).rotateX(.10f*w);
            Vector3f hip=CrankTiptoeMath.hip(new Vector3f(leg.x,leg.y,leg.z),old,toe,12*leg.yScale,1+.12f*w);
            BASE.put(leg,leg.yScale);leg.yScale*=1+.12f*w;leg.setPos(hip.x,hip.y,hip.z);
            leg.xRot+=.10f*w;
        }
        Vector3f delta=new Vector3f((r.x+l.x)*.5f,(r.y+l.y)*.5f,(r.z+l.z)*.5f).sub(hips);
        Quaternionf rotation=new Quaternionf().rotationZYX(body.zRot,body.yRot,body.xRot);
        for(String name:new String[]{"body","head","hat","right_arm","left_arm"}){
            ModelPart p=parts.apply(name);if(p==null)continue;
            Vector3f pos=CrankTiptoeMath.stretch(new Vector3f(p.x,p.y,p.z),hips,rotation,1+.10f*w).add(delta);
            p.setPos(pos.x,pos.y,pos.z);
        }
        BASE.put(body,body.yScale);body.yScale*=1+.10f*w;
        trace(parts,state);
    }
    private static void trace(Function<String,ModelPart> parts,State state) {
        long now=System.nanoTime();
        if(!strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature.isTrace() || now-state.tracedAt<100_000_000L)return;
        ModelPart body=parts.apply("body"),r=parts.apply("right_leg"),l=parts.apply("left_leg");
        if(body==null||r==null||l==null)return;
        state.tracedAt=now;
        org.slf4j.LoggerFactory.getLogger("EMFCompatBlockUse").info(
                "[TiptoeTrace] weight={} bodyScale={} rightScale={} leftScale={}",state.weight,body.yScale,r.yScale,l.yScale);
    }
}
