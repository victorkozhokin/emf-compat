package strm.emfcompat.animationadditions.blockuse;
import org.joml.Vector3f;

/** Conservative head volume clearance, including a half-pixel clothing margin. */
final class HeadClearance {
    static float shift(Vector3f min,Vector3f max,Vector3f blockMin,Vector3f blockMax) {
        if(Math.min(max.x,blockMax.x)<=Math.max(min.x,blockMin.x)
                || Math.min(max.y,blockMax.y)<=Math.max(min.y,blockMin.y)
                || min.z>=blockMax.z+.5f || max.z<=blockMin.z-.5f)return 0;
        return min.z+max.z>blockMin.z+blockMax.z?blockMax.z+.5f-min.z:blockMin.z-.5f-max.z;
    }
    static float overlap(Vector3f min,Vector3f max,Vector3f blockMin,Vector3f blockMax) {
        return Math.max(0,Math.min(Math.min(max.x,blockMax.x)-Math.max(min.x,blockMin.x),
                Math.min(Math.min(max.y,blockMax.y)-Math.max(min.y,blockMin.y),Math.min(max.z,blockMax.z)-Math.max(min.z,blockMin.z))));
    }
}
