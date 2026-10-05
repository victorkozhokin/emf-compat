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
 * Feeding an animal, milking it, shearing it - set off by the interaction the game accepted, and
 * following the animal while it lasts. Feeding: the food to its mouth, then the other hand strokes
 * its head. Milking: bent down, the bucket under it, the other hand working. Shearing: the shears
 * along the wool, the other hand on its back.
 */
public final class AnimalCare extends Gesture {
    public static final AnimalCare INSTANCE = new AnimalCare();
    public static final String KEY_ENABLED = "animalcare.enabled";
    public static final int FEED = 0, MILK = 1, SHEAR = 2;

    public String id() {
        return "AnimalCare";
    }

    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Feed, milk and shear by hand", true,
                "On", "Feeding an animal the hand goes to its mouth and the other strokes it; milking and shearing are done bent to the animal.",
                "Off", "These show only the game's own arm swing.");
    }

    /** The game accepted this player's use of what was in that hand on that animal. */
    public static void done(AbstractClientPlayer player, Entity animal, int kind, boolean mainHand) {
        Play play = INSTANCE.trigger(player, kind, animal.position());
        if (play.kind != kind) return;
        play.entity = animal.getId();
        play.right = (player.getMainArm() == HumanoidArm.RIGHT) == mainHand;
    }

    protected double seconds(Play play) {
        return play.kind == FEED ? 2.3 : play.kind == MILK ? 2.6 : 1.8;
    }

    protected void pose(Play play, float phase, Pose out) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        Entity animal = play.player.level().getEntity(play.entity);
        Vec3 at = animal == null ? play.point : animal.getPosition(partial);
        float height = animal == null ? 1f : animal.getBbHeight(), width = animal == null ? .9f : animal.getBbWidth();
        Vec3 mouth = animal == null ? at.add(0, height * .8, 0) : animal.getEyePosition(partial);
        // Towards the player, level: the side of the animal that is worked at.
        Vec3 side = play.player.getPosition(partial).subtract(at).multiply(1, 0, 1);
        side = side.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : side.normalize();
        Vec3 along = new Vec3(-side.z, 0, side.x);
        boolean right = play.right;
        Vector3f tool, other;
        float bend, toolReach, otherReach;
        if (play.kind == FEED) {
            float give = bell(phase, .22f, .46f, .72f), stroke = bell(phase - .3f, .2f, .5f, .64f);
            float rub = phase > .5f && phase < .8f ? (float) Math.sin(Math.PI * 4 * (phase - .5f) / .3f) : 0;
            tool = model(play, mouth.add(side.scale(.12)));
            other = model(play, mouth.add(0, .22, 0).add(along.scale(.1 * rub)));
            toolReach = give;
            otherReach = stroke;
            bend = Math.max(give, stroke) * Reach.low(model(play, mouth).y) * (float) Math.toRadians(20);
            out.head = new float[]{.12f * Math.max(give, stroke), 0};
            out.apart = phase > .06f && phase < .8f;
            foot(out, right, 1.2f, .8f, .2f);
        } else if (play.kind == MILK) {
            float in = bell(phase, .25f, .78f, .95f);
            float pull = phase > .34f && phase < .76f ? (float) Math.sin(Math.PI * 6 * (phase - .34f) / .42f) : 0;
            Vec3 under = at.add(side.scale(width * .3)).add(0, height * .36, 0);
            tool = model(play, under.add(along.scale(right ? -.12 : .12)));
            other = model(play, under.add(along.scale(right ? .12 : -.12)).add(0, .1 + .07 * pull, 0));
            toolReach = otherReach = in;
            bend = in * (float) Math.toRadians(30);
            out.head = new float[]{.2f * in, 0};
            out.apart = phase > .05f && phase < .82f;
            foot(out, right, 1.3f, 1.1f, .9f);
        } else {
            float in = bell(phase, .25f, .72f, .93f);
            float snip = phase > .3f && phase < .72f ? (float) Math.sin(Math.PI * 3 * (phase - .3f) / .42f) : 0;
            Vec3 wool = at.add(side.scale(width * .32)).add(0, height * .72, 0).add(along.scale(.28 * snip));
            tool = model(play, wool);
            other = model(play, at.add(side.scale(width * .1)).add(0, height * .98, 0));
            toolReach = otherReach = in;
            bend = in * Reach.low(model(play, wool).y) * (float) Math.toRadians(18);
            out.head = new float[]{.1f * in, 0};
            out.apart = phase > .05f && phase < .78f;
            foot(out, right, 1.2f, .8f, .4f);
        }
        out.hand(right, tool, toolReach);
        out.hand(!right, other, otherReach);
        out.pitch = bend;
    }

    /** The foot under the free hand forward, the other back, the pair this far apart. */
    static void foot(Pose out, boolean right, float forward, float back, float apart) {
        Vector3f lead = new Vector3f(apart * .5f, 0, -forward), rear = new Vector3f(-apart, 0, back);
        out.rightFoot = right ? rear : new Vector3f(-lead.x, 0, lead.z);
        out.leftFoot = right ? lead : new Vector3f(-rear.x, 0, rear.z);
    }
}
