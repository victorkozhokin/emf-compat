package strm.emfcompat.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What the config screen lists for one section, worked out without any GUI class so every
 * loader's screen draws the same thing: group headers, the options under them, children indented
 * under their parent. Also answers the questions a row asks while it is drawn - is the option
 * held off by its parent or the master, was it changed, how many of a group are on.
 */
public final class ConfigRows {

    /** Reads an option's stored value; the screen passes {@link EMFCompatConfig#getBooleanRaw}. */
    public interface Values {
        boolean raw(String key, boolean defaultValue);
    }

    /** One line of the list: a group's header ({@code option == null}) or an option. */
    public static final class Row {
        public final ConfigRegistry.Group group;
        public final ConfigRegistry.BooleanOption option;
        /** 0 for a top-level option, 1 for its child, and so on. */
        public final int depth;

        Row(ConfigRegistry.Group group, ConfigRegistry.BooleanOption option, int depth) {
            this.group = group;
            this.option = option;
            this.depth = depth;
        }

        public boolean isHeader() {
            return option == null;
        }
    }

    private ConfigRows() {
    }

    /**
     * The section's rows top to bottom. The master option is left out (the screen shows it above
     * the list), and so are the options of a group {@code collapsed} says is folded.
     */
    public static List<Row> build(ConfigRegistry.Section section, Predicate<ConfigRegistry.Group> collapsed) {
        List<Row> rows = new ArrayList<>();
        for (ConfigRegistry.Group group : section.groups) {
            List<ConfigRegistry.BooleanOption> options = optionsOf(section, group);
            if (options.isEmpty()) {
                continue;
            }
            if (group.title != null) {
                rows.add(new Row(group, null, 0));
                if (collapsed.test(group)) {
                    continue;
                }
            }
            for (ConfigRegistry.BooleanOption opt : options) {
                if (!hasParentIn(opt, options)) {
                    addWithChildren(rows, group, opt, options, 0);
                }
            }
        }
        return rows;
    }

    /** The options listed under {@code group}, children included, without the master. */
    public static List<ConfigRegistry.BooleanOption> optionsOf(ConfigRegistry.Section section,
                                                              ConfigRegistry.Group group) {
        List<ConfigRegistry.BooleanOption> options = new ArrayList<>();
        for (ConfigRegistry.BooleanOption opt : section.booleans) {
            if (opt != section.master() && opt.group.equals(group.id)) {
                options.add(opt);
            }
        }
        return options;
    }

    /** How many of the group's options are stored as on. */
    public static int enabledCount(ConfigRegistry.Section section, ConfigRegistry.Group group, Values values) {
        int on = 0;
        for (ConfigRegistry.BooleanOption opt : optionsOf(section, group)) {
            if (values.raw(opt.key, opt.defaultValue)) {
                on++;
            }
        }
        return on;
    }

    /**
     * Whether the option does nothing right now whatever it is set to, because its parent (or
     * the parent's parent, or the section's master) is off.
     */
    public static boolean locked(ConfigRegistry.Section section, ConfigRegistry.BooleanOption opt, Values values) {
        ConfigRegistry.BooleanOption master = section.master();
        if (master != null && opt != master && !values.raw(master.key, master.defaultValue)) {
            return true;
        }
        // Bounded by the option count, so a parent cycle cannot hang the screen.
        ConfigRegistry.BooleanOption at = opt;
        for (int i = 0; i < section.booleans.size() && at.parent != null; i++) {
            ConfigRegistry.BooleanOption parent = find(section, at.parent);
            if (parent == null) {
                return false;
            }
            if (!values.raw(parent.key, parent.defaultValue)) {
                return true;
            }
            at = parent;
        }
        return false;
    }

    /** Whether the option is stored as something other than its default. */
    public static boolean modified(ConfigRegistry.BooleanOption opt, Values values) {
        return values.raw(opt.key, opt.defaultValue) != opt.defaultValue;
    }

    /** Whether any option of the section, the master included, differs from its default. */
    public static boolean anyModified(ConfigRegistry.Section section, Values values) {
        for (ConfigRegistry.BooleanOption opt : section.booleans) {
            if (modified(opt, values)) {
                return true;
            }
        }
        return false;
    }

    private static void addWithChildren(List<Row> rows, ConfigRegistry.Group group, ConfigRegistry.BooleanOption opt,
                                        List<ConfigRegistry.BooleanOption> options, int depth) {
        rows.add(new Row(group, opt, depth));
        if (depth >= options.size()) {
            return;
        }
        for (ConfigRegistry.BooleanOption child : options) {
            if (opt.key.equals(child.parent)) {
                addWithChildren(rows, group, child, options, depth + 1);
            }
        }
    }

    /** A child whose parent sits in another group is listed as a top-level option of its own. */
    private static boolean hasParentIn(ConfigRegistry.BooleanOption opt, List<ConfigRegistry.BooleanOption> options) {
        if (opt.parent == null) {
            return false;
        }
        for (ConfigRegistry.BooleanOption other : options) {
            if (other.key.equals(opt.parent)) {
                return true;
            }
        }
        return false;
    }

    private static ConfigRegistry.BooleanOption find(ConfigRegistry.Section section, String key) {
        for (ConfigRegistry.BooleanOption opt : section.booleans) {
            if (opt.key.equals(key)) {
                return opt;
            }
        }
        return null;
    }
}
