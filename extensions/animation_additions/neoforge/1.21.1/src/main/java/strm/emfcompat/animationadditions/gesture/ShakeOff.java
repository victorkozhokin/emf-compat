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
 * Out of water, powder snow or mud after a while in it, and on firm ground again: one shake of the
 * arms and the body, and as it runs down a hand brushes the thigh off. Once for a stay, not at
 * every step across the edge.
 */
public final class ShakeOff extends Gesture {
    public static final ShakeOff INSTANCE = new ShakeOff();
    public static final String KEY_ENABLED = "shakeoff.enabled";
    /** In it for this long to be worth shaking off, and out of it for this long before doing so, seconds. */
    private static final float SOAKED = 1.5f, OUT = .6f;
    /** The whole of it; the shake takes the first of these, the brushing starts before it is over. */
    private static final float SECONDS = 3f, SHAKE = 1.55f, BRUSH_AT = 1.25f, BRUSH = 1.6f;

    public String id() {
        return "ShakeOff";
    }

    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static void register(ConfigRegistry.Group config) {
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
        return SECONDS;
    }

    protected void pose(Play play, float phase, Pose out) {
        // A shake starts hard and runs down: quick at first and slowing, each one less than the last;
        // it goes through the body as a wave - the arms, their spread a beat behind, the torso, the head last.
        float t = phase * SECONDS, s = t / SHAKE;
        float in = swell(s, .16f, .4f, 1f);
        double turn = Math.PI * 2 * (4.7 * s - 1.5 * s * s);
        float arms = (float) Math.sin(turn) * in, wave = (float) Math.sin(turn - 1.1) * in;
        float body = (float) Math.sin(turn - .7) * in, head = (float) Math.sin(turn - 1.5) * in;
        // Then the right hand brushes the thigh off: twice down along it pressing, forward again
        // lifted clear, the body bent over it and the eyes on it.
        float b = (t - BRUSH_AT) / BRUSH;
        float over = smooth(b / .22f) * (1 - smooth((b - .72f) / .28f));
        double pass = Math.PI * 2 * 2 * Math.max(0, Math.min(1, (b - .2f) / .5f));
        float down = (float) (.5 - .5 * Math.cos(pass)), clear = (float) Math.max(0, -Math.sin(pass));
        clear *= clear;
        out.right = new float[]{(-.3f + .24f * arms) * in + over * (-.5f + .8f * down), .15f * over,
                (.4f + .1f * wave) * in + over * (.1f + .16f * clear), -.3f * in + .7f * over * (1 - clear)};
        out.left = new float[]{(-.3f - .24f * arms) * in + .12f * over, 0, -(.4f - .1f * wave) * in - .16f * over, -.3f * in};
        out.yaw = .15f * body + over * (.1f + .05f * (down - .5f));
        out.roll = .05f * (float) Math.cos(turn - .7) * in + .09f * over;
        out.pitch = .05f * in + .13f * over;
        out.head = new float[]{.07f * in + .4f * over, .2f * head + .3f * over};
        out.letGo = .95f;
    }
}
