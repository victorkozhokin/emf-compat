package strm.emfcompat.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRowsTest {

    private final Map<String, Boolean> stored = new HashMap<>();
    private final ConfigRows.Values values = (key, def) -> stored.getOrDefault(key, def);

    private static ConfigRegistry.Group add(ConfigRegistry.Group group, String key) {
        return group.addBoolean(key, key, true, "On", "", "Off", "");
    }

    private static ConfigRegistry.Group child(ConfigRegistry.Group group, String parent, String key) {
        return group.addChild(parent, key, key, true, "On", "", "Off", "");
    }

    /** Headers as {@code [title]}, options as their key behind one dot per level. */
    private static List<String> lines(ConfigRegistry.Section section, boolean foldAll) {
        return ConfigRows.build(section, g -> foldAll || g.isCollapsedByDefault()).stream()
                .map(r -> r.isHeader() ? "[" + r.group.title + "]" : ".".repeat(r.depth) + r.option.key)
                .collect(Collectors.toList());
    }

    @Test
    void plainSectionListsItsOptionsWithoutAHeader() {
        ConfigRegistry.Section s = ConfigRegistry.section("t1", "T1")
                .addBoolean("t1.enabled", "a", true, "On", "", "Off", "")
                .addBoolean("t1.b", "b", true, "On", "", "Off", "");
        assertEquals(List.of("t1.enabled", "t1.b"), lines(s, true));
        assertNull(ConfigRegistry.gateOf("t1.b"));
    }

    @Test
    void childrenFollowTheirParentWhateverTheRegistrationOrder() {
        ConfigRegistry.Section s = ConfigRegistry.section("t2", "T2");
        ConfigRegistry.Group g = s.group("g", "G");
        add(g, "t2.a");
        add(g, "t2.b");
        child(g, "t2.a", "t2.a1");
        child(g, "t2.a1", "t2.a1x");
        assertEquals(List.of("[G]", "t2.a", ".t2.a1", "..t2.a1x", "t2.b"), lines(s, false));
    }

    @Test
    void foldedGroupKeepsOnlyItsHeader() {
        ConfigRegistry.Section s = ConfigRegistry.section("t3", "T3");
        add(s.group("g", "G"), "t3.a");
        add(s.group("d", "D").collapsedByDefault(), "t3.d");
        assertEquals(List.of("[G]", "t3.a", "[D]"), lines(s, false));
        assertEquals(List.of("[G]", "[D]"), lines(s, true));
    }

    @Test
    void masterIsNotARowAndGatesEveryOption() {
        ConfigRegistry.Section s = ConfigRegistry.section("t4", "T4");
        ConfigRegistry.Group g = s.group("g", "G");
        add(g, "t4.a");
        s.master("t4.enabled", "all", true, "On", "", "Off", "");
        add(g, "t4.b");
        child(g, "t4.b", "t4.b1");
        assertEquals(List.of("[G]", "t4.a", "t4.b", ".t4.b1"), lines(s, false));
        assertEquals("t4.enabled", ConfigRegistry.gateOf("t4.a"));
        assertEquals("t4.enabled", ConfigRegistry.gateOf("t4.b"));
        assertEquals("t4.b", ConfigRegistry.gateOf("t4.b1"));
        assertNull(ConfigRegistry.gateOf("t4.enabled"));
        assertEquals("t4.enabled", s.booleans.get(0).key);
    }

    @Test
    void lockedByParentGrandparentOrMaster() {
        ConfigRegistry.Section s = ConfigRegistry.section("t5", "T5");
        s.master("t5.enabled", "all", true, "On", "", "Off", "");
        ConfigRegistry.Group g = s.group("g", "G");
        add(g, "t5.a");
        child(g, "t5.a", "t5.a1");
        child(g, "t5.a1", "t5.a1x");
        ConfigRegistry.BooleanOption a = s.booleans.get(1), a1 = s.booleans.get(2), a1x = s.booleans.get(3);
        assertFalse(ConfigRows.locked(s, a1x, values));
        stored.put("t5.a", false);
        assertFalse(ConfigRows.locked(s, a, values));
        assertTrue(ConfigRows.locked(s, a1, values));
        assertTrue(ConfigRows.locked(s, a1x, values));
        stored.put("t5.a", true);
        stored.put("t5.enabled", false);
        assertTrue(ConfigRows.locked(s, a, values));
        assertFalse(ConfigRows.locked(s, s.master(), values));
    }

    @Test
    void countsAndModifiedReadStoredValues() {
        ConfigRegistry.Section s = ConfigRegistry.section("t6", "T6");
        ConfigRegistry.Group g = s.group("g", "G");
        add(g, "t6.a");
        child(g, "t6.a", "t6.a1");
        g.addBoolean("t6.off", "off", false, "On", "", "Off", "");
        assertEquals(2, ConfigRows.enabledCount(s, g, values));
        assertEquals(3, ConfigRows.optionsOf(s, g).size());
        assertFalse(ConfigRows.anyModified(s, values));
        stored.put("t6.off", true);
        assertEquals(3, ConfigRows.enabledCount(s, g, values));
        assertTrue(ConfigRows.modified(s.booleans.get(2), values));
        assertTrue(ConfigRows.anyModified(s, values));
        assertFalse(ConfigRegistry.defaultOf("t6.off", true));
    }

    @Test
    void anOptionCannotBeItsOwnParent() {
        ConfigRegistry.Group g = ConfigRegistry.section("t7", "T7").group("g", "G");
        assertThrows(IllegalArgumentException.class, () -> child(g, "t7.a", "t7.a"));
    }
}
