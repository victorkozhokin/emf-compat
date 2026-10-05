package strm.emfcompat.animationadditions.torso;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Move the hips with obstacle clearance while retaining the already solved soles. */
public final class PelvisFollow {
    record Leg(Vector3f pivot, float pitch, float yaw, float roll) {}
    record Part(Vector3f pivot, float pitch, float yaw, float roll) {}

    static Part carry(Vector3f pivot, float pitch, float yaw, float roll,
                      Quaternionf turn, Vector3f waist, float shift) {
        Vector3f moved = new Quaternionf(turn).transform(new Vector3f(pivot).sub(waist)).add(waist);
        moved.x += shift;
        Quaternionf rotation = new Quaternionf(turn).mul(new Quaternionf().rotationZYX(roll, yaw, pitch)).normalize();
        Vector3f angles = angles(rotation);
        return new Part(moved, angles.x, angles.y, angles.z);
    }

    /** Carry the neck attachment without changing the pack's gaze or an authored head pose. */
    static Part carryGaze(Vector3f pivot, float pitch, float yaw, float roll,
                          Quaternionf turn, Vector3f waist, float shift) {
        Vector3f moved = new Quaternionf(turn).transform(new Vector3f(pivot).sub(waist)).add(waist);
        moved.x += shift;
        return new Part(moved, pitch, yaw, roll);
    }

    private static Vector3f angles(Quaternionf q) {
        return new Vector3f(
                (float)Math.atan2(2 * (q.w * q.x + q.y * q.z), 1 - 2 * (q.x * q.x + q.y * q.y)),
                (float)Math.asin(Math.max(-1, Math.min(1, 2 * (q.w * q.y - q.z * q.x)))),
                (float)Math.atan2(2 * (q.w * q.z + q.x * q.y), 1 - 2 * (q.y * q.y + q.z * q.z)));
    }

    static Vector3f waist(Vector3f pivot, float pitch, float yaw, float roll, float length) {
        return new Quaternionf().rotationZYX(roll, yaw, pitch)
                .transform(new Vector3f(0, length, 0)).add(pivot);
    }

    static Leg leg(Vector3f pivot, float pitch, float yaw, float roll, float length,
                   Vector3f waist, float turn, float shift) {
        if (length < 1e-4f || Math.abs(turn) + Math.abs(shift) < 1e-5f)
            return new Leg(new Vector3f(pivot), pitch, yaw, roll);
        Quaternionf original = new Quaternionf().rotationZYX(roll, yaw, pitch);
        Vector3f before = original.transform(new Vector3f(0, length, 0));
        Vector3f sole = new Vector3f(before).add(pivot);
        // A near-horizontal sprint leg has little vertical reach left. Reduce hip
        // movement instead of stretching it, lifting it abruptly or moving its sole.
        Leg full = trial(pivot, pitch, yaw, roll, length, waist, turn, shift, original, before, sole, 1, 0, .75f, 0);
        if (full != null) return full;
        float low = 0, high = 1;
        Leg result = new Leg(new Vector3f(pivot), pitch, yaw, roll);
        for (int i = 0; i < 12; i++) {
            float amount = (low + high) * 0.5f;
            Leg candidate = trial(pivot, pitch, yaw, roll, length, waist, turn, shift, original, before, sole, amount, 0, .75f, 0);
            if (candidate == null) high = amount;
            else { low = amount; result = candidate; }
        }
        return result;
    }

    static Leg translate(Vector3f pivot, float pitch, float yaw, float roll, float length, float dx, float dz) {
        if (length < 1e-4f || Math.abs(dx) + Math.abs(dz) < 1e-5f) return new Leg(new Vector3f(pivot), pitch, yaw, roll);
        Quaternionf original = new Quaternionf().rotationZYX(roll, yaw, pitch);
        Vector3f before = original.transform(new Vector3f(0, length, 0));
        Vector3f sole = new Vector3f(before).add(pivot);
        float low = 0, high = 1;
        Leg result = new Leg(new Vector3f(pivot), pitch, yaw, roll);
        Leg full = trial(pivot, pitch, yaw, roll, length, pivot, 0, dx, original, before, sole, 1, dz, 2, Math.min(.75f, Math.abs(before.y)));
        if (full != null) return full;
        for (int i = 0; i < 12; i++) {
            float amount = (low + high) * .5f;
            Leg candidate = trial(pivot, pitch, yaw, roll, length, pivot, 0, dx, original, before, sole, amount, dz, 2, Math.min(.75f, Math.abs(before.y)));
            if (candidate == null) high = amount;
            else { low = amount; result = candidate; }
        }
        return result;
    }

