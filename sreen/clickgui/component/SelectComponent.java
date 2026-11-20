package dev.lucid.sreen.clickgui.component;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.setting.SelectSetting;
import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Список вариантов, из которых выбран ровно один — та же раскладка, что у
 * {@link MultiSelectComponent}, только вместо галочки точка: она читается как
 * «один из», а не «несколько».
 */
public class SelectComponent extends OptionListComponent<SelectSetting> {

    private static final float DOT_CORE = 3;

    public SelectComponent(SelectSetting setting) {
        super(setting);
    }

    @Override
    protected List<String> options() {
        return setting.getOptions();
    }

    @Override
    protected boolean isSelected(String option) {
        return setting.is(option);
    }

    @Override
    protected void onOptionClicked(String option) {
        setting.setValue(option);
    }

    @Override
    protected void drawIndicator(GuiGraphics graphics, float boxX, float boxY, boolean selected) {
        if (selected) {
            Render2D.roundedRect(graphics, boxX, boxY, INDICATOR_SIZE, INDICATOR_SIZE, INDICATOR_SIZE / 2, Theme.ACCENT);
            Render2D.circle(graphics, boxX + INDICATOR_SIZE / 2, boxY + INDICATOR_SIZE / 2, DOT_CORE / 2, Theme.KNOB);
        } else {
            Render2D.roundedRectOutlined(graphics, boxX, boxY, INDICATOR_SIZE, INDICATOR_SIZE, INDICATOR_SIZE / 2,
                    Theme.OUTLINE_WIDTH, Theme.TRACK, Theme.OUTLINE);
        }
    }
}
