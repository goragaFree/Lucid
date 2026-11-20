package dev.lucid.sreen.clickgui.component;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Icons;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Список вариантов с галочками: каждая строка — отдельный независимый переключатель.
 * Геометрия и обход строк — в {@link OptionListComponent}.
 */
public class MultiSelectComponent extends OptionListComponent<MultiSelectSetting> {

    private static final float CHECKBOX_RADIUS = 2;

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting);
    }

    @Override
    protected List<String> options() {
        return setting.getOptions();
    }

    @Override
    protected boolean isSelected(String option) {
        return setting.isSelected(option);
    }

    @Override
    protected void onOptionClicked(String option) {
        setting.toggle(option);
    }

    @Override
    protected void drawIndicator(GuiGraphics graphics, float boxX, float boxY, boolean selected) {
        if (selected) {
            Render2D.roundedRect(graphics, boxX, boxY, INDICATOR_SIZE, INDICATOR_SIZE, CHECKBOX_RADIUS, Theme.ACCENT);
            Render2D.iconCentered(graphics, Icons.CHECK, boxX + INDICATOR_SIZE / 2, boxY + INDICATOR_SIZE / 2,
                    INDICATOR_SIZE - 2, Theme.KNOB);
        } else {
            Render2D.roundedRectOutlined(graphics, boxX, boxY, INDICATOR_SIZE, INDICATOR_SIZE, CHECKBOX_RADIUS,
                    Theme.OUTLINE_WIDTH, Theme.TRACK, Theme.OUTLINE);
        }
    }
}
