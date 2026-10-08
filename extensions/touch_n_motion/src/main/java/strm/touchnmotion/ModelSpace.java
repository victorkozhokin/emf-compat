package strm.touchnmotion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import strm.emfcompat.core.ik.IKFrame;
import strm.touchnmotion.create.ejector.EjectorLaunch;
import strm.touchnmotion.footgrounding.compat.FootGrounding;
import strm.touchnmotion.footgrounding.compat.HorseFootGrounding;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.motion.MotionRuntime;
import strm.touchnmotion.torso.TorsoLean;
import strm.touchnmotion.wallhand.WallSqueeze;

import java.util.function.Consumer;

/**
 * Right before the model is animated the pose stack is exactly the model's space: every feature
 * looks at the world from there. Foot grounding goes first, because it lowers the model on this
 * same stack and the others aim from where the model is really drawn.
 *
 * <p>Where "right before the model is animated" is in the game's code is each version's own
 * ({@code mixin/legacy}, {@code mixin/modern}); what is done there is this.</p>
 */
public final class ModelSpace {
    private ModelSpace() {
    }

    /**
     * The space the model's parts are in, as it is on the stack now: what a part's place is
     * measured in and a point of the world is brought into to aim a part at it.
     *
     * <p>From 1.21.11 on a player's model has a root part every other part hangs off, and a pack
     * can move it - Fresh Animations leans the whole player with it, walking aslant. The stack
     * knows nothing of that: the root is applied when the model is drawn. So the root's pose is
     * laid on top of the stack here - the one the model was last drawn with, a frame old, which
     * the pack only ever changes smoothly. Without it a hand misses its place by as much as the
     * root has moved the shoulder.</p>
     */
    public static IKFrame frame(net.minecraft.world.entity.Entity entity, PoseStack stack) {
        org.joml.Matrix4f pose = stack.last().pose();
        //? if >=1.21.11 {
        /*org.joml.Matrix4f root = TouchNMotionHook.Drawn.root(entity.getUUID());
        if (root != null) pose = new org.joml.Matrix4f(pose).mul(root);
        *///?}
        return IKFrame.capture(pose, strm.touchnmotion.platform.Platform.cameraPosition());
    }

    /**
     * @param model what only the caller can do, having the model or its render state in hand: called for a
     *              player once the stack is turned and before anything measures from it
     */
    public static void before(LivingEntity entity, PoseStack stack, float partialTick, Consumer<AbstractClientPlayer> model) {
        if (HorseFootGrounding.handles(entity)) {
            HorseFootGrounding.modelPose((AbstractHorse) entity, stack, partialTick);
            return;
        }
        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (strm.touchnmotion.compat.ParCoolActivity.active(player)
                || strm.touchnmotion.compat.WholeBody.held(player)) {
            InteractionRuntime.suspend(player.getUUID());
            return;
        }
        strm.touchnmotion.blockuse.aeronautics.CockpitControls.orient(player, stack);
        strm.touchnmotion.ride.MinecartRide.orient(player, stack);
        strm.touchnmotion.wallhand.FenceLean.orient(player, stack);
        strm.touchnmotion.lookat.LookAt.orient(player, stack);
        model.accept(player);
        FootGrounding.modelPose(player, stack);
        IKFrame frame = frame(player, stack);
        strm.touchnmotion.blockuse.aeronautics.CockpitControls.frame(player, frame);
        MotionRuntime.modelPose(player);
        InteractionRuntime.modelPose(player, frame);
        strm.touchnmotion.blockuse.BlockUse.frame(player, frame);
        EjectorLaunch.modelPose(player);
        WallSqueeze.modelPose(player, frame);
        TorsoLean.modelPose(player);
    }
}
