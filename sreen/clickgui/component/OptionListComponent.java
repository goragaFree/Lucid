package dev.lucid.sreen.clickgui.component;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.setting.Setting;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Общая раскладка настроек-списков: заголовок, строки по {@link Theme#OPTION_HEIGHT}
 * и индикатор справа.
 *
 * <p>Селект и мультиселект отличаются ровно двумя вещами — видом индикатора (точка или
 * галочка) и тем, что делает клик (заменить выбор или переключить вариант). Всё остальное
 * у них обязано совпадать, иначе два списка в одной панели начнут разъезжаться по отступам
 * при первой же правке. Поэтому геометрия и обход строк живут здесь, а наследники
 * переопределяют только {@link #drawIndicator} и {@link #onOptionClicked}.</p>
 */
public abstract class OptionListComponent<S extends Setting<?>> extends SettingComponent<S> {

    protected static final float PADDING = 10;
    protected static final float INDENT = 6;       // варианты сдвинуты вправо относительно названия
    protected static final float INDICATOR_SIZE = 8;

    protected OptionListComponent(S setting) {
        super(setting);
    }

    protected abstract List<String> options();

    protected abstract boolean isSelected(String option);

    /** Клик по строке варианта: селект заменяет выбор, мультиселект переключает. */
    protected abstract void onOptionClicked(String option);

    /** Индикатор в правом поле строки: boxX/boxY — левый верхний угол квадрата INDICATOR_SIZE. */
    protected abstract void drawIndicator(GuiGraphics graphics, float boxX, float boxY, boolean selected);

    @Override
    public float getHeight() {
        return Theme.SETTING_HEIGHT + options().size() * Theme.OPTION_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        float size = Theme.SETTING_FONT_SIZE;
        Render2D.textInRow(graphics, Fonts.regular(), setting.getName(), x + PADDING, y,
                Theme.SETTING_HEIGHT, size, Theme.TEXT_SECONDARY);

        float rowY = y + Theme.SETTING_HEIGHT;
        for (String option : options()) {
            boolean selected = isSelected(option);
            // Никакой реакции на наведение: список — это статичный перечень, и подсветка
            // под курсором только рябит, когда ведёшь мышь от строки к строке
            Render2D.textInRow(graphics, Fonts.regular(), option, x + PADDING + INDENT, rowY,
                    Theme.OPTION_HEIGHT, size, selected ? Theme.TEXT : Theme.TEXT_SECONDARY);

            drawIndicator(graphics,
                    x + width - PADDING - INDICATOR_SIZE,
                    rowY + (Theme.OPTION_HEIGHT - INDICATOR_SIZE) / 2,
                    selected);

            rowY += Theme.OPTION_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        float rowY = y + Theme.SETTING_HEIGHT;
        for (String option : options()) {
            if (Render2D.isHovered(mouseX, mouseY, x, rowY, width, Theme.OPTION_HEIGHT)) {
                onOptionClicked(option);
                return true;
            }
            rowY += Theme.OPTION_HEIGHT;
        }
        return false;
    }
}
