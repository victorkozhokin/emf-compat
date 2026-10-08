package strm.touchnmotion.gesture;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.interaction.InteractionContext;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import strm.touchnmotion.interaction.Ease;
import strm.touchnmotion.interaction.Body;

/**
 * Putting on armour and Curios accessories: when a piece appears in a slot - however it got
 * there, by using the item or through the inventory, on this player or one in view - both hands
 * go to where it is worn: a helmet to the head, a chestplate to the chest, leggings to the hips,
 * boots down to the feet; an amulet to the neck, a ring or a bracelet to the other hand, a belt
 * to the waist. Several at once are put on one after another. What a player already wears when
 * first seen sets nothing off.
 */
public final class ArmorDon extends Gesture {
    public static final ArmorDon INSTANCE = new ArmorDon();
    public static final String KEY_ENABLED = "armordon.enabled";
    public static final String KEY_HELMET = "armordon.helmet", KEY_CHEST = "armordon.chest", KEY_INSPECT = "armordon.inspect", KEY_CURIOS = "armordon.curios";
    private static final String[] KEYS = {KEY_HELMET, KEY_CHEST, KEY_INSPECT, KEY_INSPECT};
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatGesture");
    static final int HEAD = 0, CHEST = 1, LEGS = 2, FEET = 3, NECK = 4, HAND = 5, WAIST = 6, BACK = 7;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Seen for this long before a change counts: equipment arriving with the player is not put on. */
    private static final long SETTLE_NANOS = 1_000_000_000L;
    private static final long CURIOS_EVERY_NANOS = 500_000_000L;

    /** Where a Curios slot is worn, by the slot's own id - never guessed from the item. */
    private static final Map<String, Integer> CURIOS = Map.of("head", HEAD, "necklace", NECK, "charm", NECK,
            "ring", HAND, "hands", HAND, "bracelet", HAND, "belt", WAIST, "body", CHEST, "back", BACK);

    private static final class Worn {
        final Item[] armour = new Item[4];
        final Map<String, java.util.List<String>> curios = new HashMap<>();
        long since, curiosAt, roomAt;
        boolean room;
        int queued;
        boolean curiosKnown;
    }

