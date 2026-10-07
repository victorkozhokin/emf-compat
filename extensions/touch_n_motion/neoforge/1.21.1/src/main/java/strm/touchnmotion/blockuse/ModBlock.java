package strm.touchnmotion.blockuse;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A block of an optional mod, or a family of them, known without the mod's classes: first by the
 * id it is registered under - {@code create:hand_crank}, which a mod keeps from version to version
 * - and failing that by the name of its class, which also takes in what another mod builds on it.
 * An id may hold one {@code *} for a family told apart by colour: {@code create:*_valve_handle}.
 * The answer is kept for each block.
 */
public final class ModBlock {

    private enum By {SAME, EXTENDS, STARTS}

    private final By by;
    private final String className;
    private final String[] ids;
    private final Map<Block, Boolean> known = new IdentityHashMap<>();

    private ModBlock(By by, String className, String[] ids) {
        this.by = by;
        this.className = className;
        this.ids = ids;
    }

    /** These ids, or exactly this class - not what extends it, which is another block with a target of its own. */
    public static ModBlock exact(String className, String... ids) {
        return new ModBlock(By.SAME, className, ids);
    }

    /** These ids, or this class and whatever extends or implements it. */
    public static ModBlock family(String className, String... ids) {
        return new ModBlock(By.EXTENDS, className, ids);
    }

    /** These ids, or any class whose name starts so. */
    public static ModBlock named(String classPrefix, String... ids) {
        return new ModBlock(By.STARTS, classPrefix, ids);
    }

    public boolean is(BlockState state) {
        return is(state.getBlock());
    }

    public boolean is(Block block) {
        Boolean answer = known.get(block);
        if (answer == null) known.put(block, answer = look(block));
        return answer;
    }

    private boolean look(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        for (String each : ids) if (like(each, id)) return true;
        String name = block.getClass().getName();
        return switch (by) {
            case SAME -> name.equals(className);
            case EXTENDS -> ModAccess.is(block.getClass(), className);
            case STARTS -> name.startsWith(className);
        };
    }

    static boolean like(String pattern, String id) {
        int star = pattern.indexOf('*');
        if (star < 0) return pattern.equals(id);
        return id.length() >= pattern.length() - 1 && id.startsWith(pattern.substring(0, star)) && id.endsWith(pattern.substring(star + 1));
    }
}
