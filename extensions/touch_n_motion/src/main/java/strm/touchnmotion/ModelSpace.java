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
        // Every creature drawn comes through here: all but the players and the horses leave at once, before anything is worked out.
        if (!(entity instanceof AbstractClientPlayer) && !HorseFootGrounding.handles(entity)) return;
        // A shader mod draws the entity once more for the shadows, on a stack that starts from the sun's view.
        if (strm.touchnmotion.compat.ShadowPass.drawing()) {
            org.joml.Matrix4f view = strm.touchnmotion.compat.ShadowPass.view();
            if (view != null) under(new org.joml.Matrix4f(view), entity, stack, partialTick, model);
            return;
        }
        //? if <1.20.5 {
        /*// Before 1.20.5 the stack an entity is drawn on has the camera's turn in it as well, under everything
        // else; from then on that is the game's own matrix and the stack is the world, moved to the camera.
        under(new org.joml.Matrix4f(new org.joml.Matrix3f(com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix()).invert()),
                entity, stack, partialTick, model);
        *///?} else {
        unturned(entity, stack, partialTick, model);
        //?}
    }

    /**
     * Everything here reads the stack as the world, moved to the camera. Where something else lies under that
     * on the stack, it is taken off for the work and put back after.
     */
    private static void under(org.joml.Matrix4f view, LivingEntity entity, PoseStack stack, float partialTick,
                              Consumer<AbstractClientPlayer> model) {
        org.joml.Matrix4f off = new org.joml.Matrix4f(view).invert();
        org.joml.Matrix3f turn = new org.joml.Matrix3f(view), unturn = new org.joml.Matrix3f(off);
        stack.last().pose().mulLocal(off);
        stack.last().normal().mulLocal(unturn);
        try {
            unturned(entity, stack, partialTick, model);
        } finally {
            stack.last().pose().mulLocal(view);
            stack.last().normal().mulLocal(turn);
        }
    }

    private static void unturned(LivingEntity entity, PoseStack stack, float partialTick, Consumer<AbstractClientPlayer> model) {
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
