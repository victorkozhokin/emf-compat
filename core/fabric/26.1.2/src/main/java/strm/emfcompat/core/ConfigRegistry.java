package strm.emfcompat.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of configuration sections, one per compat mod, that the config screen renders as
 * tabs. The core registers a {@code "core"} section (shown first, selected by default); each
 * addon registers its own section and options at mod construction, keeping the core decoupled
 * from addon-specific settings. Values live in {@link EMFCompatConfig}, keyed by option id.
 *
 * <p><b>Naming rule:</b> an addon's master on/off option must be named {@code <addon>.enabled}
 * and must gate everything that addon does. The core's global switch works by forcing every key
 * with that suffix to read as off, so an addon whose master option is named differently would
 * keep running after the user turns compatibility off. See {@link EMFCompatConfig#getBoolean}.</p>
 *
 * <p><b>Larger sections</b> can sort their options into {@link Group}s (collapsible on the
 * screen), hang an option under the one it refines with {@link Group#addChild}, and name a
 * {@link Section#master master} option that switches the whole section. A child reads as off
 * while its parent is off, and every option of a section reads as off while its master is off -
 * {@link EMFCompatConfig#getBoolean} applies that, so features need no checks of their own. The
 * stored values are kept either way.</p>
 */
public final class ConfigRegistry {

    /** Id of the core section, always rendered first and selected by default. */
    public static final String CORE_ID = "core";

    /** A single boolean option: an on/off choice with per-state label text and tooltip. */
    public static final class BooleanOption {
        public final String key;
        public final String label;
        public final boolean defaultValue;
        public final String onText;
        public final String onTooltip;
        public final String offText;
        public final String offTooltip;
        /** Id of the {@link Group} the option is listed under; {@code ""} for the unnamed one. */
        public final String group;
        /** Key of the option this one refines, or {@code null} for a top-level option. */
        public final String parent;

        BooleanOption(String key, String label, boolean defaultValue,
                      String onText, String onTooltip, String offText, String offTooltip,
                      String group, String parent) {
            this.key = key;
            this.label = label;
            this.defaultValue = defaultValue;
            this.onText = onText;
            this.onTooltip = onTooltip;
            this.offText = offText;
            this.offTooltip = offTooltip;
            this.group = group;
            this.parent = parent;
        }
    }

    /** A titled, collapsible run of options inside a section. */
    public static final class Group {
        public final String id;
        /** Shown as the group's header; {@code null} for the unnamed group, which has none. */
        public final String title;
        private final Section section;
        private volatile boolean collapsedByDefault;

        Group(Section section, String id, String title) {
            this.section = section;
            this.id = id;
            this.title = title;
        }

        /** Registers a top-level option in this group and returns the group for chaining. */
        public Group addBoolean(String key, String label, boolean defaultValue,
                                String onText, String onTooltip, String offText, String offTooltip) {
            section.add(new BooleanOption(key, label, defaultValue, onText, onTooltip, offText, offTooltip, id, null));
            return this;
        }

        /**
         * Registers an option that refines {@code parentKey}: it is listed indented under its
         * parent and reads as off while the parent is off.
         */
        public Group addChild(String parentKey, String key, String label, boolean defaultValue,
                              String onText, String onTooltip, String offText, String offTooltip) {
            if (parentKey == null || parentKey.equals(key)) {
                throw new IllegalArgumentException("Option " + key + " needs a parent other than itself");
            }
            section.add(new BooleanOption(key, label, defaultValue, onText, onTooltip, offText, offTooltip, id, parentKey));
            return this;
        }

        /** The group starts folded each session - for options most players never need. */
        public Group collapsedByDefault() {
            collapsedByDefault = true;
            return this;
        }

        public boolean isCollapsedByDefault() {
            return collapsedByDefault;
        }
    }

    /** A tab's worth of options for one mod. */
    public static final class Section {
        public final String id;
        public final String title;
        /** Every option of the section, the master included, in registration order. */
        public final List<BooleanOption> booleans = new CopyOnWriteArrayList<>();
        /** The groups in the order they were first asked for; the unnamed one only once used. */
        public final List<Group> groups = new CopyOnWriteArrayList<>();
        private volatile BooleanOption master;

        Section(String id, String title) {
            this.id = id;
            this.title = title;
        }

        /** Registers a boolean option in the unnamed group and returns the section for chaining. */
        public Section addBoolean(String key, String label, boolean defaultValue,
                                  String onText, String onTooltip, String offText, String offTooltip) {
            group(DEFAULT_GROUP, null).addBoolean(key, label, defaultValue, onText, onTooltip, offText, offTooltip);
            return this;
        }

        /** Returns the group for {@code groupId}, creating it with {@code groupTitle} on first use. */
        public Group group(String groupId, String groupTitle) {
            synchronized (this) {
                for (Group g : groups) {
                    if (g.id.equals(groupId)) {
                        return g;
                    }
                }
                Group g = new Group(this, groupId, groupTitle);
                groups.add(g);
                return g;
            }
        }

        /**
         * Registers the option that switches the whole section: while it is off every other
         * option of the section reads as off. The screen shows it above the list. Name it
         * {@code <addon>.enabled} so the core's global switch covers it too.
         */
        public Section master(String key, String label, boolean defaultValue,
                              String onText, String onTooltip, String offText, String offTooltip) {
            synchronized (this) {
                master = new BooleanOption(key, label, defaultValue, onText, onTooltip, offText, offTooltip, DEFAULT_GROUP, null);
                DEFAULTS.put(key, defaultValue);
                booleans.add(0, master);
                for (BooleanOption opt : booleans) {
                    gate(opt);
                }
            }
            return this;
        }

        /** The section's master option, or {@code null} when it has none. */
        public BooleanOption master() {
            return master;
        }

        void add(BooleanOption opt) {
            synchronized (this) {
                DEFAULTS.put(opt.key, opt.defaultValue);
                booleans.add(opt);
                gate(opt);
            }
        }

        private void gate(BooleanOption opt) {
            BooleanOption m = master;
            if (opt.parent != null) {
                GATES.put(opt.key, opt.parent);
            } else if (m != null && m != opt) {
                GATES.put(opt.key, m.key);
            }
        }
    }

    /** Id of the unnamed group that {@link Section#addBoolean} fills. */
    public static final String DEFAULT_GROUP = "";

    /** Option key -> the key that must be on for it to read as on (its parent, else the master). */
    private static final Map<String, String> GATES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> DEFAULTS = new ConcurrentHashMap<>();

    /** The option that must be on for {@code key} to read as on, or {@code null}. */
    public static String gateOf(String key) {
        return GATES.get(key);
    }

    /** The registered default of {@code key}; {@code fallback} for a key nobody registered. */
    public static boolean defaultOf(String key, boolean fallback) {
        Boolean v = DEFAULTS.get(key);
        return v != null ? v : fallback;
    }

    // Forge and NeoForge construct mods in parallel on a ForkJoinPool, and every addon registers
    // its section from its constructor — so this map is written from several threads at once. A
    // plain LinkedHashMap threw ConcurrentModificationException inside computeIfAbsent once enough
    // addons were installed to collide. The lock keeps registration atomic while preserving
    // registration order, which the tab list depends on.
    private static final Object LOCK = new Object();

    private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();

    private ConfigRegistry() {
    }

    /** Returns the section for {@code id}, creating it with {@code title} on first use. */
    public static Section section(String id, String title) {
        synchronized (LOCK) {
            return SECTIONS.computeIfAbsent(id, k -> new Section(id, title));
        }
    }

    public static Section get(String id) {
        synchronized (LOCK) {
            return SECTIONS.get(id);
        }
    }

    /** All sections with the core section first, then the rest in registration order. */
    public static Collection<Section> orderedSections() {
        List<Section> ordered = new ArrayList<>();
        synchronized (LOCK) {
            Section core = SECTIONS.get(CORE_ID);
            if (core != null) {
                ordered.add(core);
            }
            for (Section s : SECTIONS.values()) {
                if (!s.id.equals(CORE_ID)) {
                    ordered.add(s);
                }
            }
        }
        return ordered;
    }
}
