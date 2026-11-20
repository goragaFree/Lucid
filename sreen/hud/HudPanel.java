
package dev.lucid.sreen.hud;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Общая геометрия и палитра текстовых HUD-панелей (Potions, Hotkeys, Cooldowns).
 *
 * <p>Здесь находятся общие параметры панелей: отступы, шрифты, цвета,
 * отрисовка фона с шапкой и вспомогательные методы для строк.</p>
 *
 * <p>Палитра Lucid: тёмная шапка и более светлая полупрозрачная
 * основная часть, сквозь которую виден мир Minecraft.</p>
 */
public final class HudPanel {

    /** Поле плашки по горизонтали. */
    public static final float PADDING = 7.5F;

    /** Штатная высота строки. */
    public static final float ROW_HEIGHT = 13;

    /** Промежуток между левой и правой частью строки. */
    public static final float GAP = 13;

    public static final float RADIUS = 6;
    public static final float OUTLINE = 1;
    public static final float FONT_SIZE = 8.5F;

    /** Высота шапки. */
    public static final float HEADER_HEIGHT = 20;

    /** Размер шрифта заголовка. */
    public static final float TITLE_FONT_SIZE = 9.5F;

    /** Отступ содержимого сверху и снизу. */
    public static final float BODY_TOP = 4;
    public static final float BODY_BOTTOM = 5;

    /** Отступ панели от края экрана. */
    public static final float MARGIN = 4;

    /** Свободное пространство снизу под элементы ванильного HUD. */
    public static final float BOTTOM_RESERVE = 58;

    // Основная часть: светлее шапки и достаточно прозрачная.
    public static final int BACKGROUND = 0xB820222B;

    // Шапка: тёмная, нейтральная, без лишнего фиолетового оттенка.
    public static final int HEADER_BACKGROUND = 0xD9101119;

    // Остальные цвета оставлены из существующей темы.
    public static final int BORDER = Theme.OUTLINE;
    public static final int TEXT = Theme.TEXT;
    public static final int TEXT_SECONDARY = Theme.TEXT_SECONDARY;
    public static final int ACCENT = Theme.ACCENT;

    private HudPanel() {}

    public static FontAtlas font() {
        return Fonts.regular();
    }

    public static float width(String text) {
        return font().width(text, FONT_SIZE);
    }

    /**
     * Ширина панели определяется самой широкой строкой или заголовком.
     */
    public static float width(float left, float right, String title) {
        float content = PADDING + left + GAP + right + PADDING;
        float header = PADDING * 2
                + Fonts.semibold().width(title, TITLE_FONT_SIZE);

        return Math.round(Math.max(content, header));
    }

    public static float height(int rows) {
        return height(rows, ROW_HEIGHT);
    }

    /** Пустая панель: целая шапка; нижняя часть — 37.5% обычной (ещё на 25% ниже половины). */
    public static float emptyHeight() {
        return HEADER_HEIGHT + (BODY_TOP + ROW_HEIGHT + BODY_BOTTOM) * 0.375F;
    }

    /** Высота панели для заданного количества строк. */
    public static float height(int rows, float rowHeight) {
        return HEADER_HEIGHT + BODY_TOP
                + Math.max(1, rows) * rowHeight
                + BODY_BOTTOM;
    }

    /** Y-координата первой строки содержимого. */
    public static float bodyTop(float y) {
        return y + HEADER_HEIGHT + BODY_TOP;
    }

    /**
     * Отрисовывает фон, тёмную шапку, заголовок и обводку.
     */
    public static void draw(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height,
            String title
    ) {
        float rx = Math.round(x);
        float ry = Math.round(y);
        float rw = Math.round(width);
        float rh = Math.round(height);

        // Основная заливка с небольшим отступом под обводку.
        float inset = Render2D.outlineInset(rw, rh, OUTLINE);
        float fillX = rx + inset;
        float fillY = ry + inset;
        float fillWidth = rw - inset * 2;
        float fillHeight = rh - inset * 2;
        float fillRadius = Math.max(RADIUS - inset, 0);

        Render2D.roundedRect(
                graphics,
                fillX,
                fillY,
                fillWidth,
                fillHeight,
                fillRadius,
                BACKGROUND
        );

        // Тёмная шапка поверх основной заливки.
        graphics.enableScissor(
                (int) Math.floor(rx),
                (int) Math.floor(ry),
                (int) Math.ceil(rx + rw),
                (int) Math.ceil(ry + HEADER_HEIGHT)
        );

        Render2D.roundedRect(
                graphics,
                fillX,
                fillY,
                fillWidth,
                fillHeight,
                fillRadius,
                HEADER_BACKGROUND
        );

        graphics.disableScissor();

        // Заголовок по центру шапки.
        FontAtlas titleFont = Fonts.semibold();

        Render2D.textInRow(
                graphics,
                titleFont,
                title,
                rx + (rw - titleFont.width(title, TITLE_FONT_SIZE)) * 0.5F,
                ry,
                HEADER_HEIGHT,
                TITLE_FONT_SIZE,
                TEXT
        );

        // Обводка поверх заливок.
        Render2D.roundedOutline(
                graphics,
                rx,
                ry,
                rw,
                rh,
                RADIUS,
                OUTLINE,
                BORDER
        );
    }

    /** Строка с обычной высотой. */
    public static void rowText(
            GuiGraphics graphics,
            String text,
            float x,
            float rowY,
            int color
    ) {
        rowText(graphics, text, x, rowY, ROW_HEIGHT, color);
    }

    /** Строка с заданной высотой. */
    public static void rowText(
            GuiGraphics graphics,
            String text,
            float x,
            float rowY,
            float rowHeight,
            int color
    ) {
        Render2D.textInRow(
                graphics,
                font(),
                text,
                x,
                rowY,
                rowHeight,
                FONT_SIZE,
                color
        );
    }

    /** Строка, прижатая к правому краю. */
    public static void rowTextRight(
            GuiGraphics graphics,
            String text,
            float right,
            float rowY,
            int color
    ) {
        rowTextRight(graphics, text, right, rowY, ROW_HEIGHT, color);
    }

    /** Строка с заданной высотой, прижатая к правому краю. */
    public static void rowTextRight(
            GuiGraphics graphics,
            String text,
            float right,
            float rowY,
            float rowHeight,
            int color
    ) {
        rowText(
                graphics,
                text,
                right - width(text),
                rowY,
                rowHeight,
                color
        );
    }

    /**
     * Остаток времени в формате m:ss.
     * Секунды округляются вверх, чтобы последний тик отображался как 0:01.
     */
    public static String clock(int ticks) {
        int seconds = Math.max(0, (ticks + 19) / 20);
        int rest = seconds % 60;

        return (seconds / 60)
                + ":"
                + (rest < 10 ? "0" : "")
                + rest;
    }
}
