package strm.emfcompat.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of configuration sections, one per compat mod, that the config screen renders as
 * tabs. The core registers a {@code "core"} section (shown first, selected by default); each
 * addon registers its own section and options at mod construction, keeping the core decoupled
 * from addon-specific settings. Values live in {@link EMFCompatConfig}, keyed by option id.
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
        public final List<BooleanOption> booleans = new ArrayList<>();

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

    private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();

    private ConfigRegistry() {
    }

    /** Returns the section for {@code id}, creating it with {@code title} on first use. */
    public static Section section(String id, String title) {
        return SECTIONS.computeIfAbsent(id, k -> new Section(id, title));
    }

    public static Section get(String id) {
        return SECTIONS.get(id);
    }

    /** All sections with the core section first, then the rest in registration order. */
    public static Collection<Section> orderedSections() {
        List<Section> ordered = new ArrayList<>();
        Section core = SECTIONS.get(CORE_ID);
        if (core != null) {
            ordered.add(core);
        }
        for (Section s : SECTIONS.values()) {
            if (!s.id.equals(CORE_ID)) {
                ordered.add(s);
            }
        }
        return ordered;
    }
}
