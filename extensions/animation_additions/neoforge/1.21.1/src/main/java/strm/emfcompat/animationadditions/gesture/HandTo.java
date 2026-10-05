package strm.emfcompat.animationadditions.gesture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * One hand to a point and back, the body bending to it: dressing an armour stand at the part
 * that was clicked, and a seed pressed into the bed where it went in. Planting along a row, each
 * seed takes the hand on from the last without straightening up in between.
 */
public final class HandTo extends Gesture {
    public static final HandTo INSTANCE = new HandTo();
    public static final String KEY_STAND = "armorstand.enabled", KEY_SEEDS = "planting.enabled";
    public static final int STAND = 0, SEED = 1;

    public String id() {
        return "HandTo";
    }

    public boolean isEnabled() {
        return true;
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_STAND, "Dress an armour stand by hand", true,
                "On", "Putting a piece on an armour stand or taking it off, the hand goes to that part of it.",
                "Off", "Only the game's own arm swing.");
        config.addBoolean(KEY_SEEDS, "Plant seeds by hand", true,
                "On", "Planting, the player bends down and presses the seed into the bed.",
                "Off", "Only the game's own arm swing.");
    }

    public static void done(AbstractClientPlayer player, int kind, Vec3 point, boolean mainHand) {
        if (!EMFCompatConfig.getBoolean(kind == STAND ? KEY_STAND : KEY_SEEDS, true)) return;
        Play play = INSTANCE.trigger(player, kind, point);
        if (!play.playing) play.right = (player.getMainArm() == HumanoidArm.RIGHT) == mainHand;
    }

    private static final float IN = .3f, OUT = .58f, END = .93f;

    protected double seconds(Play play) {
        return play.kind == SEED ? 1.25 : 1.35;
    }

    /** The next seed of a row: the hand goes on to it from where it is. */
    protected boolean again(Play play, int kind, Vec3 point) {
        if (kind != SEED || play.kind != SEED) return false;
        if (play.phase > OUT) {
            // Back to the same height of reach on the way in, so nothing jumps.
            float amount = 1 - smooth((play.phase - OUT) / (END - OUT)), lo = 0, hi = IN;
            for (int i = 0; i < 12; i++) {
                float mid = (lo + hi) * .5f;
                if (smooth(mid / IN) < amount) lo = mid;
                else hi = mid;
            }
            play.phase = lo;
        }
        return true;
    }

    protected void pose(Play play, float phase, Pose out) {
        float in = bell(phase, IN, OUT, END);
        Vector3f point = model(play, play.point);
        if (play.kind == SEED) {
            // Pressed in: a short dip at the bottom.
            float press = phase > IN && phase < OUT ? (float) Math.sin(Math.PI * (phase - IN) / (OUT - IN)) : 0;
            point.y += .8f * press;
            out.pitch = in * (float) Math.toRadians(30);
            out.head = new float[]{.25f * in, 0};
            out.apart = phase > .05f && phase < .7f;
            AnimalCare.foot(out, play.right, 1.3f, .9f, .5f);
        } else {
            out.pitch = in * Reach.low(point.y) * (float) Math.toRadians(22);
            out.head = new float[]{.08f * in, 0};
            out.apart = phase > .05f && phase < .7f;
            AnimalCare.foot(out, play.right, .9f, .6f, .3f);
        }
        out.hand(play.right, point, in);
    }
}
