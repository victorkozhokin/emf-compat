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
            ItemStack stack = player.getItemInHand(hand);
            int kind = stack.is(Items.SHEARS) && target instanceof Shearable wool && wool.readyForShearing() ? SHEAR
                    : stack.is(Items.BUCKET) && (target instanceof Cow || target instanceof Goat) && !((Animal) target).isBaby() ? MILK
                    : target instanceof Animal animal && !stack.isEmpty() && animal.isFood(stack)
                    // All the client knows of whether it will eat: not in love already. (Its age is the server's.)
                    && (animal.isBaby() || animal.canFallInLove()) ? FEED : -1;
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
        Vector3f tool, other;
        float toolReach = level, otherReach = level, bend;
        if (play.kind == FEED) {
            // The food is held out and given; the giving hand comes away as the other strokes the head.
            float stroke = play.acted ? smooth((work - .12f) / .2f) * (1 - smooth((work - .8f) / .2f)) : 0;
            float rub = (float) Math.sin(Math.PI * 2 * 2 * work) * stroke;
            tool = model(play, mouth.add(side.scale(.1)).add(0, -.04 + .03 * Math.sin(Math.PI * 6 * work) * busy, 0));
            other = model(play, mouth.add(0, .24, 0).add(along.scale(.11 * rub)).add(side.scale(-.05 * rub)));
            toolReach = level * (1 - smooth((work - .5f) / .3f));
            otherReach = stroke * Math.min(1, level * 1.4f);
            bend = Math.max(toolReach, otherReach) * ((float) Math.toRadians(9) + Reach.low(tool.y) * (float) Math.toRadians(16));
            out.head = new float[]{.12f * level, 0};
            foot(out, right, 1.2f, .8f, .2f);
        } else if (play.kind == MILK) {
            float pull = (float) Math.sin(Math.PI * 2 * 4 * work) * busy;
            Vec3 under = at.add(side.scale(width * .3)).add(0, height * .36, 0);
            tool = model(play, under.add(along.scale(right ? -.13 : .13)).add(0, -.04, 0));
            other = model(play, under.add(along.scale(right ? .13 : -.13)).add(0, .12 + .07 * pull, 0));
            bend = level * (float) Math.toRadians(30) + .02f * pull;
            out.head = new float[]{.2f * level, 0};
            foot(out, right, 1.3f, 1.1f, .9f);
        } else {
            float sweep = (float) Math.sin(Math.PI * 2 * 1.5 * work) * busy;
            Vec3 wool = at.add(side.scale(width * .32)).add(0, height * .72, 0).add(along.scale(.3 * sweep));
            tool = model(play, wool);
            other = model(play, at.add(side.scale(width * .1)).add(0, height * .98, 0));
            otherReach = level * (play.acted ? 1 : .5f);
            bend = level * ((float) Math.toRadians(6) + Reach.low(tool.y) * (float) Math.toRadians(16));
            out.head = new float[]{.1f * level, .05f * sweep};
            out.yaw = .04f * sweep;
            foot(out, right, 1.2f, .8f, .4f);
        }
        out.hand(right, tool, toolReach);
        out.hand(!right, other, otherReach);
        out.pitch = bend;
        out.apart = !play.back;
    }

    /** The foot under the free hand forward, the other back, the pair this far apart. */
    static void foot(Pose out, boolean right, float forward, float back, float apart) {
        Vector3f lead = new Vector3f(apart * .5f, 0, -forward), rear = new Vector3f(-apart, 0, back);
        out.rightFoot = right ? rear : new Vector3f(-lead.x, 0, lead.z);
        out.leftFoot = right ? lead : new Vector3f(-rear.x, 0, rear.z);
    }
}
