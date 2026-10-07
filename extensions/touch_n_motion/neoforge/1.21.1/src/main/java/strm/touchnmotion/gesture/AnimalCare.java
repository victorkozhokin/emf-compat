package strm.touchnmotion.gesture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
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
import strm.touchnmotion.interaction.InteractionContext;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.touchnmotion.interaction.Ease;
import strm.touchnmotion.interaction.Body;

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
    public static final String KEY_FEED = "animalcare.feed", KEY_STROKE = "animalcare.stroke", KEY_MILK = "animalcare.milk", KEY_SHEAR = "animalcare.shear";
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
        config.addChild(KEY_ENABLED, KEY_FEED, "Feeding", true,
                "On", "The food is brought to the animal's mouth and held there while it is taken.",
                "Off", "Feeding shows only the game's own arm swing.");
        config.addChild(KEY_FEED, KEY_STROKE, "Stroke it after", true,
                "On", "The feeding hand comes away and the other strokes the animal twice, from the brow back along the neck.",
                "Off", "The food is given and the hand comes back.");
        config.addChild(KEY_ENABLED, KEY_MILK, "Milking", true,
                "On", "Bent to the animal, the bucket held under it and the other hand drawing down.",
                "Off", "Milking shows only the game's own arm swing.");
        config.addChild(KEY_ENABLED, KEY_SHEAR, "Shearing", true,
                "On", "One hand on the animal's back, the shears twice along its flank.",
                "Off", "Shearing shows only the game's own arm swing.");
    }

    private static boolean wanted(int kind) {
        return kind >= 0 && on(kind == FEED ? KEY_FEED : kind == MILK ? KEY_MILK : KEY_SHEAR);
    }

    /** Another player's swing is the click, unless it turns out to have been a blow. */
    protected void remote(InteractionContext context, Play play) {
        super.remote(context, play);
        if (play.acted && !play.back && play.work < .3f
                && play.player.level().getEntity(play.entity) instanceof LivingEntity animal && animal.hurtTime > 0) play.back = true;
    }

    /** The game accepted this player's use of what was in that hand on that animal. */
    public static void done(AbstractClientPlayer player, Entity animal, int kind, boolean mainHand) {
        if (!wanted(kind) || gap(player, animal) > NEAR) return;
        Play play = INSTANCE.trigger(player, kind, animal.position());
        if (play.kind != kind) return;
        play.entity = animal.getId();
        play.right = Body.right(player, mainHand);
    }

    /** No further from the animal than this, blocks, for a hand to be put out to it at all. */
    private static final double NEAR = 2;
    /** How far up the free hand waits for its turn, of the way to where it will work. */
    private static final float WAITS = .3f;

    /** How far the player stands from the animal's side, level, blocks. */
    static double gap(Entity player, Entity animal) {
        AABB box = animal.getBoundingBox();
        double x = Math.max(0, Math.max(box.minX - player.getX(), player.getX() - box.maxX));
        double z = Math.max(0, Math.max(box.minZ - player.getZ(), player.getZ() - box.maxZ));
        return Math.hypot(x, z);
    }

    /**
     * Where the animal's head is: {its mouth, its brow, the way back along its neck}. The box and
     * the eye height say how big it is, the body's turn where the neck leaves it, the head's own
     * turn where the head points from there.
     */
    static Vec3[] head(Entity animal, float partial) {
        Vec3 at = animal.getPosition(partial);
        float body = animal.getYRot(), head = body;
        if (animal instanceof LivingEntity living) {
            body = Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot);
            head = Mth.rotLerp(partial, living.yHeadRotO, living.yHeadRot);
        }
        Vec3 along = Vec3.directionFromRotation(0, body), nose = Vec3.directionFromRotation(0, head);
        double width = animal.getBbWidth(), height = animal.getBbHeight(), eyes = animal.getEyeHeight();
        Vec3 neck = at.add(along.scale(width * .5));
        return new Vec3[]{neck.add(nose.scale(width * .45)).add(0, eyes - height * .14, 0),
                neck.add(nose.scale(width * .2)).add(0, eyes + height * .12, 0), along.scale(-1)};
    }

    protected boolean poises() {
        return true;
    }

    /** The food, the bucket or the shears in a hand, and under the crosshair an animal that will take it. */
    protected boolean poised(InteractionContext context, Play play) {
        AbstractClientPlayer player = context.player();
        if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) return false;
        if (!(sight(context) instanceof EntityHitResult hit)) return false;
        Entity target = hit.getEntity();
        for (InteractionHand hand : InteractionHand.values()) {
            int kind = kindOf(player.getItemInHand(hand), target);
            if (kind < 0 || gap(player, target) > NEAR) continue;
            if (!play.acted) {
                play.kind = kind;
                play.entity = target.getId();
                play.point = target.position();
                play.right = Body.right(player, hand);
            }
            return true;
        }
        return false;
    }

    /** A step further from the animal than when it was done, or turned away from it. */
    protected boolean lost(Play play) {
        Entity animal = play.player.level().getEntity(play.entity);
        if (animal == null || !animal.isAlive()) return true;
        return gap(play.player, animal) > NEAR + .6
                || turnedFrom(play, animal.position().add(0, animal.getBbHeight() * .5, 0));
    }

    /** What this in the hand would do to that animal: FEED, MILK, SHEAR, or -1 for nothing of ours. */
    public static int kindOf(ItemStack stack, Entity target) {
        int kind = kind(stack, target);
        return wanted(kind) ? kind : -1;
    }

    private static int kind(ItemStack stack, Entity target) {
        if (stack.is(Items.SHEARS) && target instanceof Shearable wool && wool.readyForShearing()) return SHEAR;
        if (stack.is(Items.BUCKET) && (target instanceof Cow || target instanceof Goat) && !((Animal) target).isBaby()) return MILK;
        // All the client knows of whether it will eat: not in love already. (Its age is the server's.)
        return target instanceof Animal animal && !stack.isEmpty() && animal.isFood(stack)
                && (animal.isBaby() || animal.canFallInLove()) ? FEED : -1;
    }

    /** The click is held back until the hand is at the animal; {@code false}: let it through now. */
    public static boolean hold(AbstractClientPlayer player, Entity animal, int kind, boolean mainHand, Runnable click) {
        if (!INSTANCE.isEnabled() || gap(player, animal) > NEAR || !INSTANCE.defer(player, kind, animal.position(), click)) return false;
        Play play = INSTANCE.play(player);
        play.entity = animal.getId();
        play.right = Body.right(player, mainHand);
        return true;
    }

    protected float approach(Play play) {
        return play.kind == FEED ? .72f : .8f;
    }

    protected double work(Play play) {
        return play.kind == FEED ? on(KEY_STROKE) ? 2.8 : 1.1 : play.kind == MILK ? 2.6 : 2.2;
    }

    protected void pose(Play play, float work, Pose out) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        Entity animal = play.player.level().getEntity(play.entity);
        Vec3 at = animal == null ? play.point : animal.getPosition(partial);
        if (at == null) return;
        float height = animal == null ? 1f : animal.getBbHeight(), width = animal == null ? .9f : animal.getBbWidth();
        Vec3[] head = animal == null ? null : head(animal, partial);
        Vec3 mouth = head == null ? at.add(0, height * .8, 0) : head[0], brow = head == null ? mouth.add(0, .2, 0) : head[1];
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
            // In turn: the food is brought to its mouth and held there a moment while it is taken;
            // that hand is drawn away; then the other strokes it, twice and unhurried, from the brow
            // back along the neck pressing and forward again lifted. The body makes three moves of
            // it - in to the mouth, over to the brow, back - and none for the strokes themselves.
            boolean strokes = on(KEY_STROKE);
            float waits = on(KEY_FREE_ARM) ? WAITS : 0;
            float away = play.acted ? Ease.smooth((work - .24f) / .2f) : 0;
            float stroke = !strokes ? 0 : play.acted ? Ease.smooth((work - .36f) / .16f) * (1 - Ease.smooth((work - .88f) / .12f)) : 0;
            double pass = Math.PI * 2 * 2 * Ease.unit((work - .52f) / .36f);
            float gone = (float) (.5 - .5 * Math.cos(pass)), up = (float) Math.max(0, -Math.sin(pass));
            Vec3 neck = head == null ? side.scale(-1) : head[2];
            // The food comes to the mouth from where the player stands.
            Vec3 from = play.player.getPosition(partial).subtract(mouth).multiply(1, 0, 1);
            from = from.lengthSqr() < 1e-6 ? side : from.normalize();
            // As long a pass as the arm has: its far end no further off than a hand held out.
            Vec3 feet = play.player.getPosition(partial);
            double far = brow.add(neck.scale(.3)).subtract(feet).multiply(1, 0, 1).length();
            double length = Math.max(.14, Math.min(.3, .3 - (far - 1.15)));
            float taken = play.acted ? bell(work, .1f, .2f, .3f) : 0;
            tool = model(play, mouth.add(from.scale(.08 - .03 * taken)).add(0, -.02, 0));
            other = model(play, brow.add(0, .04 + .09 * up * up * stroke, 0).add(neck.scale(length * gone * stroke)));
            toolReach = level * (1 - away);
            // Until its turn the free hand is half up and ready, not hanging.
            otherReach = Math.min(1, level * 1.4f) * (waits + (1 - waits) * stroke);
            // The body stays in for the whole of it and goes over from the one hand's place to the other's once.
            float stay = level * (!play.acted ? 1 : strokes ? 1 - Ease.smooth((work - .88f) / .12f) : 1 - Ease.smooth((work - .45f) / .4f));
            float over = play.acted && strokes ? Ease.smooth((work - .28f) / .22f) : 0;
            Vector3f mid = model(play, brow.add(0, .04, 0).add(neck.scale(length * .5)));
            out.fitAt = new Vector3f(tool).lerp(mid, over);
            out.fitRight = over < .5f == right;
            out.fitWeight = stay;
            out.fitSeconds = .3f;
            bend = stay * ((float) Math.toRadians(9) + Reach.low(model(play, mouth).y) * (float) Math.toRadians(16));
            // The shoulder of the hand at work comes forward: the giving one, then the stroking one.
            out.yaw = hand * stay * (.07f * over - .05f * (1 - over));
            watched = mouth.lerp(brow, over).add(neck.scale(length * .5 * gone * stroke));
            foot(out, right, 1.2f, .8f, .2f);
        } else if (play.kind == MILK) {
            // In turn: the bucket is put under the udder and held still; the other hand comes to
            // it, draws down three times and goes; the bucket is brought out. The body goes down
            // once, stays, and comes up once.
            Vec3 fore = head == null ? along : head[2].scale(-1);
            float come = play.acted ? Ease.smooth((work - .08f) / .16f) * (1 - Ease.smooth((work - .8f) / .12f)) : 0;
            double draws = Math.PI * 2 * 3 * Ease.unit((work - .26f) / .52f);
            float pull = (float) (.5 - .5 * Math.cos(draws)) * come;
            Vec3 under = at.add(fore.scale(-width * .26)).add(side.scale(width * .24)).add(0, height * .44, 0);
            tool = model(play, under.add(0, -.12, 0));
            other = model(play, under.add(0, .14 - .2 * pull, 0).add(side.scale(-.04 + .05 * pull)));
            float waits = on(KEY_FREE_ARM) ? WAITS : 0;
            otherReach = Math.min(1, level * 1.4f) * (waits + (1 - waits) * come);
            float stay = level * (play.acted ? 1 - Ease.smooth((work - .88f) / .12f) : 1);
            out.fitAt = new Vector3f(tool);
            out.fitRight = right;
            out.fitWeight = stay;
            out.fitSeconds = .3f;
            bend = stay * (float) Math.toRadians(22);
            out.yaw = hand * .06f * stay;
            watched = under.add(0, .1, 0);
            foot(out, right, 1.3f, 1.1f, .9f);
        } else {
            // In turn: the free hand is laid on its back and stays; the shears go along the flank
            // twice, from the shoulder to the rump cutting and forward again lifted clear. The body
            // bends to it once and comes up once.
            Vec3 fore = head == null ? along : head[2].scale(-1);
            // From its front to its rear - or, stood before it or behind, across in front of the player.
            Vec3 line = Math.abs(side.dot(fore)) > .75 ? along.scale(hand) : fore.scale(-1);
            float cut = play.acted ? Ease.smooth((work - .06f) / .1f) * (1 - Ease.smooth((work - .88f) / .12f)) : 0;
            double pass = Math.PI * 2 * 2 * Ease.unit((work - .14f) / .72f);
            float gone = (float) (.5 - .5 * Math.cos(pass)), up = (float) Math.max(0, -Math.sin(pass));
            double where = -.28 + .56 * gone * cut;
            Vec3 flank = at.add(side.scale(width * .32)).add(0, height * .72, 0);
            tool = model(play, flank.add(line.scale(where)).add(0, .07 * up * up * cut, 0).add(side.scale(.05 * up * up * cut)));
            other = model(play, at.add(side.scale(width * .12)).add(0, height * .96, 0));
            otherReach = Math.min(1, level * 1.3f) * (play.acted ? .7f + .3f * Ease.smooth(work / .1f) : .7f);
            float stay = level * (play.acted ? 1 - Ease.smooth((work - .88f) / .12f) : 1);
            Vector3f mid = model(play, flank);
            out.fitAt = mid;
            out.fitRight = right;
            out.fitWeight = stay;
            out.fitSeconds = .3f;
            bend = stay * ((float) Math.toRadians(7) + Reach.low(mid.y) * (float) Math.toRadians(16));
            out.yaw = hand * .06f * stay;
            watched = flank.add(line.scale(where));
            foot(out, right, 1.2f, .8f, .4f);
        }
        out.hand(right, tool, toolReach);
        out.hand(!right, other, otherReach);
        // Broad weight transfer supports the work; individual strokes do not pump the pelvis.
        float effort = level * (play.acted ? 1 - Ease.smooth((work - .88f) / .12f) : 1);
        out.weightSide = (right ? .3f : -.3f) * effort;
        out.weightForward = -.25f * effort;
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