    /** Reach a new stepping sole without stretching the straight pack leg. */
    static Leg sole(Vector3f pivot, float pitch, float yaw, float roll, float length,
                    Vector3f offset, float twist) {
        if (length < 1e-4f) return new Leg(new Vector3f(pivot),pitch,yaw,roll);
        Quaternionf original = new Quaternionf().rotationZYX(roll, yaw, pitch);
        Vector3f before = original.transform(new Vector3f(0, length, 0));
        Vector3f direction = new Vector3f(before).add(offset);
        float yy = length * length - direction.x * direction.x - direction.z * direction.z;
        if (yy < 0) return new Leg(new Vector3f(pivot), pitch, yaw, roll);
        float y = Math.copySign((float)Math.sqrt(yy), before.y);
        Vector3f hip = new Vector3f(pivot).add(0, direction.y - y, 0);
        direction.y = y;
        Quaternionf q = new Quaternionf().rotationTo(before, direction).mul(original)
                .rotateY(twist).normalize();
        Vector3f angles = angles(q);
        return new Leg(hip, angles.x, angles.y, angles.z);
    }

    /** Small weight transfer with both solved soles retained; carry the torso by the achieved shift. */
    public static void shift(java.util.function.Function<String,net.minecraft.client.model.geom.ModelPart> parts,float dx,float dz) {
        var r=parts.apply("right_leg");var l=parts.apply("left_leg");if(r==null || l==null)return;
        var before=new Vector3f((r.x+l.x)*.5f,(r.y+l.y)*.5f,(r.z+l.z)*.5f);
        for(var p:new net.minecraft.client.model.geom.ModelPart[]{r,l}) {
            var moved=translate(new Vector3f(p.x,p.y,p.z),p.xRot,p.yRot,p.zRot,12*p.yScale,dx,dz);
            p.setPos(moved.pivot.x,moved.pivot.y,moved.pivot.z);p.setRotation(moved.pitch,moved.yaw,moved.roll);
        }
        var delta=new Vector3f((r.x+l.x)*.5f,(r.y+l.y)*.5f,(r.z+l.z)*.5f).sub(before);
        for(String name:new String[]{"body","head","hat","right_arm","left_arm"}) {
            var p=parts.apply(name);if(p!=null)p.setPos(p.x+delta.x,p.y+delta.y,p.z+delta.z);
        }
    }

    /** Stepping layer: carry the torso by the achieved mean hip displacement. */
    public static void step(java.util.function.Function<String, net.minecraft.client.model.geom.ModelPart> parts,
                            Vector3f right, Vector3f left, float rightTwist, float leftTwist) {
        var r = parts.apply("right_leg"); var l = parts.apply("left_leg");
        if (r == null || l == null) return;
        Vector3f before = new Vector3f((r.x+l.x)*.5f, (r.y+l.y)*.5f, (r.z+l.z)*.5f);
        for (var leg : new net.minecraft.client.model.geom.ModelPart[]{r,l}) {
            var moved = sole(new Vector3f(leg.x,leg.y,leg.z), leg.xRot,leg.yRot,leg.zRot,
                    12*leg.yScale, leg==r ? right : left, leg==r ? rightTwist : leftTwist);
            leg.setPos(moved.pivot.x, moved.pivot.y, moved.pivot.z);
            leg.setRotation(moved.pitch,moved.yaw,moved.roll);
        }
        Vector3f delta = new Vector3f((r.x+l.x)*.5f,(r.y+l.y)*.5f,(r.z+l.z)*.5f).sub(before);
        for (String name : new String[]{"body","head","hat","right_arm","left_arm"}) {
            var part = parts.apply(name);
            if (part != null) part.setPos(part.x+delta.x,part.y+delta.y,part.z+delta.z);
        }
    }

    private static Leg trial(Vector3f pivot, float pitch, float yaw, float roll, float length,
                             Vector3f waist, float turn, float shift, Quaternionf original,
                             Vector3f before, Vector3f sole, float amount, float shiftZ, float heightLimit, float minVertical) {
        Quaternionf rotation = new Quaternionf().rotationY(turn * amount);
        Vector3f hip = rotation.transform(new Vector3f(pivot).sub(waist)).add(waist);
        hip.x += shift * amount;
        hip.z += shiftZ * amount;
        float x = sole.x - hip.x, z = sole.z - hip.z;
        float yy = length * length - x * x - z * z;
        // Avoid a singular fully horizontal leg while shifting the low-reach pelvis.
        if (yy < minVertical * minVertical) return null;
        float y = Math.copySign((float)Math.sqrt(yy), before.y);
        hip.y = sole.y - y;
        if (Math.abs(hip.y - pivot.y) > heightLimit) return null;
        Quaternionf swung = rotation.mul(new Quaternionf(original));
        Vector3f direction = swung.transform(new Vector3f(0, length, 0));
        Quaternionf q = new Quaternionf().rotationTo(direction, new Vector3f(x, y, z))
                .mul(swung).normalize();
        float px = (float)Math.atan2(2 * (q.w * q.x + q.y * q.z), 1 - 2 * (q.x * q.x + q.y * q.y));
        float py = (float)Math.asin(Math.max(-1, Math.min(1, 2 * (q.w * q.y - q.z * q.x))));
        float pz = (float)Math.atan2(2 * (q.w * q.z + q.x * q.y), 1 - 2 * (q.y * q.y + q.z * q.z));
        return new Leg(hip, px, py, pz);
    }
}
