package strm.emfcompat.core.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.ConfigRows;
import strm.emfcompat.core.EMFCompatConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabbed configuration screen. A column of tabs on the left selects which compat mod to
 * configure (Core first, selected by default); the panel on the right lists that mod's options
 * in a scrolling list, under collapsible group headers where the mod declared groups, with
 * child options indented under their parent. Above the list sit the mod's master switch (if it
 * has one) and a reset button; below it, the description of the option under the cursor. Tabs
 * and options come from {@link ConfigRegistry}, the rows from {@link ConfigRows}.
 */
public class ConfigScreen extends Screen {

    private static final int TAB_WIDTH = 100;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int PANEL_TOP = 40;
    /** Room under the list for the description of the hovered option. */
    private static final int DESCRIPTION_LINES = 2;
    private static final int INDENT = 12;
    private static final int RESET_WIDTH = 50;
    private static final int MASTER_WIDTH = 170;

    private static final ConfigRows.Values RAW = EMFCompatConfig::getBooleanRaw;

    /** Which groups the player folded or opened, kept while the game runs. Key: section/group. */
    private static final Map<String, Boolean> COLLAPSED = new HashMap<>();

    private final Screen parent;
    private String selectedSectionId = ConfigRegistry.CORE_ID;

    /** How many tabs are scrolled off the top of the column, and the current max. */
    private int tabScroll = 0;
    private int tabMaxScroll = 0;

    private OptionList list;
    private Button resetButton;
    /** Set by a click inside the list; the rows are rebuilt before the next frame, not under it. */
    private boolean rowsDirty;

    public ConfigScreen(Screen parent) {
        super(Component.literal("EMF Compat"));
        this.parent = parent;
    }

    /** Opened on one mod's tab: a mod with a section of its own opens the screen from its entry in the mod list. */
    public ConfigScreen(Screen parent, String sectionId) {
        this(parent);
        if (sectionId != null && ConfigRegistry.get(sectionId) != null) {
            selectedSectionId = sectionId;
        }
    }

    @Override
    protected void init() {
        int tabX = 12;
        int rowH = TAB_HEIGHT + TAB_GAP;
        // Keep tabs between the header and the Done button; scroll the rest.
        int tabTop = PANEL_TOP;
        int tabBottom = this.height - 40;
        int maxVisible = Math.max(1, (tabBottom - tabTop + TAB_GAP) / rowH);

        List<ConfigRegistry.Section> sections = new ArrayList<>(ConfigRegistry.orderedSections());
        tabMaxScroll = Math.max(0, sections.size() - maxVisible);
        tabScroll = Mth.clamp(tabScroll, 0, tabMaxScroll);

        int tabY = tabTop;
        int end = Math.min(sections.size(), tabScroll + maxVisible);
        for (int i = tabScroll; i < end; i++) {
            ConfigRegistry.Section section = sections.get(i);
            Button tab = Button.builder(Component.literal(section.title), b -> {
                        selectedSectionId = section.id;
                        list = null;
                        rebuildWidgets();
                    })
                    .bounds(tabX, tabY, TAB_WIDTH, TAB_HEIGHT)
                    .build();
            // The current tab is shown inactive (greyed) so it reads as selected.
            tab.active = !section.id.equals(selectedSectionId);
            addRenderableWidget(tab);
            tabY += rowH;
        }

        int optX = tabX + TAB_WIDTH + 16;
        int optW = this.width - optX - 16;
        resetButton = null;
        ConfigRegistry.Section selected = ConfigRegistry.get(selectedSectionId);
        if (selected != null && !selected.booleans.isEmpty()) {
            int headerY = PANEL_TOP - ROW_HEIGHT - ROW_GAP;
            resetButton = Button.builder(Component.literal("Reset"), b -> resetSection(selected))
                    .bounds(optX + optW - RESET_WIDTH, headerY, RESET_WIDTH, ROW_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Set every option of this tab back to its default.")))
                    .build();
            addRenderableWidget(resetButton);

            ConfigRegistry.BooleanOption master = selected.master();
            if (master != null) {
                int masterW = Math.min(MASTER_WIDTH, optW - RESET_WIDTH - ROW_GAP);
                addRenderableWidget(CycleButton.<Boolean>builder(
                                v -> Component.literal(v ? master.onText : master.offText))
                        .withValues(Boolean.TRUE, Boolean.FALSE)
                        .withInitialValue(EMFCompatConfig.getBooleanRaw(master.key, master.defaultValue))
                        .withTooltip(v -> Tooltip.create(Component.literal(v ? master.onTooltip : master.offTooltip)))
                        .create(optX + optW - RESET_WIDTH - ROW_GAP - masterW, headerY, masterW, ROW_HEIGHT,
                                Component.literal(master.label),
                                (btn, value) -> EMFCompatConfig.setBoolean(master.key, value)));
            }

            int listBottom = this.height - 36 - DESCRIPTION_LINES * (this.font.lineHeight + 1) - 4;
            double scroll = list != null ? list.getScrollAmount() : 0;
            list = new OptionList(this.minecraft, optW, Math.max(ROW_HEIGHT, listBottom - PANEL_TOP), PANEL_TOP);
            list.setLeftPos(optX);
            list.fill(selected);
            list.setScrollAmount(scroll);
            addRenderableWidget(list);
        } else {
            list = null;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, ROW_HEIGHT)
                .build());
    }

