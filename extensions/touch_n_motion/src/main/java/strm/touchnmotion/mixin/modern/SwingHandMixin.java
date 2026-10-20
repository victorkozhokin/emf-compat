package strm.touchnmotion.mixin.modern;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.interaction.InteractionRuntime;

/**
 * From 1.21.11 on: a press with the hand that the game does not swing (a lever on the left reached
 * with the left hand) has its swing drawn on that arm, so the pack turns the torso with the hand
 * and not against it. The model reads the swinging arm from the render state - {@code attackArm},
 * from 26.3 the hand of {@code currentSwing} - filled here for the avatar and every humanoid; it is
 * told the other arm once it is filled. The entity is left as it is: the first-person hand and the
 * server know nothing of it. See the same up to 1.21.10 in mixin/legacy.
 */
@Mixin(HumanoidMobRenderer.class)
public class SwingHandMixin {

    @Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
    private static void touchnmotion$swingOnHand(LivingEntity entity, HumanoidRenderState state, float partialTick,
                                                 ItemModelResolver items, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player) || !InteractionRuntime.swingsOtherArm(player)) return;
        //? if >=26.3 {
        /*var swing = state.currentSwing;
        // A spear's stab stays where the spear is.
        if (swing == null || swing.animation().type() == net.minecraft.world.item.SwingAnimationType.STAB) return;
        state.currentSwing = new LivingEntity.SwingDescription(
                swing.hand() == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.InteractionHand.OFF_HAND
                        : net.minecraft.world.InteractionHand.MAIN_HAND, swing.animation(), swing.durationTicks());
        *///?} else {
        state.attackArm = state.attackArm.getOpposite();
        //?}
    }
}
