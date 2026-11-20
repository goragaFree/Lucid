package dev.lucid.sreen.clickgui.component;

import dev.lucid.setting.BindSetting;
import dev.lucid.sreen.clickgui.CategoryPanel;
import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Click the row, then press a keyboard key or a mouse button. */
public final class BindComponent extends SettingComponent<BindSetting> {
    private final CategoryPanel panel;

    public BindComponent(BindSetting setting, CategoryPanel panel) {
        super(setting);
        this.panel = panel;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean listening = panel.isListening(setting);
        String text = listening ? "..." : setting.getDisplayName();
        FontAtlas font = Fonts.medium();
        float size = Theme.BIND_FONT_SIZE;
        // Reserve half the row for the value, including long names like Backspace.
        float textWidth = font.width(text, size);
        float maxWidth = width / 2 - 12;
        if (textWidth > maxWidth) size *= maxWidth / textWidth;
        textWidth = font.width(text, size);
        float boxWidth = Math.max(22, textWidth + 8);
        float boxHeight = 10;
        float boxX = x + width - boxWidth - 8;
        float boxY = y + (getHeight() - boxHeight) / 2;
        Render2D.roundedRect(graphics, boxX, boxY, boxWidth, boxHeight, 3, Theme.TRACK);
        Render2D.textInRow(graphics, font, text, boxX + (boxWidth - textWidth) / 2,
                y, getHeight(), size, listening ? Theme.ACCENT : Theme.TEXT_SECONDARY);

        String label = setting.getName();
        FontAtlas labelFont = Fonts.regular();
        float available = boxX - (x + 10) - 4;
        while (!label.isEmpty() && labelFont.width(label, Theme.SETTING_FONT_SIZE) > available) {
            label = label.substring(0, label.length() - 1);
        }
        Render2D.textInRow(graphics, labelFont, label, x + 10, y, getHeight(),
                Theme.SETTING_FONT_SIZE, Theme.TEXT_SECONDARY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isHovered(mouseX, mouseY)) return false;
        panel.beginBind(setting);
        return true;
    }
}