    private void resetSection(ConfigRegistry.Section section) {
        for (ConfigRegistry.BooleanOption opt : section.booleans) {
            EMFCompatConfig.setBoolean(opt.key, opt.defaultValue);
            if (opt instanceof ConfigRegistry.ChoiceOption choice) {
                EMFCompatConfig.setNumber(choice.key, choice.defaultNumber);
            }
        }
        rebuildWidgets();
    }

    private static boolean isCollapsed(ConfigRegistry.Section section, ConfigRegistry.Group group) {
        Boolean stored = COLLAPSED.get(section.id + "/" + group.id);
        return stored != null ? stored : group.isCollapsedByDefault();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        // Scroll the tab column while the cursor is over it.
        if (tabMaxScroll > 0 && mouseX < 12 + TAB_WIDTH + 8) {
            int updated = Mth.clamp(tabScroll - (int) Math.signum(scrollY), 0, tabMaxScroll);
            if (updated != tabScroll) {
                tabScroll = updated;
                rebuildWidgets();
            }
            return true;
        }
        // Anywhere else, scroll the options - also from beside or below the list.
        if (list != null && !list.isMouseOver(mouseX, mouseY)) {
            return list.mouseScrolled(mouseX, mouseY, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ConfigRegistry.Section selected = ConfigRegistry.get(selectedSectionId);
        if (rowsDirty && list != null && selected != null) {
            rowsDirty = false;
            double scroll = list.getScrollAmount();
            list.fill(selected);
            list.setScrollAmount(scroll);
        }
        if (resetButton != null && selected != null) {
            resetButton.active = ConfigRows.anyModified(selected, RAW);
        }

        // 1.20.1: a screen darkens what is behind it only when asked.
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, 12, 16, 0xFFFFFF);

        // Arrows hint that the tab column scrolls (mouse wheel over the tabs).
        if (tabMaxScroll > 0) {
            int cx = 12 + TAB_WIDTH / 2;
            if (tabScroll > 0) {
                graphics.drawCenteredString(this.font, Component.literal("▲"), cx, PANEL_TOP - 10, 0xFFFFFF);
            }
            if (tabScroll < tabMaxScroll) {
                graphics.drawCenteredString(this.font, Component.literal("▼"), cx, this.height - 38, 0xFFFFFF);
            }
        }

        int optX = 12 + TAB_WIDTH + 16;
        if (selected != null) {
            graphics.drawString(this.font, Component.literal(selected.title), optX, 16, 0xFFFFFF);
            if (selected.booleans.isEmpty()) {
                graphics.drawString(this.font, Component.literal("No options yet."), optX, PANEL_TOP + 4, 0xA0A0A0);
            }
        }

        if (list != null && selected != null) {
            ConfigRegistry.BooleanOption described = list.described(mouseX, mouseY);
            if (described != null) {
                boolean on = EMFCompatConfig.getBooleanRaw(described.key, described.defaultValue);
                String text = on ? described.onTooltip : described.offTooltip;
                if (ConfigRows.locked(selected, described, RAW)) {
                    text = "Has no effect while " + lockedBy(selected, described) + " is off. " + text;
                }
                List<FormattedCharSequence> lines = this.font.split(Component.literal(text), this.width - optX - 16);
                int y = list.getBottom() + 4;
                for (int i = 0; i < Math.min(lines.size(), DESCRIPTION_LINES); i++) {
                    graphics.drawString(this.font, lines.get(i), optX, y, 0xFFFFFF);
                    y += this.font.lineHeight + 1;
                }
            }
        }
    }

    /** The label of whatever holds {@code opt} off: its parent if that is off, else the master. */
    private static String lockedBy(ConfigRegistry.Section section, ConfigRegistry.BooleanOption opt) {
        if (opt.parent != null) {
            for (ConfigRegistry.BooleanOption other : section.booleans) {
                if (other.key.equals(opt.parent)
                        && !EMFCompatConfig.getBooleanRaw(other.key, other.defaultValue)) {
                    return "\"" + other.label + "\"";
                }
            }
        }
        ConfigRegistry.BooleanOption master = section.master();
        return master != null ? "\"" + master.label + "\"" : "its parent";
    }

    @Override
    public void onClose() {
        EMFCompatConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    /** The scrolling list of one section's rows. */
    private final class OptionList extends ContainerObjectSelectionList<RowEntry> {

        OptionList(Minecraft minecraft, int width, int height, int y) {
            // 1.20.1: the list is told the screen's height and its own top and bottom, and draws
            // a dirt background and edge bars unless told not to.
            super(minecraft, width, ConfigScreen.this.height, y, y + height, ROW_HEIGHT + ROW_GAP);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
        }

        void fill(ConfigRegistry.Section section) {
            int valueWidth = 36;
            for (ConfigRegistry.BooleanOption opt : section.booleans) {
                valueWidth = Math.max(valueWidth, Math.max(font.width(opt.onText), font.width(opt.offText)) + 12);
                if (opt instanceof ConfigRegistry.ChoiceOption choice) {
                    for (String text : choice.texts) {
                        valueWidth = Math.max(valueWidth, font.width(text) + 12);
                    }
                }
            }
            List<RowEntry> entries = new ArrayList<>();
            for (ConfigRows.Row row : ConfigRows.build(section, g -> isCollapsed(section, g))) {
                entries.add(row.isHeader() ? new HeaderEntry(section, row.group)
                        : new OptionEntry(section, row, valueWidth));
            }
            replaceEntries(entries);
        }

        /** The option to describe under the list: the one under the cursor, else the focused one. */
        ConfigRegistry.BooleanOption described(int mouseX, int mouseY) {
            RowEntry entry = isMouseOver(mouseX, mouseY) ? getEntryAtPosition(mouseX, mouseY) : null;
            if (entry == null) {
                entry = getFocused();
            }
            return entry instanceof OptionEntry option ? option.row.option : null;
        }

        @Override
        public int getRowWidth() {
            return this.width - 16;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getRight() - 6;
        }
    }

    private abstract static class RowEntry extends ContainerObjectSelectionList.Entry<RowEntry> {
    }

    /** A group's title; a click folds or opens the group. */
    private final class HeaderEntry extends RowEntry {
        private final ConfigRegistry.Section section;
        private final ConfigRegistry.Group group;

        HeaderEntry(ConfigRegistry.Section section, ConfigRegistry.Group group) {
            this.section = section;
            this.group = group;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.fill(left, top, left + width, top + height, hovering ? 0x50FFFFFF : 0x30FFFFFF);
            int textY = top + (height - font.lineHeight) / 2 + 1;
            String arrow = isCollapsed(section, group) ? "▶ " : "▼ ";
            graphics.drawString(font, Component.literal(arrow + group.title).withStyle(ChatFormatting.BOLD),
                    left + 4, textY, 0xFFFFFF);
            String count = ConfigRows.enabledCount(section, group, RAW) + "/" + ConfigRows.optionsOf(section, group).size();
            graphics.drawString(font, count, left + width - 4 - font.width(count), textY, 0xA0A0A0);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            COLLAPSED.put(section.id + "/" + group.id, !isCollapsed(section, group));
            rowsDirty = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** An option: its label on the left, the on/off button on the right. */
    private final class OptionEntry extends RowEntry {
        private final ConfigRegistry.Section section;
        private final ConfigRows.Row row;
        private final CycleButton<?> button;

        OptionEntry(ConfigRegistry.Section section, ConfigRows.Row row, int valueWidth) {
            this.section = section;
            this.row = row;
            ConfigRegistry.BooleanOption opt = row.option;
            this.button = opt instanceof ConfigRegistry.ChoiceOption choice ? stepped(choice, valueWidth)
                    : CycleButton.<Boolean>builder(v -> Component.literal(v ? opt.onText : opt.offText))
                    .withValues(Boolean.TRUE, Boolean.FALSE)
                    .displayOnlyValue()
                    // Raw: show what each option is actually set to, even when the global
                    // switch is currently forcing every addon off.
                    .withInitialValue(EMFCompatConfig.getBooleanRaw(opt.key, opt.defaultValue))
                    .create(0, 0, valueWidth, ROW_HEIGHT, Component.literal(opt.label),
                            (btn, value) -> EMFCompatConfig.setBoolean(opt.key, value));
        }

        /** A button that steps through a {@link ConfigRegistry.ChoiceOption}'s values. */
        private static CycleButton<Integer> stepped(ConfigRegistry.ChoiceOption choice, int valueWidth) {
            List<Integer> steps = new ArrayList<>();
            for (int i = 0; i < choice.values.length; i++) {
                steps.add(i);
            }
            return CycleButton.<Integer>builder(i -> Component.literal(choice.texts[i]))
                    .withValues(steps)
                    .displayOnlyValue()
                    .withInitialValue(choice.indexOf(EMFCompatConfig.getNumber(choice.key, choice.defaultNumber)))
                    .create(0, 0, valueWidth, ROW_HEIGHT, Component.literal(choice.label),
                            (btn, i) -> EMFCompatConfig.setNumber(choice.key, choice.values[i]));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            ConfigRegistry.BooleanOption opt = row.option;
            boolean locked = ConfigRows.locked(section, opt, RAW);
            int textX = left + 4 + row.depth * INDENT;
            int textY = top + (height - font.lineHeight) / 2 + 1;
            // The label gives way to the button; a changed option is marked.
            String mark = ConfigRows.modified(opt, RAW) ? " *" : "";
            int room = left + width - button.getWidth() - 6 - textX - font.width(mark);
            String label = font.width(opt.label) <= room ? opt.label
                    : font.plainSubstrByWidth(opt.label, room - font.width("...")) + "...";
            graphics.drawString(font, label + mark, textX, textY, locked ? 0x808080 : 0xFFFFFF);

            button.active = !locked;
            button.setX(left + width - button.getWidth());
            button.setY(top);
            button.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }
    }
}
