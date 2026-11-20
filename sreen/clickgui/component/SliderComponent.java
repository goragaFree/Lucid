package dev.lucid.sreen.clickgui.component;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;

public class SliderComponent extends SettingComponent<SliderSetting> {

    private static final float PADDING = 10;
    private static final float TRACK_HEIGHT = 3;
    private static final float KNOB_SIZE = 7;

    private boolean dragging;

    public SliderComponent(SliderSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return Theme.SLIDER_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (dragging) updateValue(mouseX);

        // Название и значение
        FontAtlas font = Fonts.regular();
        float size = Theme.SETTING_FONT_SIZE;
        float rowHeight = getHeight() - 7; // строка текста над полосой
        Render2D.textInRow(graphics, font, setting.getName(), x + PADDING, y + 1, rowHeight, size, Theme.TEXT_SECONDARY);
        String value = format(setting.getValue());
        Render2D.textInRow(graphics, Fonts.medium(), value, x + width - PADDING - Fonts.medium().width(value, size), y + 1, rowHeight, size, Theme.TEXT);

        // Полоса
        float trackX = x + PADDING;
        float trackWidth = width - PADDING * 2;
        float trackY = y + getHeight() - 6;
        double percent = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        float filled = (float) (trackWidth * percent);

        Render2D.roundedRect(graphics, trackX, trackY, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT / 2, Theme.TRACK);
        Render2D.roundedRect(graphics, trackX, trackY, filled, TRACK_HEIGHT, TRACK_HEIGHT / 2, Theme.ACCENT);
        Render2D.circle(graphics, trackX + filled, trackY + TRACK_HEIGHT / 2, KNOB_SIZE / 2, Theme.KNOB);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            dragging = true;
            updateValue(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) dragging = false;
    }

    private void updateValue(int mouseX) {
        float trackX = x + PADDING;
        float trackWidth = width - PADDING * 2;
        double percent = Math.max(0, Math.min(1, (mouseX - trackX) / trackWidth));
        setting.setValue(setting.getMin() + (setting.getMax() - setting.getMin()) * percent);
    }

    private String format(double value) {
        if (setting.getStep() >= 1) return String.valueOf(Math.round(value));
        return String.format("%.2f", value);
    }
}
