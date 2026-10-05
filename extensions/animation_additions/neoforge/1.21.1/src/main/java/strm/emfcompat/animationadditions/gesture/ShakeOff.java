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
 * Out of water, powder snow or mud after a while in it, and on firm ground again: a shake of the
 * arms, the body and the legs, and then one foot is lifted, looked at and shaken off. Once for a stay, not at every step across the edge.
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
        return 3.3;
    }

    /** The shake proper is over by here; then a foot is lifted, looked at and shaken. */
    private static final float SHAKEN = .46f, LIFT = .5f, DOWN = .88f;

    protected void pose(Play play, float phase, Pose out) {
        // A shake starts hard and runs down: quick at first and slowing, each one less than the last;
        // it goes through the body from the hands in - the arms, the torso, the head a beat behind,
        // and the legs take it up from the hips.
        float in = swell(phase, .06f, .3f, SHAKEN);
        float p = Math.min(1, phase / SHAKEN);
        double turn = Math.PI * 2 * (5.4 * p - 1.7 * p * p);
        float arms = (float) Math.sin(turn) * in, body = (float) Math.sin(turn - .7) * in, head = (float) Math.sin(turn - 1.5) * in;
        float flick = (float) Math.sin(turn * 2) * in, legs = (float) Math.sin(turn - 1.1) * in;
        // Then one foot: up in front, the arms out a little to the sides for it, a look down at it, three shakes, down.
        float up = smooth((phase - LIFT) / .1f) * (1 - smooth((phase - DOWN) / (.97f - DOWN)));
        float kick = phase > .63f && phase < .86f ? (float) Math.sin(Math.PI * 2 * 3 * (phase - .63f) / .23f) * up : 0;
        float wide = Math.max(in * .75f, up);
        out.right = new float[]{-.3f * in + .24f * arms - .12f * up, 0, .4f * in + .1f * flick + .5f * up + .05f * kick, -.5f * Math.abs(arms)};
        out.left = new float[]{-.3f * in - .24f * arms - .12f * up, 0, -(.4f * in - .1f * flick) - .5f * up + .05f * kick, -.5f * Math.abs(arms)};
        out.rightLeg = new float[]{.1f * legs - .66f * up + .13f * kick, 0, .05f * in + .12f * up + .1f * kick};
        out.leftLeg = new float[]{-.1f * legs, 0, -.05f * in};
        out.yaw = .15f * body + .05f * up;
        // Over the standing leg while the other is up.
        out.roll = .05f * (float) Math.cos(turn - .7) * in - .07f * up;
        out.pitch = .05f * wide + .1f * up;
        out.head = new float[]{.07f * in + .42f * up, .2f * head + .22f * up + .04f * kick};
        out.letGo = .97f;
    }
}
