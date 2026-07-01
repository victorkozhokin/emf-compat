package strm.emfcompat.immersivemelodies.compat;

import immersive_melodies.client.animation.EntityModelAnimator;
import net.minecraft.world.entity.LivingEntity;

public final class ImmersiveMelodiesCompat {

    private ImmersiveMelodiesCompat() {
    }

    public static boolean hasInstrument(LivingEntity entity) {
        return EntityModelAnimator.getInstrument(entity) != null;
    }
}
