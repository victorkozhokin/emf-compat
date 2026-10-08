package strm.touchnmotion.gesture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.InteractionContext;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.touchnmotion.interaction.Body;

/**
 * One hand to a point and back, the body bending to it, held out towards the point already while
 * the player is about to act and pressed home by the click the game accepts: dressing an armour stand at the part
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

    public static void register(ConfigRegistry.Group config) {
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
        play.right = Body.right(player, mainHand);
    }

    /** The click is held back until the hand is at the point; {@code false}: let it through now. */
    public static boolean hold(AbstractClientPlayer player, int kind, Vec3 point, boolean mainHand, Runnable click) {
        if (!EMFCompatConfig.getBoolean(kind == STAND ? KEY_STAND : KEY_SEEDS, true) || !INSTANCE.defer(player, kind, point, click)) return false;
        INSTANCE.play(player).right = Body.right(player, mainHand);
        return true;
    }

    protected boolean poises() {
        return true;
    }

    /**
     * Another player dressing a stand: their swing is the click. Planting: the swing could as well
     * be a blow at the bed, so it is the seed appearing where they looked, while their arm swings.
     */
    protected void remote(InteractionContext context, Play play) {
        if (play.kind != SEED) {
            super.remote(context, play);
            return;
        }
        if (!(play.notes instanceof net.minecraft.core.BlockPos bed) || !strm.touchnmotion.platform.Platform.swinging(play.player) || play.acted && !play.back) return;
        net.minecraft.world.level.block.Block grown = play.player.level().getBlockState(bed).getBlock();
        if (!(grown instanceof CropBlock || grown instanceof StemBlock || grown instanceof NetherWartBlock)) return;
        play.notes = null;
        acted(play);
    }

    /** A piece of armour (or an empty hand) and a stand under the crosshair; a seed and a bed for it. */
    protected boolean poised(InteractionContext context, Play play) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = context.player();
        HitResult sight = sight(context);
        if (sight == null) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            int kind = -1;
            Vec3 point = null;
            if (sight instanceof EntityHitResult hit && hit.getEntity() instanceof ArmorStand
                    && EMFCompatConfig.getBoolean(KEY_STAND, true)
                    && (strm.touchnmotion.platform.Platform.isArmor(stack) || stack.isEmpty() && hand == InteractionHand.MAIN_HAND)) {
                kind = STAND;
                point = hit.getLocation();
            } else if (sight instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                    && hit.getDirection() == Direction.UP && EMFCompatConfig.getBoolean(KEY_SEEDS, true)
                    && stack.getItem() instanceof BlockItem seed && plants(seed)
                    && seed.getBlock().defaultBlockState().canSurvive(player.level(), hit.getBlockPos().above())
                    && player.level().getBlockState(hit.getBlockPos().above()).isAir()) {
                kind = SEED;
                point = hit.getLocation();
            }
            if (kind < 0) continue;
            if (!play.acted) {
                // Where another player's seed will show when it has gone in.
                if (player != mc.player) play.notes = kind == SEED ? ((BlockHitResult) sight).getBlockPos().above() : null;
                play.kind = kind;
                play.point = point;
                play.right = Body.right(player, hand);
            }
            return true;
        }
        return false;
    }

    public static boolean plants(BlockItem seed) {
        return seed.getBlock() instanceof CropBlock || seed.getBlock() instanceof StemBlock || seed.getBlock() instanceof NetherWartBlock;
    }

    protected boolean lost(Play play) {
        return play.point == null || play.player.getEyePosition().distanceTo(play.point) > 5;
    }

    protected float approach(Play play) {
        return play.kind == SEED ? .6f : .72f;
    }

    protected double work(Play play) {
        return play.kind == SEED ? .32 : .3;
    }

    /** The next seed of a row: the hand is down already and only presses again. */
    protected boolean again(Play play, int kind, Vec3 point) {
        if (kind != SEED || play.kind != SEED) return false;
        play.work = 0;
        return true;
    }

    protected void pose(Play play, float work, Pose out) {
        if (play.point == null) return;
        float level = play.level, hand = play.right ? 1 : -1;
        float press = play.acted ? (float) Math.sin(Math.PI * Math.min(1, work)) : 0;
        Vector3f point = model(play, play.point);
        out.look = new Vector3f(point);
        out.looking = Math.min(1, level * 1.6f) * .8f;
        if (play.kind == SEED) {
            // Pressed in with the heel of the hand and a small twist; the weight goes down with it.
            point.y += .9f * press;
            point.x += .5f * hand * (float) Math.sin(Math.PI * 2 * work) * press;
            out.pitch = level * (float) Math.toRadians(play.player.isCrouching() ? 12 : 25) + .04f * press;
            out.yaw = -hand * .1f * level;
            out.roll = hand * .04f * level;
            AnimalCare.foot(out, play.right, 1.3f, .9f, .5f);
        } else {
            // Set in place with a short push, the shoulder going in behind the hand.
            Vector3f direction = new Vector3f(point).sub(play.right ? -5 : 5, 2, 0);
            if (direction.lengthSquared() > 1e-6f) point.add(direction.normalize(.65f * press));
            out.pitch = level * ((float) Math.toRadians(5) + Reach.low(point.y) * (float) Math.toRadians(22)) + .02f * press;
            out.yaw = -hand * (.09f * level + .05f * press);
            AnimalCare.foot(out, play.right, .9f, .6f, .3f);
        }
        out.weightForward = -.35f * level;
        out.apart = !play.back;
        out.hand(play.right, point, level);
        // The other arm goes out and back against the lean instead of hanging by the side.
        out.free(!play.right, Math.min(1, level) * (play.kind == SEED ? 1 : .5f));
    }
}
