package strm.emfcompat.animationadditions.interaction;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKMath;
import strm.emfcompat.core.EMFCompatCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Contact targets reapplied from the drawn shoulder after breathing and torso layers. */
public final class HandContacts {
    private record Key(String source, Effector hand) {}
    private record Anchor(SubLevels.Space space, Vec3 local) {
        Vec3 world() {return space.valid()?space.refresh().toWorld(local):null;}
    }
    private static final EntityStates<Map<Key, Anchor>> STATES = new EntityStates<>(HashMap::new);
    private HandContacts() {}

    public static void remember(InteractionContext context, String source, Effector hand, Vec3 world) {
        remember(context,source,hand,world,SubLevels.WORLD);
    }

    public static void remember(InteractionContext context,String source,Effector hand,Vec3 world,SubLevels.Space space) {
        STATES.seen(context.player().getUUID(), context.now()).value.put(new Key(source,hand),new Anchor(space,space.toLocal(world)));
    }

    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        Map<Key, Anchor> targets = STATES.fresh(uuid);
        if (targets == null) return;
        var frame=InteractionRuntime.frame(uuid);
        if(frame==null)return;
        targets.forEach((key, anchor) -> {
            Vec3 world=anchor.world();
            if(world==null)return;
            Vector3f point=frame.relativeToJoint(world,new Vector3f());
            float w = InteractionRuntime.weight(uuid, key.hand, key.source);
            ModelPart arm = parts.apply(key.hand.part);
            if (w < 1e-3f || arm == null) return;
            Vector3f to = new Vector3f(point).sub(arm.x, arm.y, arm.z);
            if (to.lengthSquared() < 1e-6f) return;
            to.normalize();
            float pitch = -(float) Math.acos(Math.max(-1, Math.min(1, to.y)));
            float yaw = (float) Math.atan2(-to.x, -to.z);
            arm.xRot += IKMath.wrap(pitch - arm.xRot) * w;
            arm.yRot += IKMath.wrap(yaw - arm.yRot) * w;
        });
    }
}
