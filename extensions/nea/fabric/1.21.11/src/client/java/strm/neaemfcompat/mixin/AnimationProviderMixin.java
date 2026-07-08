package strm.neaemfcompat.mixin;

import dev.tr7zw.notenoughanimations.animations.hands.NarutoRunningAnimation;
import dev.tr7zw.notenoughanimations.logic.AnimationProvider;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.BurningAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.FreezingAnimation;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.ActiveParts;
import strm.emfcompat.core.PoseManager;
import strm.neaemfcompat.compat.EMFCompat;

@Mixin(AnimationProvider.class)
public class AnimationProviderMixin {

    @Unique
    private BasicAnimation[] neaemfcompat$animationArray;

    @Inject(method = "applyAnimations", at = @At("HEAD"))
    private void neaemfcompat$resetAnimationArray(AbstractClientPlayerEntity entity, PlayerEntityModel model, float delta, float swing, CallbackInfo ci) {
        this.neaemfcompat$animationArray = null;
    }

    @ModifyVariable(
            method = "applyAnimations",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private BasicAnimation[] neaemfcompat$captureAnimationArray(BasicAnimation[] animation) {
        this.neaemfcompat$animationArray = animation;
        return animation;
    }

    @Inject(method = "applyAnimations", at = @At("RETURN"))
    private void neaemfcompat$onApplyAnimationsReturn(AbstractClientPlayerEntity entity, PlayerEntityModel model, float delta, float swing, CallbackInfo ci) {

        BasicAnimation[] animation = this.neaemfcompat$animationArray;

        if (animation == null) return;
        boolean leftArm = EMFCompat.shouldPauseForAnimation(animation[BodyPart.LEFT_ARM.ordinal()]);
        boolean rightArm = EMFCompat.shouldPauseForAnimation(animation[BodyPart.RIGHT_ARM.ordinal()]);

        boolean leftLeg = false;
        boolean rightLeg = false;
        if (NEABaseMod.config.enableHorseLegAnimation) {
            Entity vehicle = entity.getVehicle();
            if (vehicle instanceof AbstractHorseEntity) {
                leftLeg = true;
                rightLeg = true;
            }
        }

        if (entity.isSprinting()) {
            boolean hasBurningOrFreezingOrNaruto = false;
            for (BasicAnimation anim : animation) {
                if (anim instanceof BurningAnimation || anim instanceof FreezingAnimation || anim instanceof NarutoRunningAnimation) {
                    hasBurningOrFreezingOrNaruto = true;
                    break;
                }
            }
            if (!hasBurningOrFreezingOrNaruto && !leftLeg && !rightLeg) {
                PoseManager.clearPoses(entity.getUuid());
                return;
            }
        }

        if (!leftArm && !rightArm && !leftLeg && !rightLeg) {
            PoseManager.clearPoses(entity.getUuid());
            return;
        }

        PoseManager.setActiveParts(entity.getUuid(), new ActiveParts(leftArm, rightArm, leftLeg, rightLeg), model);
    }
}
