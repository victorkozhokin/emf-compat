package strm.emfcompat.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        BooleanOption(String key, String label, boolean defaultValue,
                      String onText, String onTooltip, String offText, String offTooltip) {
            this.key = key;
            this.label = label;
            this.defaultValue = defaultValue;
            this.onText = onText;
            this.onTooltip = onTooltip;
            this.offText = offText;
            this.offTooltip = offTooltip;
        }
    }

    /** A tab's worth of options for one mod. */
    public static final class Section {
        public final String id;
        public final String title;
        public final List<BooleanOption> booleans = new CopyOnWriteArrayList<>();

        Section(String id, String title) {
            this.id = id;
            this.title = title;
        }

        /** Registers a boolean option in this section and returns the section for chaining. */
        public Section addBoolean(String key, String label, boolean defaultValue,
                                  String onText, String onTooltip, String offText, String offTooltip) {
            booleans.add(new BooleanOption(key, label, defaultValue, onText, onTooltip, offText, offTooltip));
            return this;
        }
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
