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
        return 1.7;
    }

    protected void pose(Play play, float phase, Pose out) {
        // A shake starts hard and runs down: quick at first and slowing, each one less than the last;
        // it goes through the body from the hands in - the arms, then the torso, then the head a beat behind.
        float in = swell(phase, .14f, .5f, .95f);
        double turn = Math.PI * 2 * (5.2 * phase - 1.6 * phase * phase);
        float arms = (float) Math.sin(turn) * in, body = (float) Math.sin(turn - .7) * in, head = (float) Math.sin(turn - 1.5) * in;
        float flick = (float) Math.sin(turn * 2) * in;
        out.right = new float[]{(-.3f + .24f * arms) * in, 0, (.4f + .1f * flick) * in, -.5f * Math.abs(arms)};
        out.left = new float[]{(-.3f - .24f * arms) * in, 0, -(.4f - .1f * flick) * in, -.5f * Math.abs(arms)};
        out.yaw = .15f * body;
        out.roll = .05f * (float) Math.cos(turn - .7) * in;
        out.pitch = .05f * in;
        out.head = new float[]{.07f * in, .2f * head};
        out.letGo = .93f;
    }
}
