package dev.lucid.sreen.clickgui.component;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;

/** Переключатель-«таблетка» справа. */
public class BooleanComponent extends SettingComponent<BooleanSetting> {

    private static final float SWITCH_WIDTH = 16;
    private static final float SWITCH_HEIGHT = 8;

    public BooleanComponent(BooleanSetting setting) {
        super(setting);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Render2D.textInRow(graphics, Fonts.regular(), setting.getName(), x + 10, y, getHeight(), Theme.SETTING_FONT_SIZE, Theme.TEXT_SECONDARY);

        float sx = x + width - SWITCH_WIDTH - 8;
        float sy = y + (getHeight() - SWITCH_HEIGHT) / 2;
        boolean on = setting.getValue();

        Render2D.roundedRect(graphics, sx, sy, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, on ? Theme.ACCENT : Theme.TRACK);
        float knob = SWITCH_HEIGHT - 2;
        float kx = on ? sx + SWITCH_WIDTH - knob - 1 : sx + 1;
        Render2D.circle(graphics, kx + knob / 2, sy + 1 + knob / 2, knob / 2, Theme.KNOB);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            setting.toggle();
            return true;
        }
        return false;
    }
}
