package strm.emfcompat.animationadditions.gesture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Looking through a container: while a chest, a barrel or a shulker box the player faces stands
 * open, one hand rests on its near edge and the other goes in and moves about, dipping now and
 * then; the body leans over it. It lasts as long as the container is open, and ends with both
 * hands drawn back. Whose container it is cannot be read from the game - it is the open one a
 * player looks at from within reach; for this player, the one whose screen is up.
 */
public final class ContainerSearch extends Gesture {
    public static final ContainerSearch INSTANCE = new ContainerSearch();
    public static final String KEY_ENABLED = "containersearch.enabled", KEY_SLOTS = "containersearch.slots";
    private static final long LOOK_EVERY_NANOS = 250_000_000L;
    private static final double REACH = 4.5;
    private static final float HOLD = .5f;

    private static final class Open {
        BlockPos pos;
        boolean open;
        long lookedAt, actedAt;
        /** For another player: how many slot actions their game had counted when last looked. */
        int heardActions = -1;
        java.util.List<net.minecraft.world.item.ItemStack> contents;
    }

    public String id() {
        return "ContainerSearch";
    }

    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Look through open containers", true,
                "On", "While a chest, a barrel or a shulker box is open, one hand holds its edge and the other looks through it.",
                "Off", "Only opening it shows.");
        config.addChild(KEY_ENABLED, KEY_SLOTS, "Hands answer what is moved", true,
                "On", "In your own open container the hands rest until something is put in, taken or moved, and work then.",
                "Off", "The hand goes round inside it for as long as it is open.");
    }

    protected boolean watches() {
        return true;
    }

    protected void watch(InteractionContext context, Play play) {
        long now = context.now();
        Open open = play.notes instanceof Open notes ? notes : new Open();
        play.notes = open;
        if (now - open.lookedAt < LOOK_EVERY_NANOS) return;
        open.lookedAt = now;
        AbstractClientPlayer player = context.player();
        BlockPos pos = looked(player);
        if (pos != null && !opened(player, pos)) pos = null;
        // The one it started with is kept while it stays open, wherever the eyes wander inside it.
        if (open.pos != null && play.playing && pos == null && opened(player, open.pos)
                && player.getEyePosition().distanceTo(Vec3.atCenterOf(open.pos)) < REACH + 1) pos = open.pos;
        open.open = pos != null;
        if (pos == null) return;
        if (!pos.equals(open.pos)) { open.contents = null; open.actedAt = 0; }
        open.pos = pos;
        if (player == Minecraft.getInstance().player && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen) {
            var slots = screen.getMenu().slots;
            java.util.List<net.minecraft.world.item.ItemStack> current = new java.util.ArrayList<>();
            for (var slot : slots) current.add(slot.getItem().copy());
            if (open.contents != null && (open.contents.size() != current.size() || changed(open.contents, current))) open.actedAt = now;
            open.contents = current;
        }
        int heard = strm.emfcompat.animationadditions.net.Inputs.menuActions(player);
        if (heard >= 0) {
            if (open.heardActions >= 0 && heard != open.heardActions) open.actedAt = now;
            open.heardActions = heard;
        } else {
            open.heardActions = -1;
        }
        if (!play.playing && !play.pending && isEnabled()) {
            Play started = trigger(player, 0, Vec3.atCenterOf(pos));
            started.right = player.getMainArm() == HumanoidArm.RIGHT;
        }
    }

    private static BlockPos looked(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = strm.emfcompat.animationadditions.net.Inputs.sight(player, REACH, 1f);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
    }

    private static boolean opened(AbstractClientPlayer player, BlockPos pos) {
        Level level = player.level();
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return false;
        Minecraft mc = Minecraft.getInstance();
        // This player's own: the screen is the word on it, also for what has no lid to watch.
        if (player == mc.player && mc.screen instanceof AbstractContainerScreen<?> && entity instanceof BaseContainerBlockEntity) return true;
        if (entity instanceof ShulkerBoxBlockEntity shulker) return shulker.getProgress(1f) > .05f;
        if (entity instanceof LidBlockEntity lid) return lid.getOpenNess(1f) > .05f;
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(BarrelBlock.OPEN) && state.getValue(BarrelBlock.OPEN);
    }

    private static boolean changed(java.util.List<net.minecraft.world.item.ItemStack> before, java.util.List<net.minecraft.world.item.ItemStack> after) {
        for (int i = 0; i < after.size(); i++) if (!net.minecraft.world.item.ItemStack.matches(before.get(i), after.get(i))) return true;
        return false;
    }

    protected int priority() {
        // Open-container work replaces the opening gesture (8), but yields to held controls (12).
        return 10;
    }

    protected float holdAt() {
        return HOLD;
    }

    protected boolean sustain(Play play) {
        return play.notes instanceof Open open && open.open && isEnabled();
    }

    protected double seconds(Play play) {
        return 1.0;
    }

    protected void pose(Play play, float phase, Pose out) {
        if (!(play.notes instanceof Open open) || open.pos == null) return;
        float in = phase <= HOLD ? swell(phase, HOLD - .04f, 2f, 3f) : 1 - smooth((phase - HOLD) / .4f);
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 middle = Vec3.atCenterOf(open.pos);
        Vec3 side = play.player.getPosition(partial).subtract(middle).multiply(1, 0, 1);
        side = side.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : side.normalize();
        Vec3 along = new Vec3(-side.z, 0, side.x);
        boolean right = play.right;
        // In it: round and round, and a dip every second or so as something is taken hold of.
        boolean local = (play.player == Minecraft.getInstance().player || strm.emfcompat.animationadditions.net.Inputs.menuActions(play.player) >= 0) && on(KEY_SLOTS);
        float elapsed = open.actedAt == 0 ? 10f : (System.nanoTime() - open.actedAt) * 1e-9f;
        float activity = local ? bell(elapsed, .12f, .65f, 1.15f) : .35f;
        double turn = (local ? Math.min(elapsed, 1.15f) : play.held) * 2.3, beat = (play.held % 1.25f) / 1.25f;
        double dip = (beat < .28 ? Math.sin(Math.PI * beat / .28) : 0) * activity;
        Vec3 inside = middle.add(0, .28 - .12 * dip, 0).add(side.scale(.1 + .1 * Math.sin(turn) * activity))
                .add(along.scale(.16 * Math.cos(turn * .7) * activity * (right ? -1 : 1)));
        Vec3 rim = middle.add(0, .46, 0).add(side.scale(.42)).add(along.scale(right ? .3 : -.3));
        BlockState state = play.player.level().getBlockState(open.pos);
        boolean front = state.hasProperty(BarrelBlock.FACING) && state.getValue(BarrelBlock.FACING).getAxis().isHorizontal();
        if (state.hasProperty(BarrelBlock.FACING)) {
            var face = state.getValue(BarrelBlock.FACING);
            if (face.getAxis().isHorizontal()) {
                Vec3 normal = Vec3.atLowerCornerOf(face.getNormal()), tangent = new Vec3(-normal.z, 0, normal.x);
                inside = middle.add(normal.scale(.43 - .12 * dip)).add(tangent.scale(.08 * Math.sin(turn) * activity));
                rim = middle.add(normal.scale(.52)).add(tangent.scale(right ? .3 : -.3)).add(0, -.15, 0);
            }
        }
        out.hand(right, model(play, inside), in);
        out.hand(!right, model(play, rim), in);
        // A front opening is worked from its face; bending over it would put the head through the barrel.
        out.pitch = in * ((float) Math.toRadians(8) + (front ? 0 : Reach.low(model(play, inside).y) * (float) Math.toRadians(22))) + .03f * (float) dip * in;
        // The eyes on the hand that looks, the shoulder of it down and in, the body giving with each dip.
        out.look = model(play, inside);
        out.looking = in * .8f;
        out.yaw = (right ? -1 : 1) * (.09f + .03f * (float) Math.sin(turn)) * in;
        out.roll = (right ? 1 : -1) * .04f * (float) dip * in;
        out.weightSide = (right ? .25f : -.25f) * in;
        out.weightForward = -.25f * in;
        out.apart = phase > .04f && phase < HOLD + .2f;
        AnimalCare.foot(out, right, 1.1f, .7f, .5f);
        out.letGo = .92f;
    }
}
