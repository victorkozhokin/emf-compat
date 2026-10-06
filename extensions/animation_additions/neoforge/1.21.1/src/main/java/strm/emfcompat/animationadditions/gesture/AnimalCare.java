package strm.emfcompat.animationadditions.gesture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Feeding an animal, milking it, shearing it. With the food, the bucket or the shears in hand and
 * the animal under the crosshair the hand is already held out to it; the click the game accepts
 * takes it the rest of the way and starts the work, which follows the animal while it lasts. Feeding: the food to its mouth, then the other hand strokes
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

    public static void register(ConfigRegistry.Group config) {
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

    protected boolean poises() {
        return true;
    }

    /** The food, the bucket or the shears in a hand, and under the crosshair an animal that will take it. */
    protected boolean poised(InteractionContext context, Play play) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof EntityHitResult hit)) return false;
        Entity target = hit.getEntity();
        AbstractClientPlayer player = context.player();
        for (InteractionHand hand : InteractionHand.values()) {
            int kind = kindOf(player.getItemInHand(hand), target);
            if (kind < 0) continue;
            if (!play.acted) {
                play.kind = kind;
                play.entity = target.getId();
                play.point = target.position();
                play.right = (player.getMainArm() == HumanoidArm.RIGHT) == (hand == InteractionHand.MAIN_HAND);
            }
            return true;
        }
        return false;
    }

    /** Gone further from the animal than a hand's reach and a step. */
    protected boolean lost(Play play) {
        Entity animal = play.player.level().getEntity(play.entity);
        return animal == null || !animal.isAlive() || animal.distanceTo(play.player) > 4.2f;
    }

    /** What this in the hand would do to that animal: FEED, MILK, SHEAR, or -1 for nothing of ours. */
    public static int kindOf(ItemStack stack, Entity target) {
        if (stack.is(Items.SHEARS) && target instanceof Shearable wool && wool.readyForShearing()) return SHEAR;
        if (stack.is(Items.BUCKET) && (target instanceof Cow || target instanceof Goat) && !((Animal) target).isBaby()) return MILK;
        // All the client knows of whether it will eat: not in love already. (Its age is the server's.)
        return target instanceof Animal animal && !stack.isEmpty() && animal.isFood(stack)
                && (animal.isBaby() || animal.canFallInLove()) ? FEED : -1;
    }

    /** The click is held back until the hand is at the animal; {@code false}: let it through now. */
    public static boolean hold(AbstractClientPlayer player, Entity animal, int kind, boolean mainHand, Runnable click) {
        if (!INSTANCE.isEnabled() || !INSTANCE.defer(player, kind, animal.position(), click)) return false;
        Play play = INSTANCE.play(player);
        play.entity = animal.getId();
        play.right = (player.getMainArm() == HumanoidArm.RIGHT) == mainHand;
        return true;
    }

    protected float approach(Play play) {
        return play.kind == FEED ? .72f : .8f;
    }

    protected double work(Play play) {
        return play.kind == FEED ? 1.5 : play.kind == MILK ? 1.7 : 1.05;
    }

    protected void pose(Play play, float work, Pose out) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        Entity animal = play.player.level().getEntity(play.entity);
        Vec3 at = animal == null ? play.point : animal.getPosition(partial);
        if (at == null) return;
        float height = animal == null ? 1f : animal.getBbHeight(), width = animal == null ? .9f : animal.getBbWidth();
        Vec3 mouth = animal == null ? at.add(0, height * .8, 0) : animal.getEyePosition(partial);
        // Towards the player, level: the side of the animal that is worked at.
        Vec3 side = play.player.getPosition(partial).subtract(at).multiply(1, 0, 1);
        side = side.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : side.normalize();
        Vec3 along = new Vec3(-side.z, 0, side.x);
        boolean right = play.right;
        float level = play.level;
        // The work swells in and out of itself, so its first and last moments are still.
        float busy = play.acted ? (float) Math.sin(Math.PI * Math.min(1, work)) : 0;
        float hand = right ? 1 : -1;
        Vector3f tool, other;
        float toolReach = level, otherReach = level, bend;
        Vec3 watched;
        if (play.kind == FEED) {
            // The food is held out; the animal takes it in little tugs; the giving hand comes away
            // as the other strokes its head - back along it pressing, forward again lifted.
            float stroke = play.acted ? smooth((work - .1f) / .2f) * (1 - smooth((work - .84f) / .16f)) : 0;
            double pass = Math.PI * 2 * 3 * Math.max(0, work - .2f) / .64;
            double back = .13 * Math.cos(pass) * stroke, lift = .06 * Math.max(0, Math.sin(pass)) * stroke;
            double tug = Math.sin(Math.PI * 2 * 5 * work) * Math.exp(-4 * work) * busy;
            tool = model(play, mouth.add(side.scale(.1 + .03 * tug)).add(0, -.04 - .03 * tug, 0));
            other = model(play, mouth.add(0, .22 + lift, 0).add(side.scale(-.1 + back)).add(along.scale(.04 * hand)));
            toolReach = level * (1 - smooth((work - .42f) / .3f));
            otherReach = stroke * Math.min(1, level * 1.4f);
            bend = Math.max(toolReach, otherReach) * ((float) Math.toRadians(9) + Reach.low(tool.y) * (float) Math.toRadians(16));
            // The shoulder of the hand at work comes forward: the giving one first, then the stroking one.
            out.yaw = hand * (.13f * otherReach - .09f * toolReach);
            out.roll = -hand * .03f * (float) Math.cos(pass) * stroke;
            watched = mouth.add(0, .1 * stroke, 0);
            foot(out, right, 1.2f, .8f, .2f);
        } else if (play.kind == MILK) {
            // The bucket held still under it; the other hand draws down and lets go, and the body gives with each draw.
            double draw = Math.PI * 2 * 5 * work;
            float pull = (float) Math.max(0, Math.sin(draw)) * busy, ease = (float) Math.sin(draw) * busy;
            Vec3 under = at.add(side.scale(width * .3)).add(0, height * .36, 0);
            tool = model(play, under.add(along.scale(-.13 * hand)).add(0, -.06, 0));
            other = model(play, under.add(along.scale(.13 * hand + .02 * ease)).add(0, .14 - .08 * pull, 0).add(side.scale(-.03 * pull)));
            bend = level * (float) Math.toRadians(30) + .025f * pull;
            out.yaw = hand * (.08f * level + .02f * ease);
            out.roll = hand * .03f * pull;
            watched = under.add(0, .1, 0);
            foot(out, right, 1.3f, 1.1f, .9f);
        } else {
            // One long pass from the shoulder to the rump, the blades working as they go; the other
            // hand goes ahead of them holding the wool up, and the body turns after the pass.
            float along01 = play.acted ? smooth(work) : .15f;
            double snip = Math.sin(Math.PI * 2 * 7 * work) * busy;
            double where = -.3 + .6 * along01;
            Vec3 flank = at.add(side.scale(width * .32)).add(0, height * .72, 0);
            tool = model(play, flank.add(along.scale(where * hand + .03 * snip)).add(0, .03 * snip, 0));
            other = model(play, flank.add(along.scale((where + .2) * hand)).add(0, .1, 0).add(side.scale(-.08)));
            otherReach = level * (play.acted ? 1 : .6f);
            bend = level * ((float) Math.toRadians(7) + Reach.low(tool.y) * (float) Math.toRadians(16));
            out.yaw = hand * (.06f * level + .14f * (along01 - .5f) * busy);
            watched = flank.add(along.scale(where * hand));
            foot(out, right, 1.2f, .8f, .4f);
        }
        out.hand(right, tool, toolReach);
        out.hand(!right, other, otherReach);
        out.pitch = bend;
        out.look = model(play, watched);
        out.looking = Math.min(1, level * 1.6f) * .85f;
        out.apart = !play.back;
    }

    /** The foot under the free hand forward, the other back, the pair this far apart. */
    static void foot(Pose out, boolean right, float forward, float back, float apart) {
        Vector3f lead = new Vector3f(apart * .5f, 0, -forward), rear = new Vector3f(-apart, 0, back);
        out.rightFoot = right ? rear : new Vector3f(-lead.x, 0, lead.z);
        out.leftFoot = right ? lead : new Vector3f(-rear.x, 0, rear.z);
    }
}
