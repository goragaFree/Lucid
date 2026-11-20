package dev.lucid.sreen.clickgui;

import dev.lucid.Lucid;
import dev.lucid.setting.BindSetting;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.sreen.clickgui.component.TextComponent;
import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI: по колонке на каждую категорию. Открывается/закрывается на RShift.
 * Экран создаётся один раз (см. Lucid#getClickGui) — раскрытые модули сохраняются.
 *
 * Кнопки мыши читаются напрямую через GLFW — так код не зависит от
 * ванильных mouseClicked/mouseReleased, которые часто меняются между версиями.
 */
public class ClickGuiScreen extends Screen {

    private static final int[] BUTTONS = {
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            GLFW.GLFW_MOUSE_BUTTON_4,
            GLFW.GLFW_MOUSE_BUTTON_5,
            GLFW.GLFW_MOUSE_BUTTON_6,
            GLFW.GLFW_MOUSE_BUTTON_7,
            GLFW.GLFW_MOUSE_BUTTON_8
    };
    private final List<CategoryPanel> panels = new ArrayList<>();
    private final boolean[] wasDown = new boolean[BUTTONS.length];

    /** Модуль, который ждёт бинда: следующее нажатие — клавиша или кнопка мыши — станет его. */
    private Module listening;
    private BindSetting listeningSetting;

    /** Поле ввода, которому принадлежит клавиатура; {@code null} — печатать некуда. */
    private TextComponent textListening;

    public ClickGuiScreen() {
        super(Component.literal("ClickGUI"));
        for (Category category : Category.values()) {
            panels.add(new CategoryPanel(category, this));
        }
    }

    @Override
    protected void init() {
        // Чтобы уже зажатая кнопка не "засчиталась" кликом при открытии
        long window = GLFW.glfwGetCurrentContext();
        for (int i = 0; i < BUTTONS.length; i++) {
            wasDown[i] = window != 0L && GLFW.glfwGetMouseButton(window, BUTTONS[i]) == GLFW.GLFW_PRESS;
        }

        // Колонки по центру экрана по обеим осям (пересчитывается и при смене размера окна).
        // Высота фиксированная, но на маленьком экране ужимается, чтобы влезть.
        float totalWidth = panels.size() * Theme.PANEL_WIDTH + (panels.size() - 1) * Theme.PANEL_GAP;
        float panelHeight = Math.max(Math.min(Theme.PANEL_HEIGHT, this.height - Theme.SCREEN_MARGIN * 2), Theme.HEADER_HEIGHT * 3);
        // Округляем до целых: так края scissor-обрезки совпадают с краями панели
        float x = (float) Math.floor((this.width - totalWidth) / 2);
        float y = (float) Math.floor((this.height - panelHeight) / 2);
        for (CategoryPanel panel : panels) {
            panel.setBounds(x, y, panelHeight);
            x += Theme.PANEL_WIDTH + Theme.PANEL_GAP;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        pollMouse(mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, delta);
        // Тени — одним проходом до подложек: иначе тень соседа справа ложится на уже
        // нарисованный край предыдущей панели и вдоль колонок идут тёмные полосы.
        for (CategoryPanel panel : panels) {
            panel.renderShadow(graphics);
        }
        for (CategoryPanel panel : panels) {
            panel.render(graphics, mouseX, mouseY);
        }
    }

    private void pollMouse(int mouseX, int mouseY) {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) return;

        for (int i = 0; i < BUTTONS.length; i++) {
            boolean down = GLFW.glfwGetMouseButton(window, BUTTONS[i]) == GLFW.GLFW_PRESS;
            if (down && !wasDown[i]) onMouseClicked(mouseX, mouseY, BUTTONS[i]);
            else if (!down && wasDown[i]) onMouseReleased(mouseX, mouseY, BUTTONS[i]);
            wasDown[i] = down;
        }
    }

    private void onMouseClicked(int mouseX, int mouseY, int button) {
        // Ждём бинд — тогда клик становится кнопкой мыши, где бы он ни пришёлся.
        // Проверка стоит до панелей: иначе тот же клик ещё и переключил бы модуль.
        if (listeningSetting != null) {
            listeningSetting.bindMouse(button);
            listeningSetting = null;
            return;
        }
        if (listening != null) {
            listening.bindMouse(button);
            listening = null;
            return;
        }
        // Клик мимо поля ввода снимает с него фокус — но не тогда, когда клик
        // прицелен в само поле: это проверяет isFieldHovered, а не весь компонент.
        if (textListening != null && !textListening.isFieldHovered(mouseX, mouseY)) {
            textListening = null;
        }
        for (CategoryPanel panel : panels) {
            if (panel.mouseClicked(mouseX, mouseY, button)) return;
        }
    }

    private void onMouseReleased(int mouseX, int mouseY, int button) {
        for (CategoryPanel panel : panels) {
            panel.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (CategoryPanel panel : panels) {
            if (panel.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- Бинды ----

    /** Начать привязку: следующий ввод станет биндом модуля (см. onMouseClicked и keyPressed). */
    void beginBind(Module module) {
        this.listeningSetting = null;
        this.listening = module;
    }

    void beginBind(BindSetting setting) {
        this.listening = null;
        this.listeningSetting = setting;
    }

    // ---- Поля ввода ----

    /** Клик по полю настройки просит клавиатуру для себя; старое поле фокус теряет. */
    public void beginText(TextComponent component) {
        this.textListening = component;
    }

    /** Enter/Esc/клик мимо — фокус снят, клавиши снова идут в бинды и закрытие GUI. */
    public void endText() {
        this.textListening = null;
    }

    public boolean isTextListening(TextComponent component) {
        return textListening == component;
    }

    /**
     * Символы приходят из {@code KeyboardHandlerMixin}: экран не объявляет
     * {@code charTyped}, injection в него не нашлась бы (см. AltManager).
     */
    public boolean onCharTyped(net.minecraft.client.input.CharacterEvent event) {
        return textListening != null && textListening.charTyped(event);
    }

    boolean isListening(BindSetting setting) {
        return listeningSetting == setting;
    }

    public boolean isBinding() {
        return listening != null || listeningSetting != null;
    }

    boolean isListening(Module module) {
        return listening == module;
    }

    /**
     * Пока ждём бинд, экран забирает клавишу себе и не даёт ей закрыть GUI.
     * Esc — отмена (старый бинд остаётся), Delete/Backspace — стереть бинд совсем.
     *
     * <p>Поле ввода в фокусе первично: Esc и Enter оно съедает само (снимает фокус),
     * и закрытие GUI по Esc при наборе текста не срабатывает.</p>
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (textListening != null && textListening.keyPressed(event)) return true;
        if (!isBinding()) return super.keyPressed(event);

        int key = event.key();
        if (key < GLFW.GLFW_KEY_SPACE || key > GLFW.GLFW_KEY_LAST) return true;
        if (listeningSetting != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listeningSetting = null;
            } else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                listeningSetting.clearBind();
                listeningSetting = null;
            } else {
                listeningSetting.bindKey(key);
                listeningSetting = null;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            listening = null;
        } else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            listening.clearBind();
            listening = null;
        } else {
            listening.bindKey(key);
            listening = null;
        }
        return true;
    }

    @Override
    public void removed() {
        // Закрыли GUI с зажатой кнопкой — отпускаем слайдеры
        for (int i = 0; i < BUTTONS.length; i++) {
            onMouseReleased(0, 0, BUTTONS[i]);
            wasDown[i] = false;
        }
        listening = null;
        listeningSetting = null;
        textListening = null;
        // Закрыли GUI — сохраняем всё, что поменяли
        Lucid.getInstance().getConfigManager().save();
        super.removed();
    }

    /** Фон не рисуем: ванильный renderBackground затемнил бы мир и включил блюр. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
