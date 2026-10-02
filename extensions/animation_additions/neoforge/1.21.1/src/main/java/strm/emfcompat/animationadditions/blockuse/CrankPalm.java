package strm.emfcompat.animationadditions.blockuse;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Contact at the drawn hand, including the FA+Player child mesh offset. */
final class CrankPalm {
    static Vector3f local(ModelPart arm) {
        PoseStack root=new PoseStack();arm.translateAndRotate(root);
        Matrix4f inverse=new Matrix4f(root.last().pose()).invert();
        Vector3f[] best={new Vector3f(0,11,0)};float[] size={0};
        arm.visit(new PoseStack(),(pose,path,index,cube)->{
            Matrix4f m=new Matrix4f(inverse).mul(pose.pose());
            Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);
            for(float x:new float[]{cube.minX,cube.maxX})for(float y:new float[]{cube.minY,cube.maxY})for(float z:new float[]{cube.minZ,cube.maxZ}) {
                Vector3f p=m.transformPosition(new Vector3f(x,y,z).div(16)).mul(16);lo.min(p);hi.max(p);
            }
            if(hi.y-lo.y>size[0] && hi.y-lo.y>=6) {size[0]=hi.y-lo.y;best[0].set((lo.x+hi.x)*.5f,hi.y-.25f,(lo.z+hi.z)*.5f);}
        });
        return best[0].mul(arm.xScale,arm.yScale,arm.zScale);
    }

    static Vector3f point(ModelPart arm,Vector3f local) {
        return new Quaternionf().rotationZYX(arm.zRot,arm.yRot,arm.xRot).transform(new Vector3f(local)).add(arm.x,arm.y,arm.z);
    }

    static void aim(ModelPart arm,Vector3f palm,Vector3f target,float weight) {
        Vector3f to=new Vector3f(target).sub(arm.x,arm.y,arm.z);
        if(to.lengthSquared()<1e-6f || palm.lengthSquared()<1e-6f)return;
        Quaternionf wanted=CrankPalmMath.rotation(palm,to);
        Quaternionf q=new Quaternionf().rotationZYX(arm.zRot,arm.yRot,arm.xRot).slerp(wanted,weight).normalize();
        arm.setRotation((float)Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y)),
                (float)Math.asin(Math.max(-1,Math.min(1,2*(q.w*q.y-q.z*q.x)))),
                (float)Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z)));
    }
}
