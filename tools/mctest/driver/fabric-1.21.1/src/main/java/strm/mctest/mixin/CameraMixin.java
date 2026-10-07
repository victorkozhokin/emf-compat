package strm.mctest.mixin;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.mctest.Driver;

/**
 * The {@code orbit} step: a third-person camera at any angle around the player, so a lean or a
 * side-on pose can be shot while the player keeps moving the way it looks.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void move(float zoom, float dy, float dx);

    @Shadow protected abstract float getMaxZoom(float maxZoom);

    @Inject(method = "setup", at = @At("TAIL"))
    private void mctest$orbit(BlockGetter level, Entity entity, boolean detached, boolean mirror,
                              float partialTick, CallbackInfo ci) {
        float[] orbit = Driver.orbit();
        if (orbit == null || !detached || entity == null) {
            return;
        }
        if (orbit.length >= 6) {
            setRotation(orbit[0], orbit[1]);
            setPosition(orbit[3], orbit[4], orbit[5]);
            move(-getMaxZoom(orbit[2]), 0f, 0f);
            return;
        }
        // Relative to where the entity faces, so "90" stays side-on while it runs and turns.
        setRotation(entity.getViewYRot(partialTick) + orbit[0], orbit[1]);
        setPosition(Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()) + Mth.lerp(partialTick, eyeHeightOld, eyeHeight),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
        move(-getMaxZoom(orbit[2]), 0f, 0f);
    }
}
