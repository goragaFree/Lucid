package dev.lucid.sreen.clickgui;

import dev.lucid.sreen.clickgui.component.BooleanComponent;
import dev.lucid.sreen.clickgui.component.BindComponent;
import dev.lucid.setting.BindSetting;
import dev.lucid.sreen.clickgui.component.ColorComponent;
import dev.lucid.sreen.clickgui.component.MultiSelectComponent;
import dev.lucid.sreen.clickgui.component.SelectComponent;
import dev.lucid.sreen.clickgui.component.SettingComponent;
import dev.lucid.sreen.clickgui.component.SliderComponent;
import dev.lucid.sreen.clickgui.component.TextComponent;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.ColorSetting;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.Setting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.setting.TextSetting;
import dev.lucid.util.KeyNames;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import dev.lucid.util.render.font.Icons;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Кнопка модуля: ЛКМ — вкл/выкл, ПКМ — раскрыть настройки, СКМ — назначить бинд. */
public class ModuleButton {

    private final Module module;
    private final List<SettingComponent<?>> components = new ArrayList<>();
    private boolean expanded;
    private float x;
    private float y;
    // Внутренняя область панели (между обводками) — для подсветки и фона настроек
    private float innerX;
    private float innerWidth;
    private final CategoryPanel panel;

    public ModuleButton(Module module, CategoryPanel panel) {
        this.module = module;
        this.panel = panel;
        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BindSetting bind) components.add(new BindComponent(bind, panel));
            else if (setting instanceof BooleanSetting bool) components.add(new BooleanComponent(bool));
            else if (setting instanceof SliderSetting slider) components.add(new SliderComponent(slider));
            else if (setting instanceof SelectSetting select) components.add(new SelectComponent(select));
            else if (setting instanceof MultiSelectSetting multi) components.add(new MultiSelectComponent(multi));
            else if (setting instanceof TextSetting text) components.add(new TextComponent(text));
            else if (setting instanceof ColorSetting color) components.add(new ColorComponent(color));
        }
    }

    public void setBounds(float x, float y, float innerX, float innerWidth) {
        this.x = x;
        this.y = y;
        this.innerX = innerX;
        this.innerWidth = innerWidth;
    }

    /**
     * Только те настройки, чьё условие сейчас истинно. Список считается на каждый кадр:
     * условие может переключиться кликом по соседней строке в этом же кадре, и высота
     * панели должна измениться сразу, без пересборки компонентов.
     */
    private List<SettingComponent<?>> visibleComponents() {
        List<SettingComponent<?>> visible = new ArrayList<>(components.size());
        for (SettingComponent<?> component : components) {
            if (component.isVisible()) visible.add(component);
        }
        return visible;
    }

    public float getHeight() {
        return Theme.MODULE_HEIGHT + getSettingsHeight();
    }

    private float getSettingsHeight() {
        if (!expanded) return 0;
        float height = 0;
        for (SettingComponent<?> component : visibleComponents()) height += component.getHeight();
        return height;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        // Включённый модуль — акцентный цвет названия
        Render2D.textInRow(graphics, Fonts.semibold(), module.getName(), x + Theme.MODULE_PADDING, y, Theme.MODULE_HEIGHT,
                Theme.MODULE_FONT_SIZE, module.isEnabled() ? Theme.ACCENT : Theme.TEXT_SECONDARY);

        renderBind(graphics);

        if (!visibleComponents().isEmpty()) {
            float iconSize = 7;
            Render2D.iconCentered(graphics, Icons.CHEVRON_DOWN, x + Theme.PANEL_WIDTH - Theme.MODULE_PADDING - iconSize / 2,
                    y + Theme.MODULE_HEIGHT / 2, iconSize, expanded ? Theme.TEXT : Theme.TEXT_SECONDARY);
        }

        if (expanded) {
            float settingsY = y + Theme.MODULE_HEIGHT;
            panel.fillRow(graphics, settingsY, getSettingsHeight(), Theme.SETTING_BACKGROUND);

            for (SettingComponent<?> component : visibleComponents()) {
                component.setPosition(x, settingsY);
                component.render(graphics, mouseX, mouseY);
                settingsY += component.getHeight();
            }
        }
    }

    /**
     * Имя бинда справа в строке. Пока ждём нажатия — многоточие акцентом вместо имени,
     * чтобы было видно, что экран слушает клавиатуру.
     */
    private void renderBind(GuiGraphics graphics) {
        boolean listening = panel.isListening(module);
        String bind = KeyNames.of(module.getKey(), module.getMouseButton());
        if (!listening && bind == null) return;

        String text = listening ? "..." : bind;
        FontAtlas font = Fonts.medium();
        // Отступ справа — ещё и под стрелку раскрытия, иначе бинд налезет на неё
        float right = x + Theme.PANEL_WIDTH - Theme.MODULE_PADDING - (visibleComponents().isEmpty() ? 0 : 10);
        Render2D.textInRow(graphics, font, text, right - font.width(text, Theme.BIND_FONT_SIZE), y, Theme.MODULE_HEIGHT,
                Theme.BIND_FONT_SIZE, listening ? Theme.ACCENT : Theme.TEXT_SECONDARY);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (Render2D.isHovered(mouseX, mouseY, x, y, Theme.PANEL_WIDTH, Theme.MODULE_HEIGHT)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                panel.beginBind(module);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                module.toggle();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !visibleComponents().isEmpty()) {
                expanded = !expanded;
            }
            return true;
        }

        if (expanded) {
            for (SettingComponent<?> component : visibleComponents()) {
                if (component.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        for (SettingComponent<?> component : visibleComponents()) {
            component.mouseReleased(mouseX, mouseY, button);
        }
    }
}
