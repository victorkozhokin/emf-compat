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
 * arms and the body. Once for a stay, not at
 * every step across the edge.
 */
public final class ShakeOff extends Gesture {
    public static final ShakeOff INSTANCE = new ShakeOff();
    public static final String KEY_ENABLED = "shakeoff.enabled";
    public static final String KEY_WATER = "shakeoff.water", KEY_SNOW = "shakeoff.snow", KEY_MUD = "shakeoff.mud";
    /** In it for this long to be worth shaking off, and out of it for this long before doing so, seconds. */
    private static final float SOAKED = 1.5f, OUT = .6f;
    private static final int WATER = 0, SNOW = 1, MUD = 2;
    private static final class Exposure { float inside, outside; int kind; }
    /** Keep the main shake, then release; no separate hand brushing the clothing. */
    private static final float SECONDS = 1.75f, SHAKE = 1.55f;

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
        config.addChild(KEY_ENABLED, KEY_WATER, "After water", true,
                "On", "Out of water after a swim or a wade.", "Off", "Water is not shaken off.");
        config.addChild(KEY_ENABLED, KEY_SNOW, "After powder snow", true,
                "On", "Out of powder snow, with the arms thrown higher.", "Off", "Snow is not shaken off.");
        config.addChild(KEY_ENABLED, KEY_MUD, "After mud", true,
                "On", "Off mud, with a smaller turn of the body.", "Off", "Mud is not shaken off.");
    }

    protected boolean watches() {
        return true;
    }

    protected void watch(InteractionContext context, Play play) {
        AbstractClientPlayer player = context.player();
        Exposure wet = play.notes instanceof Exposure notes ? notes : new Exposure();
        play.notes = wet;
        float dt = (float) Math.min(.1, context.dt());
        // The game marks "in powder snow" only for a player it moves itself - our own. Another
        // player is placed where the server says, and the mark never comes: the block they stand in says it.
        boolean snow = player.isInPowderSnow || player.getInBlockState().is(Blocks.POWDER_SNOW);
        boolean in = player.isInWater() || snow || player.getBlockStateOn().is(Blocks.MUD);
        if (in) {
            wet.inside += dt;
            wet.kind = player.getBlockStateOn().is(Blocks.MUD) ? MUD : snow ? SNOW : WATER;
            wet.outside = 0;
        } else if (wet.inside >= SOAKED) {
            wet.outside += dt;
            if (wet.outside >= OUT && player.onGround()) {
                wet.inside = wet.outside = 0;
                if (isEnabled() && on(wet.kind == MUD ? KEY_MUD : wet.kind == SNOW ? KEY_SNOW : KEY_WATER)) trigger(player, wet.kind, null);
            } else if (wet.outside > 6) wet.inside = 0;
        } else {
            wet.inside = 0;
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
        out.right = new float[]{(-.3f + .24f * arms) * in, 0, (.4f + .1f * wave) * in, -.3f * in};
        out.left = new float[]{(-.3f - .24f * arms) * in, 0, -(.4f - .1f * wave) * in, -.3f * in};
        out.yaw = (play.kind == MUD ? .09f : .15f) * body;
        out.roll = .05f * (float) Math.cos(turn - .7) * in;
        out.pitch = .05f * in;
        out.head = new float[]{.07f * in, .2f * head};
        out.apart = phase > .04f && phase < .65f;
        out.rightFoot = new Vector3f(-.65f, 0, .35f);
        out.leftFoot = new Vector3f(.65f, 0, -.35f);
        if (play.kind == SNOW) {
            out.right[0] -= .35f * in;
            out.right[2] += .12f * in;
            out.head[1] -= .12f * in;
        }
    }
}
