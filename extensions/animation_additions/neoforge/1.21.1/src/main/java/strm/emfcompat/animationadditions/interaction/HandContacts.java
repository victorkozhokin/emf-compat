package strm.emfcompat.animationadditions.interaction;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.core.EMFCompatCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Contact targets reapplied from the drawn shoulder after breathing and torso layers. */
public final class HandContacts {
    private record Key(String source, Effector hand) {}
    private static final EntityStates<Map<Key, Vector3f>> STATES = new EntityStates<>(HashMap::new);
    private HandContacts() {}

    public static void remember(InteractionContext context, String source, Effector hand, Vec3 world) {
        STATES.seen(context.player().getUUID(), context.now()).value.put(new Key(source, hand),
                context.frame().relativeToJoint(world, new Vector3f()));
    }

    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        Map<Key, Vector3f> targets = STATES.fresh(uuid);
        if (targets == null) return;
        targets.forEach((key, point) -> {
            float w = InteractionRuntime.weight(uuid, key.hand, key.source);
            ModelPart arm = parts.apply(key.hand.part);
            if (w < 1e-3f || arm == null) return;
            ArmAim.towards(arm, point, w, false);
        });
    }
}
