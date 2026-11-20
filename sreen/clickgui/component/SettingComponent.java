package dev.lucid.sreen.clickgui.component;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.setting.Setting;
import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphics;

/** Базовый элемент настройки внутри раскрытого модуля. */
public abstract class SettingComponent<S extends Setting<?>> {

    protected final S setting;
    protected float x;
    protected float y;
    protected final float width = Theme.PANEL_WIDTH;

    protected SettingComponent(S setting) {
        this.setting = setting;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getHeight() {
        return Theme.SETTING_HEIGHT;
    }

    /** Скрытая настройка не занимает место и не ловит клики, но остаётся в конфиге. */
    public boolean isVisible() { return setting.isVisible(); }

    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY);

    /** @return true, если клик обработан */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {}

    protected boolean isHovered(int mouseX, int mouseY) {
        return Render2D.isHovered(mouseX, mouseY, x, y, width, getHeight());
    }
}
