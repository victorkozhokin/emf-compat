package strm.emfcompat.animationadditions.gesture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

import net.minecraft.world.level.block.Blocks;
import strm.emfcompat.animationadditions.interaction.InteractionContext;

/**
 * Out of water, powder snow or mud after a while in it, and on firm ground again: one short shake
 * of the arms and the body. Once for a stay, not at every step across the edge.
 */
public final class ShakeOff extends Gesture {
    public static final ShakeOff INSTANCE = new ShakeOff();
    public static final String KEY_ENABLED = "shakeoff.enabled";
    /** In it for this long to be worth shaking off, and out of it for this long before doing so, seconds. */
    private static final float SOAKED = 1.5f, OUT = .6f;

    public String id() {
        return "ShakeOff";
    }

    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Shake off water, snow and mud", true,
                "On", "Out of water, powder snow or mud, the player shakes it off once.",
                "Off", "Nothing shows.");
    }

    protected boolean watches() {
        return true;
    }

    protected void watch(InteractionContext context, Play play) {
        AbstractClientPlayer player = context.player();
        float[] wet = play.notes instanceof float[] notes ? notes : new float[2];
        play.notes = wet;
        float dt = (float) Math.min(.1, context.dt());
        boolean in = player.isInWater() || player.isInPowderSnow || player.getBlockStateOn().is(Blocks.MUD);
        if (in) {
            wet[0] += dt;
            wet[1] = 0;
        } else if (wet[0] >= SOAKED) {
            wet[1] += dt;
            if (wet[1] >= OUT && player.onGround()) {
                wet[0] = wet[1] = 0;
                if (isEnabled()) trigger(player, 0, null);
            } else if (wet[1] > 6) wet[0] = 0;
        } else {
            wet[0] = 0;
        }
    }

    protected boolean ready(AbstractClientPlayer player) {
        return super.ready(player) && player.onGround() && !player.isInWater() && !player.swinging && !player.isUsingItem();
    }

    protected double seconds(Play play) {
        return 1.5;
    }

    protected void pose(Play play, float phase, Pose out) {
        float in = bell(phase, .2f, .72f, .92f);
        float shake = (float) Math.sin(Math.PI * 2 * 3.5 * phase) * in;
        // Both arms a little forward and out, flicked against each other; the body and the head with them.
        out.right = new float[]{(-.3f + .22f * shake) * in, 0, .42f * in, 0};
        out.left = new float[]{(-.3f - .22f * shake) * in, 0, -.42f * in, 0};
        out.yaw = .13f * shake;
        out.roll = .04f * shake;
        out.head = new float[]{.06f * in, .16f * shake};
        out.letGo = .92f;
    }
}
