package dev.lucid.sreen.clickgui.component;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.setting.ColorSetting;
import dev.lucid.util.render.ColorUtil;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Цветовая настройка: свотч справа в строке, по клику раскрывается HSV-пикер.
 *
 * <p>Квадрат насыщенность/яркость и полоса тона собраны из тех же градиентов, что
 * рисует {@link Render2D}: базовая заливка тоном, поверх белый градиент по X и чёрный
 * по Y — классическое SV-поле без единого дополнительного шейдера. Сектор полосы тона
 * под курсором кликнуть нельзя — только сама полоса, как и в любых пикерах.</p>
 *
 * <p>Состояние тона/насыщенности/яркости живёт в компоненте и снимается со значения
 * один раз при раскрытии: каждый кадр гонять RGB → HSV → RGB нельзя, округление каналов
 * за минуту драга «съело» бы точность выбранного цвета. В настройку пишется уже
 * собранный ARGB, свотч читает его прямо из настройки.</p>
 */
public class ColorComponent extends SettingComponent<ColorSetting> {

    private static final float PADDING = 10;
    private static final float SV_HEIGHT = 50;
    private static final float HUE_HEIGHT = 6;
    private static final float GAP = 4;
    private static final float PICKER_TOP_GAP = 3;
    private static final float PICKER_BOTTOM_PAD = 3;
    private static final float SWATCH_WIDTH = 16;
    private static final float SWATCH_HEIGHT = 8;
    /** Шесть сегментов полосы тона: red → yellow → green → cyan → blue → magenta → red. */
    private static final int[] HUE_STOPS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };
    private static final int MARKER_OUTLINE = 0xE6000000;

    private boolean expanded;
    private boolean draggingSV;
    private boolean draggingHue;
    private float hue;
    private float saturation;
    private float brightness;

    public ColorComponent(ColorSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return expanded
                ? Theme.SETTING_HEIGHT + PICKER_TOP_GAP + SV_HEIGHT + GAP + HUE_HEIGHT + PICKER_BOTTOM_PAD
                : Theme.SETTING_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Render2D.textInRow(graphics, Fonts.regular(), setting.getName(), x + PADDING, y,
                Theme.SETTING_HEIGHT, Theme.SETTING_FONT_SIZE, Theme.TEXT_SECONDARY);

        float sx = x + width - SWATCH_WIDTH - PADDING;
        float sy = y + (Theme.SETTING_HEIGHT - SWATCH_HEIGHT) / 2;
        Render2D.roundedRect(graphics, sx, sy, SWATCH_WIDTH, SWATCH_HEIGHT, 2, setting.getValue());
        Render2D.roundedOutline(graphics, sx, sy, SWATCH_WIDTH, SWATCH_HEIGHT, 2, 1, Theme.OUTLINE);

        if (draggingSV) updateSV(mouseX, mouseY);
        if (draggingHue) updateHue(mouseX);

        if (!expanded) return;

        float svX = x + PADDING;
        float svY = y + Theme.SETTING_HEIGHT + PICKER_TOP_GAP;
        float svW = width - PADDING * 2;

        int hueColor = ColorUtil.fromHsv(hue, 1.0F, 1.0F);
        Render2D.rect(graphics, svX, svY, svW, SV_HEIGHT, hueColor);
        Render2D.horizontalGradient(graphics, svX, svY, svW, SV_HEIGHT, 0xFFFFFFFF, 0x00FFFFFF);
        Render2D.verticalGradient(graphics, svX, svY, svW, SV_HEIGHT, 0x00000000, 0xFF000000);

        float markerX = svX + saturation * svW;
        float markerY = svY + (1.0F - brightness) * SV_HEIGHT;
        Render2D.circle(graphics, markerX, markerY, 3.5F, MARKER_OUTLINE);
        Render2D.circle(graphics, markerX, markerY, 2.5F, Theme.KNOB);

        float hueY = svY + SV_HEIGHT + GAP;
        float segment = svW / (HUE_STOPS.length - 1);
        for (int i = 0; i < HUE_STOPS.length - 1; i++) {
            Render2D.horizontalGradient(graphics, svX + i * segment, hueY, segment, HUE_HEIGHT,
                    HUE_STOPS[i], HUE_STOPS[i + 1]);
        }
        float hueMarkerX = svX + hue / 360.0F * svW;
        Render2D.circle(graphics, hueMarkerX, hueY + HUE_HEIGHT / 2, 3.5F, MARKER_OUTLINE);
        Render2D.circle(graphics, hueMarkerX, hueY + HUE_HEIGHT / 2, 2.5F, Theme.KNOB);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        if (isHovered(mouseX, mouseY) && !pickerHovered(mouseX, mouseY)) {
            expanded = !expanded;
            if (expanded) {
                // Снимок HSV один раз на раскрытие, а не каждый кадр — см. javadoc класса
                float[] hsv = ColorUtil.hsv(setting.getValue());
                hue = hsv[0];
                saturation = hsv[1];
                brightness = hsv[2];
            }
            return true;
        }

        if (expanded) {
            if (svHovered(mouseX, mouseY)) {
                draggingSV = true;
                updateSV(mouseX, mouseY);
                return true;
            }
            if (hueHovered(mouseX, mouseY)) {
                draggingHue = true;
                updateHue(mouseX);
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            draggingSV = false;
            draggingHue = false;
        }
    }

    private boolean pickerHovered(int mouseX, int mouseY) {
        return svHovered(mouseX, mouseY) || hueHovered(mouseX, mouseY);
    }

    private boolean svHovered(int mouseX, int mouseY) {
        return Render2D.isHovered(mouseX, mouseY, x + PADDING, y + Theme.SETTING_HEIGHT + PICKER_TOP_GAP,
                width - PADDING * 2, SV_HEIGHT);
    }

    private boolean hueHovered(int mouseX, int mouseY) {
        return Render2D.isHovered(mouseX, mouseY, x + PADDING,
                y + Theme.SETTING_HEIGHT + PICKER_TOP_GAP + SV_HEIGHT + GAP,
                width - PADDING * 2, HUE_HEIGHT);
    }

    private void updateSV(int mouseX, int mouseY) {
        float svX = x + PADDING;
        float svY = y + Theme.SETTING_HEIGHT + PICKER_TOP_GAP;
        float svW = width - PADDING * 2;
        saturation = Math.max(0.0F, Math.min(1.0F, (mouseX - svX) / svW));
        brightness = Math.max(0.0F, Math.min(1.0F, 1.0F - (mouseY - svY) / SV_HEIGHT));
        setting.setValue(ColorUtil.fromHsv(hue, saturation, brightness));
    }

    private void updateHue(int mouseX) {
        float svW = width - PADDING * 2;
        hue = Math.max(0.0F, Math.min(1.0F, (mouseX - x - PADDING) / svW)) * 360.0F;
        setting.setValue(ColorUtil.fromHsv(hue, saturation, brightness));
    }
}
