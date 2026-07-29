package strm.emfcompat.core.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabbed configuration screen. A column of tabs on the left selects which compat mod to
 * configure (Core first, selected by default); the panel on the right shows that mod's
 * options. Tabs and options come from {@link ConfigRegistry}, so an addon adds a whole tab
 * just by registering a section.
 *
 * <p>Same screen as on 1.21.11, adapted to this version's GUI API: drawing goes through
 * {@code GuiGraphicsExtractor} instead of {@code GuiGraphics}, the draw hook is
 * {@code extractRenderState} instead of {@code render}, and the text helpers are
 * {@code centeredText} / {@code text} instead of {@code drawCenteredString} /
 * {@code drawString}, and closing goes through {@code setScreenAndShow}. Widgets, tooltips and
 * scrolling are unchanged.</p>
 */
public class ConfigScreen extends Screen {

    private static final int TAB_WIDTH = 100;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int PANEL_TOP = 40;

    private final Screen parent;
    private String selectedSectionId = ConfigRegistry.CORE_ID;

    /** How many tabs are scrolled off the top of the column, and the current max. */
    private int tabScroll = 0;
    private int tabMaxScroll = 0;

    public ConfigScreen(Screen parent) {
        super(Component.literal("EMF Compat"));
        this.parent = parent;
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
        int optY = PANEL_TOP;
        ConfigRegistry.Section selected = ConfigRegistry.get(selectedSectionId);
        if (selected != null) {
            for (ConfigRegistry.BooleanOption opt : selected.booleans) {
                // Raw: show what each option is actually set to, even when the global switch is
                // currently forcing every addon off.
                addRenderableWidget(CycleButton.<Boolean>builder(
                                v -> Component.literal(v ? opt.onText : opt.offText),
                                EMFCompatConfig.getBooleanRaw(opt.key, opt.defaultValue))
                        .withValues(Boolean.TRUE, Boolean.FALSE)
                        .withTooltip(v -> Tooltip.create(Component.literal(v ? opt.onTooltip : opt.offTooltip)))
                        .create(optX, optY, optW, ROW_HEIGHT, Component.literal(opt.label),
                                (btn, value) -> EMFCompatConfig.setBoolean(opt.key, value)));
                optY += ROW_HEIGHT + ROW_GAP;
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, ROW_HEIGHT)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll the tab column while the cursor is over it.
        if (tabMaxScroll > 0 && mouseX < 12 + TAB_WIDTH + 8) {
            int updated = Mth.clamp(tabScroll - (int) Math.signum(scrollY), 0, tabMaxScroll);
            if (updated != tabScroll) {
                tabScroll = updated;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFF);

        // Arrows hint that the tab column scrolls (mouse wheel over the tabs).
        if (tabMaxScroll > 0) {
            int cx = 12 + TAB_WIDTH / 2;
            if (tabScroll > 0) {
                graphics.centeredText(this.font, Component.literal("▲"), cx, PANEL_TOP - 10, 0xFFFFFF);
            }
            if (tabScroll < tabMaxScroll) {
                graphics.centeredText(this.font, Component.literal("▼"), cx, this.height - 38, 0xFFFFFF);
            }
        }

        ConfigRegistry.Section selected = ConfigRegistry.get(selectedSectionId);
        if (selected != null && selected.booleans.isEmpty()) {
            int optX = 12 + TAB_WIDTH + 16;
            graphics.text(this.font, Component.literal("No options yet."), optX, PANEL_TOP + 4, 0xA0A0A0);
        }
    }

    @Override
    public void onClose() {
        EMFCompatConfig.save();
        if (this.minecraft != null) {
            // 26.2 renamed Minecraft#setScreen to setScreenAndShow.
            this.minecraft.setScreenAndShow(parent);
        }
    }
}
