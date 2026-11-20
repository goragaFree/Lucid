
package dev.lucid.sreen.hud;

import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Геометрия карточек из нового архива, адаптированная для GuiGraphics 1.21.11.
 * Общая геометрия и палитра текстовых HUD-панелей (Potions, Hotkeys, Cooldowns).
 *
 * <p>Здесь находятся общие параметры панелей: отступы, шрифты, цвета,
 * отрисовка фона с шапкой и вспомогательные методы для строк.</p>
 *
 * <p>Палитра Lucid: тёмная шапка и более светлая полупрозрачная
 * основная часть, сквозь которую виден мир Minecraft.</p>
 */
public final class HudCard {

    public static final float PADDING = 7.5f;
    public static final float ROW_HEIGHT = 13f;
    public static final float GAP = 13f;
    public static final float RADIUS = 6f;
    public static final float OUTLINE = 1f;
    public static final float FONT_SIZE = 8.5f;
    public static final float HEADER_HEIGHT = 20f;
    public static final float TITLE_FONT_SIZE = 9.5f;
    public static final float BODY_TOP = 4f;
    public static final float BODY_BOTTOM = 5f;
    public static final float MIN_WIDTH = 96f;
    public static final float MARGIN = 4f;
    private static final float ICON_BOX_SIZE = 10f;
    private static final float ICON_SIZE = 8f;
    private static final float ICON_GAP = 4f;
    private static final float ICON_STROKE = 0.85f;
    public static final int BACKGROUND = HudPanel.BACKGROUND;
    public static final int HEADER_BACKGROUND = HudPanel.HEADER_BACKGROUND;
    public static final int BORDER = HudPanel.BORDER;
    public static final int TEXT = HudPanel.TEXT;
    public static final int TEXT_SECONDARY = HudPanel.TEXT_SECONDARY;
    public static final int ACCENT = HudPanel.ACCENT;
    public static final int ACCENT_LIGHT = dev.lucid.util.render.ColorUtil.interpolate(ACCENT, 0xFFFFFFFF, 0.2f);

    public enum Icon { POTION, KEYBOARD, HOURGLASS, INVENTORY, STAFF }

    private HudCard() {}

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
        float header = PADDING * 2 + ICON_BOX_SIZE + ICON_GAP - 2f
                + Fonts.semibold().width(title, TITLE_FONT_SIZE);

        return (float) Math.ceil(Math.max(MIN_WIDTH, Math.max(content, header)));
    }

    public static float height(int rows) {
        return height(rows, ROW_HEIGHT);
    }

    /** Сохраняем фактическую высоту пустой нижней части из предыдущей версии. */
    public static float emptyHeight() {
        return HEADER_HEIGHT + HudPanel.emptyHeight() - HudPanel.HEADER_HEIGHT;
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

        float iconBoxX = rx + PADDING - 2f;
        float iconBoxY = ry + (HEADER_HEIGHT - ICON_BOX_SIZE) * 0.5f;
        Icon icon = switch (title) {
            case "Hotkeys" -> Icon.KEYBOARD;
            case "Cooldowns" -> Icon.HOURGLASS;
            case "Staff" -> Icon.STAFF;
            default -> Icon.POTION;
        };
        drawIcon(graphics, icon, iconBoxX + (ICON_BOX_SIZE - ICON_SIZE) * 0.5f,
                iconBoxY + (ICON_BOX_SIZE - ICON_SIZE) * 0.5f, ICON_SIZE, ACCENT_LIGHT);
        Render2D.textInRow(graphics, Fonts.semibold(), title,
                iconBoxX + ICON_BOX_SIZE + ICON_GAP, ry, HEADER_HEIGHT, TITLE_FONT_SIZE, TEXT);

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
    /** Иконка в квадрате {@code size × size}; координаты внутри — доли от размера, как у SVG 10×10. */
    static void drawIcon(GuiGraphics graphics, Icon icon,
                         float x, float y, float size, int color) {
        float u = size / 10.0f;
        float stroke = ICON_STROKE * u;

        switch (icon) {
            case STAFF -> Render2D.text(graphics, Fonts.targetIcons(), "\uE102", x, y, size, color);
            case POTION -> {
                // Горлышко.
                Render2D.roundedOutline(graphics, x + 3.6f * u, y + 0.5f * u,
                        2.8f * u, 2.7f * u, 0.5f * u, stroke, color);
                // Колба: круглая обводка, низ залит «жидкостью».
                float r = 3.5f * u;
                float cx = x + 5.0f * u;
                float cy = y + 6.3f * u;
                clip(graphics, x, cy - 0.6f * u, size, size);
                try {
                    Render2D.roundedRect(graphics, cx - r, cy - r, r * 2.0f, r * 2.0f, r,
                            dev.lucid.util.render.ColorUtil.multiplyAlpha(color, 0.85f));
                } finally {
                    graphics.disableScissor();
                }
                Render2D.roundedOutline(graphics, cx - r, cy - r, r * 2.0f, r * 2.0f, r, stroke, color);
            }
            case KEYBOARD -> {
                Render2D.roundedOutline(graphics, x + 0.6f * u, y + 1.9f * u,
                        8.8f * u, 6.4f * u, 1.4f * u, stroke, color);
                float key = 1.1f * u;
                for (int i = 0; i < 3; i++) {
                    Render2D.roundedRect(graphics, x + (2.1f + i * 2.35f) * u, y + 3.4f * u,
                            key, key, 0.3f * u, color);
                }
                Render2D.roundedRect(graphics, x + 3.0f * u, y + 6.0f * u,
                        4.0f * u, 0.95f * u, 0.45f * u, color);
            }
            case HOURGLASS -> {
                // Песочные часы: крышка, дно и два треугольника колбы, нижний засыпан.
                Render2D.rect(graphics, x + 1.8f * u, y + 0.6f * u, 6.4f * u, stroke, color);
                Render2D.rect(graphics, x + 1.8f * u, y + 9.4f * u - stroke, 6.4f * u, stroke, color);
                // Колба полосками: в атласе нет треугольника произвольной формы.
                int steps = 7;
                float top = y + 1.5f * u;
                float half = 3.5f * u;
                float step = half / steps;
                for (int i = 0; i < steps; i++) {
                    float k = 1.0f - (i + 0.5f) / steps;
                    float w = (0.8f + 4.0f * k) * u;
                    float cx = x + 5.0f * u;
                    Render2D.rect(graphics, cx - w * 0.5f, top + i * step, w, step + 0.05f,
                            dev.lucid.util.render.ColorUtil.multiplyAlpha(color, 0.45f));
                    Render2D.rect(graphics, cx - w * 0.5f, top + half * 2.0f - (i + 1) * step, w, step + 0.05f,
                            color);
                }
            }
            case INVENTORY -> {
                // Сундук: корпус, линия крышки и замок посередине.
                Render2D.roundedOutline(graphics, x + 0.7f * u, y + 1.5f * u,
                        8.6f * u, 7.2f * u, 1.3f * u, stroke, color);
                Render2D.rect(graphics, x + 1.2f * u, y + 4.3f * u, 7.6f * u, stroke, color);
                Render2D.roundedRect(graphics, x + 4.1f * u, y + 3.5f * u,
                        1.8f * u, 2.4f * u, 0.4f * u, color);
            }
        }
    }
    private static void clip(GuiGraphics graphics, float x, float y, float width, float height) {
        graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(x + width), (int) Math.ceil(y + height));
    }

}
