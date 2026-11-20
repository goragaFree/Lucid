package dev.lucid.sreen.clickgui.component;

import dev.lucid.sreen.clickgui.ClickGuiScreen;
import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.setting.TextSetting;
import dev.lucid.util.Wrapper;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Строковая настройка: название сверху, поле ввода снизу.
 *
 * <p>Свой ввод, как и у {@code AltManager}: ванильный {@code EditBox} тащит за собой
 * рамку, фокус-навигацию и виджет-обвязку, которые не вписываются в панель. Клик по
 * полю просит {@link ClickGuiScreen#beginText} передать клавиатуру этому компоненту;
 * пока поле в фокусе, клавиши приходят через {@code ClickGuiScreen#keyPressed}, а
 * символы — из {@code KeyboardHandlerMixin} (экран не объявляет {@code charTyped},
 * injection в него не нашлась бы — та же причина, что у менеджера ников).</p>
 *
 * <p>Каретка и удаление — как в поле альтов: символы дописываются в конец, Backspace
 * стирает последний. Enter и Esc снимают фокус; клик мимо поля снимает его тоже — это
 * делает сам экран, компонент только отдаёт границы поля ({@link #isFieldHovered}).</p>
 */
public class TextComponent extends SettingComponent<TextSetting> implements Wrapper {

    private static final float PADDING = 10;
    private static final float NAME_HEIGHT = 12;
    private static final float FIELD_GAP = 1;
    private static final float FIELD_HEIGHT = 12;
    private static final float FIELD_PAD_X = 4;

    public TextComponent(TextSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return NAME_HEIGHT + FIELD_GAP + FIELD_HEIGHT + 1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Render2D.textInRow(graphics, Fonts.regular(), setting.getName(), x + PADDING, y,
                NAME_HEIGHT, Theme.SETTING_FONT_SIZE, Theme.TEXT_SECONDARY);

        float fieldY = y + NAME_HEIGHT + FIELD_GAP;
        ClickGuiScreen gui = mc.screen instanceof ClickGuiScreen screen ? screen : null;
        boolean focused = gui != null && gui.isTextListening(this);

        Render2D.roundedRectOutlined(graphics, x + PADDING, fieldY, width - PADDING * 2, FIELD_HEIGHT,
                4, 1, Theme.SETTING_BACKGROUND, focused ? Theme.ACCENT : Theme.OUTLINE);

        FontAtlas font = Fonts.regular();
        String value = setting.getValue();
        float textX = x + PADDING + FIELD_PAD_X;
        if (value.isEmpty()) {
            Render2D.textInRow(graphics, font, "Click to type", textX, fieldY,
                    FIELD_HEIGHT, Theme.SETTING_FONT_SIZE, Theme.TEXT_SECONDARY);
        } else {
            Render2D.textInRow(graphics, font, value, textX, fieldY,
                    FIELD_HEIGHT, Theme.SETTING_FONT_SIZE, Theme.TEXT);
        }

        // Каретка мигает только в фокусе и рисуется после текста, чтобы не перекрывать буквы
        if (focused && System.currentTimeMillis() % 1000 < 500) {
            float caretX = textX + (value.isEmpty() ? 0 : font.width(value, Theme.SETTING_FONT_SIZE));
            Render2D.rect(graphics, caretX, fieldY + 3, 1, FIELD_HEIGHT - 6, Theme.ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) return false;
        if (mc.screen instanceof ClickGuiScreen gui) gui.beginText(this);
        return true;
    }

    /** Границы поля ввода: экран по ним решает, снимать ли фокус при клике мимо. */
    public boolean isFieldHovered(int mouseX, int mouseY) {
        return Render2D.isHovered(mouseX, mouseY, x + PADDING, y + NAME_HEIGHT + FIELD_GAP,
                width - PADDING * 2, FIELD_HEIGHT);
    }

    /** Клавиши поля в фокусе. {@code false} — клавиша не наша, отдаем экрану. */
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (mc.screen instanceof ClickGuiScreen gui) gui.endText();
            return true;
        }
        if (key != GLFW.GLFW_KEY_BACKSPACE) return false;
        String value = setting.getValue();
        if (!value.isEmpty()) setting.setValue(value.substring(0, value.length() - 1));
        return true;
    }

    /** Символы поля в фокусе: дописываем в конец, пока есть место в лимите. */
    public boolean charTyped(CharacterEvent event) {
        String typed = event.codepointAsString();
        if (typed == null) return true;
        String value = setting.getValue();
        if (value.length() + typed.length() <= setting.getMaxLength()) {
            setting.setValue(value + typed);
        }
        return true;
    }
}