    public String id() {
        return "ArmorDon";
    }

    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Put on armour and accessories", true,
                "On", "A piece of armour or a Curios accessory put on takes both hands to where it is worn.",
                "Off", "It just appears.");
        config.addChild(KEY_ENABLED, KEY_HELMET, "Helmet", true,
                "On", "Pushed down on to the head with one hand, twice, the second time harder.", "Off", "A helmet just appears.");
        config.addChild(KEY_ENABLED, KEY_CHEST, "Chestplate", true,
                "On", "Both hands from the breast out over the ribs and down the sides, and a shrug to seat it.", "Off", "A chestplate just appears.");
        config.addChild(KEY_ENABLED, KEY_INSPECT, "Leggings and boots", true,
                "On", "The player looks them over: a foot lifted and turned, the arms out for balance.", "Off", "Leggings and boots just appear.");
        config.addChild(KEY_ENABLED, KEY_CURIOS, "Accessories", true,
                "On", "A Curios ring, bracelet, necklace, belt or back piece is put on where it is worn.", "Off", "Accessories just appear.");
    }

    protected boolean watches() {
        return true;
    }

    protected void watch(InteractionContext context, Play play) {
        AbstractClientPlayer player = context.player();
        long now = context.now();
        boolean first = !(play.notes instanceof Worn);
        Worn worn = first ? new Worn() : (Worn) play.notes;
        if (first) {
            play.notes = worn;
            worn.since = now;
        }
        boolean settled = now - worn.since > SETTLE_NANOS && isEnabled();
        for (int i = 0; i < 4; i++) {
            Item item = player.getItemBySlot(SLOTS[i]).getItem();
            if (item != worn.armour[i] && item != Items.AIR && settled && on(KEYS[i])) worn.queued |= 1 << i;
            worn.armour[i] = item;
            if (item == Items.AIR) worn.queued &= ~(1 << i);
        }
        if (now - worn.curiosAt > CURIOS_EVERY_NANOS) {
            worn.curiosAt = now;
            curios(player, worn, settled && worn.curiosKnown && on(KEY_CURIOS));
            worn.curiosKnown = true;
        }
        if (worn.queued != 0 && !play.playing && !play.pending) {
            int kind = Integer.numberOfTrailingZeros(worn.queued);
            worn.queued &= ~(1 << kind);
            trigger(player, kind, null);
        }
    }

    private static boolean curiosMissing;
    private static Method inventoryOf, curiosOf, stacksOf, slotsOf, stackIn;

    /** Reads what Curios has this player wearing, slot id by slot id, and queues what is new. */
    private static void curios(AbstractClientPlayer player, Worn worn, boolean queue) {
        if (curiosMissing) return;
        try {
            if (inventoryOf == null) {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                inventoryOf = api.getMethod("getCuriosInventory", LivingEntity.class);
                curiosOf = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getCurios");
                stacksOf = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler").getMethod("getStacks");
                Class<?> items = Class.forName("net.neoforged.neoforge.items.IItemHandler");
                slotsOf = items.getMethod("getSlots");
                stackIn = items.getMethod("getStackInSlot", int.class);
            }
            Object inventory = ((java.util.Optional<?>) inventoryOf.invoke(null, player)).orElse(null);
            if (inventory == null) return;
            for (Map.Entry<?, ?> slot : ((Map<?, ?>) curiosOf.invoke(inventory)).entrySet()) {
                String id = String.valueOf(slot.getKey());
                Object stacks = stacksOf.invoke(slot.getValue());
                java.util.List<String> now = new java.util.ArrayList<>();
                int count = (int) slotsOf.invoke(stacks);
                for (int i = 0; i < count; i++) {
                    ItemStack stack = (ItemStack) stackIn.invoke(stacks, i);
                    now.add(stack.isEmpty() ? "-" : stack.getItem().toString());
                }
                java.util.List<String> was = worn.curios.put(id, now);
                Integer place = CURIOS.get(id);
                // New in the slot, and something rather than nothing: taking one off is not putting one on.
                if (queue && place != null && GestureMath.equipped(was == null ? java.util.List.of() : was, now)) worn.queued |= 1 << place;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError missing) {
            curiosMissing = true;
        } catch (ReflectiveOperationException | RuntimeException failed) {
            curiosMissing = true;
            LOGGER.warn("[ArmorDon] could not read Curios; accessories are left alone", failed);
        }
    }

    /** A tiny bounded sweep check; the inspection never kicks through a nearby block. */
    private static boolean legRoom(Play play) {
        // The blocks before a standing player do not change by the frame: asked a few times a second.
        if (!(play.notes instanceof Worn worn)) return legRoom(play.player, false);
        long now = System.nanoTime();
        if (now - worn.roomAt > ROOM_EVERY_NANOS) {
            worn.roomAt = now;
            worn.room = legRoom(play.player, false);
        }
        return worn.room;
    }

    private static final long ROOM_EVERY_NANOS = 200_000_000L;

    private static boolean legRoom(AbstractClientPlayer player, boolean right) {
        double yaw = Math.toRadians(player.yBodyRot);
        double side = right ? -.18 : .18;
        var start = player.position().add(Math.cos(yaw) * side, .04, Math.sin(yaw) * side);
        var end = start.add(-Math.sin(yaw) * .38, .22, Math.cos(yaw) * .38);
        return player.level().noCollision(player, new net.minecraft.world.phys.AABB(start, end).inflate(.10, 0, .10));
    }

    /** A push: up from nothing and down to nothing over 0..1. */
    private static float bump(float v) {
        return v <= 0 || v >= 1 ? 0 : (float) Math.sin(Math.PI * v) * (float) Math.sin(Math.PI * v);
    }

    protected boolean ready(AbstractClientPlayer player) {
        return super.ready(player) && !strm.touchnmotion.platform.Platform.swinging(player) && !player.isUsingItem();
    }

    protected double seconds(Play play) {
        // Lower pieces get time for one unhurried inspection; upper pieces keep the accepted fitting gesture.
        return play.kind == FEET || play.kind == LEGS ? InspectionMotion.SECONDS : play.kind == HEAD ? 1.9 : play.kind == BACK || play.kind == CHEST ? 1.7 : 1.4;
    }

    protected void pose(Play play, float phase, Pose out) {
        // Out at once: the piece is on already, the hands must not come after it.
        float in = swell(phase, play.kind == HEAD || play.kind == BACK ? .3f : .2f, .7f, .95f);
        // Settled into place once the hands are there, and how far through that it is.
        float through = Ease.unit((phase - .3f) / .4f), set = (float) Math.sin(Math.PI * through);
        Vector3f right, left = null;
        switch (play.kind) {
            case HEAD -> {
                // One hand flat on top of it, and it takes some doing: a push that seats it, a harder
                // one that drives it home - the head and the shoulders going down under each - and the
                // other arm out a little for it.
                float first = bump((through - .1f) / .3f), second = bump((through - .5f) / .38f), push = .5f * first + second;
                out.onBody = true;
                out.hand(true, new Vector3f(-1.2f, -9.2f + 2.2f * push, -1f), in);
                out.left = new float[]{.12f * in, 0, -.3f * in - .12f * push, 0};
                out.head = new float[]{.08f * in + .2f * push, -.05f * in};
                out.pitch = .07f * push;
                out.roll = -.035f * in - .02f * push;
                return;
            }
            case CHEST -> {
                // From the breast out over the ribs and down the sides, the chest lifting into it; then a shrug to seat it.
                float slide = Ease.smooth(through * 1.4f);
                right = new Vector3f(-2.6f - 2.2f * slide, 4.5f + 4.5f * slide, -3.3f + 1.3f * slide);
                out.pitch = -.07f * set;
                out.roll = .035f * (float) Math.sin(Math.PI * 2 * 2 * through) * set;
                out.head = new float[]{.22f * in * (1 - slide * .5f), 0};
            }
            case LEGS, FEET -> {
                InspectionMotion.Pose inspect = InspectionMotion.at(phase);
                float l = inspect.left(), spread = inspect.spread(), look = inspect.look();
                // The right side bears the weight before the left foot leaves the floor.
                // Eyes arrive first; asymmetric hands counterbalance and settle a beat later.
                out.right = new float[]{.08f * l - .02f * inspect.settle(), 0, .31f * spread + .035f * l, 0};
                out.left = new float[]{-.06f * l, -.035f * l, -.38f * spread, 0};
                out.head = new float[]{.95f * look + .08f * l, -.30f * look};
                out.pitch = .08f * look + .035f * l + .012f * inspect.settle();
                out.yaw = -.04f * look;
                out.roll = -.035f * inspect.support() + .012f * inspect.settle();
                out.weightSide = -.65f * inspect.support();
                out.weightForward = -.10f * l;
                out.apart = phase > .035f && phase < .87f;
                out.rightFoot = new Vector3f(-.8f, 0, 0);
                out.leftFoot = new Vector3f(.8f, 0, 0);
                boolean planted = Body.planted(play.player);
                // Crouch keeps both soles; no lift if the small forward sweep meets a block.
                if (planted && !play.player.isCrouching() && legRoom(play))
                    out.leftLeg = new float[]{-.52f * l, inspect.turnLeft(), .05f * l};
                out.letGo = .97f;
                return;
            }
            case NECK -> {
                // Round the neck from behind to the front, then let hang.
                right = new Vector3f(-2.6f + 1.2f * Ease.smooth(through), .6f + 1.2f * Ease.smooth(through), 1f - 4.6f * Ease.smooth(through * 1.5f));
                out.head = new float[]{.16f * in + .06f * set, 0};
            }
            case WAIST -> {
                // Drawn round the waist to the front and pulled tight.
                float round = Ease.smooth(through * 1.4f);
                right = new Vector3f(-4.4f + 2.6f * round - .5f * set, 11.5f, -.5f - 2.9f * round);
                out.head = new float[]{.24f * in, 0};
                out.pitch = (float) Math.toRadians(7) * in - .04f * set;
            }
            case BACK -> {
                right = new Vector3f(-4.4f, .5f + 1.5f * set, 1.8f);
                out.roll = .03f * (float) Math.sin(Math.PI * 2 * 2 * through) * set;
            }
            default -> {
                // A ring or a bracelet: the right hand held out and turned to the eyes, the left hand to it, a twist on.
                out.onBody = true;
                out.hand(true, new Vector3f(-1.2f, 9f - .6f * set, -6.5f), in);
                out.hand(false, new Vector3f(-2.2f + .7f * (float) Math.sin(Math.PI * 2 * through) * set, 8.2f, -6.9f), in * Math.min(1, phase / .16f));
                out.head = new float[]{.3f * in, .1f * in};
                out.pitch = .04f * in;
                return;
            }
        }
        out.onBody = true;
        out.hand(true, right, in);
        out.hand(false, left != null ? left : Reach.mirror(right), in * Ease.smooth(phase / .12f));
    }
}
