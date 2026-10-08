package strm.touchnmotion.mixin.modern;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.blockuse.BlockUse;
import strm.touchnmotion.interaction.DrawnEntities;

/**
 * A render state being filled from its entity: whose state it is, noted for what is drawn from
 * it ({@link DrawnEntities}); and the swing kept off a player whose hand is on a crank, for as
 * long as the state is read off the entity (up to 26.2; from 26.3 on the swing is no longer two
 * fields of the entity and {@link SwingQuietMixin} does it).
 */
@Mixin(LivingEntityRenderer.class)
public class StateEntityMixin {

    @Unique
    private AbstractClientPlayer touchnmotion$quieted;
    @Unique
    private float touchnmotion$attackAnim, touchnmotion$oAttackAnim;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("HEAD"))
    private void touchnmotion$filling(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        DrawnEntities.filled(state, entity);
        //? if <26.3 {
        // A hand crank held down starts a swing on every repeat of the click; see the same in mixin/legacy.
        if (entity instanceof AbstractClientPlayer player && BlockUse.quietsSwing(player.getUUID())) {
            touchnmotion$quieted = player;
            touchnmotion$attackAnim = player.attackAnim;
            touchnmotion$oAttackAnim = player.oAttackAnim;
            player.attackAnim = 0f;
            player.oAttackAnim = 0f;
        }
        //?}
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"))
    private void touchnmotion$filled(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        //? if <26.3 {
        AbstractClientPlayer player = touchnmotion$quieted;
        if (player == null) return;
        touchnmotion$quieted = null;
        player.attackAnim = touchnmotion$attackAnim;
        player.oAttackAnim = touchnmotion$oAttackAnim;
        //?}
    }
}
