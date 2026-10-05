package strm.emfcompat.animationadditions.gesture;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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
        final Map<String, String> curios = new HashMap<>();
        long since, curiosAt;
        int queued;
        boolean curiosKnown;
    }

    public String id() {
        return "ArmorDon";
    }

    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Put on armour and accessories", true,
                "On", "A piece of armour or a Curios accessory put on takes both hands to where it is worn.",
                "Off", "It just appears.");
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
            if (item != worn.armour[i] && item != Items.AIR && settled) worn.queued |= 1 << i;
            worn.armour[i] = item;
        }
        if (now - worn.curiosAt > CURIOS_EVERY_NANOS) {
            worn.curiosAt = now;
            curios(player, worn, settled && worn.curiosKnown);
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
                StringBuilder now = new StringBuilder();
                int count = (int) slotsOf.invoke(stacks);
                for (int i = 0; i < count; i++) {
                    ItemStack stack = (ItemStack) stackIn.invoke(stacks, i);
                    now.append(stack.isEmpty() ? "-" : stack.getItem().toString()).append(';');
                }
                String was = worn.curios.put(id, now.toString());
                Integer place = CURIOS.get(id);
                // New in the slot, and something rather than nothing: taking one off is not putting one on.
                if (queue && place != null && was != null && !was.equals(now.toString())
                        && now.toString().replace("-;", "").length() > was.replace("-;", "").length()) worn.queued |= 1 << place;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError missing) {
            curiosMissing = true;
        } catch (ReflectiveOperationException | RuntimeException failed) {
            curiosMissing = true;
            LOGGER.warn("[ArmorDon] could not read Curios; accessories are left alone", failed);
        }
    }

    protected boolean ready(AbstractClientPlayer player) {
        return super.ready(player) && !player.swinging && !player.isUsingItem();
    }

    protected double seconds(Play play) {
        // Up to the head and down to the feet are long ways for an arm: more time, the same pace.
        return play.kind == FEET || play.kind == HEAD || play.kind == BACK ? 1.65 : 1.3;
    }

    protected void pose(Play play, float phase, Pose out) {
        float in = bell(phase, .3f, .64f, .93f);
        // Settled into place once the hands are there: a short push, a pull or a tug.
        float set = phase > .36f && phase < .62f ? (float) Math.sin(Math.PI * (phase - .36f) / .26f) : 0;
        Vector3f at;
        switch (play.kind) {
            case HEAD -> {
                at = new Vector3f(-4.6f, -5.5f + set, -1.5f);
                out.head = new float[]{.12f * in, 0};
            }
            case CHEST -> at = new Vector3f(-3f - set, 5.5f, -3.2f);
            case LEGS -> {
                at = new Vector3f(-4.6f, 12.5f - 1.5f * set, -1.5f);
                out.pitch = (float) Math.toRadians(17) * in;
                out.head = new float[]{.2f * in, 0};
            }
            case FEET -> {
                at = new Vector3f(-2.4f, 21f - set, -4f);
                out.pitch = (float) Math.toRadians(36) * in;
                out.head = new float[]{.3f * in, 0};
                out.apart = phase > .05f && phase < .72f;
                out.rightFoot = new Vector3f(-.6f, 0, .5f);
                out.leftFoot = new Vector3f(.6f, 0, -1f);
            }
            case NECK -> {
                at = new Vector3f(-1.7f, 1.3f + .5f * set, -3.4f);
                out.head = new float[]{.14f * in, 0};
            }
            case WAIST -> {
                at = new Vector3f(-2.3f - .6f * set, 11.5f, -3.2f);
                out.head = new float[]{.22f * in, 0};
                out.pitch = (float) Math.toRadians(6) * in;
            }
            case BACK -> at = new Vector3f(-4.4f, 1f + set, 1.5f);
            default -> {
                // A ring or a bracelet: the right hand held out, the left hand to it.
                out.onBody = true;
                out.hand(true, new Vector3f(-1.2f, 9f, -6.5f), in);
                out.hand(false, new Vector3f(-2.2f + .5f * set, 8.4f, -6.9f), in);
                out.head = new float[]{.25f * in, .1f * in};
                return;
            }
        }
        out.onBody = true;
        out.hand(true, at, in);
        out.hand(false, Reach.mirror(at), in);
    }
}
