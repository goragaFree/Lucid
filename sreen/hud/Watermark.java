package dev.lucid.sreen.hud;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.util.Wrapper;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import dev.lucid.util.render.font.Icons;
import net.minecraft.client.gui.GuiGraphics;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Ватермарка Rich, адаптированная к GuiGraphics и Mojang mappings Lucid. */
public class Watermark implements Wrapper {
    private static final String BRAND = "Lucid";
    private static final float HEIGHT = 23f;
    private static final float PAD = 5f;
    private static final float TILE = 16f;
    private static final float GAP = 8f;
    private static final float BRAND_SIZE = 7f;
    private static final float NICK_SIZE = 6.4f;
    private static final float LABEL_SIZE = 4.8f;
    private static final float VALUE_SIZE = 6.5f;
    private static final float TRACKING = 0.9f;
    private static final long ANIM_NS = 200_000_000L;
    private static final float ANIM_OFFSET = 7f;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] LABELS = {"PING", "FPS", "TIME", "SERVER"};
    /**
     * Шаблоны ширины колонок: как {@code HudTimerLayout.width("00:00")} у Potions.
     * В rich_bold цифры разной ширины, поэтому мерить колонку по текущему значению
     * нельзя — плашка уезжает всякий раз, когда в числе появляется единица или восьмёрка.
     * Пустой шаблон у SERVER: это текст, он и так не меняется от кадра к кадру.
     */
    private static final String[] PATTERNS = {"888ms", "888", "88:88", ""};

    // Те же заливки и контур, что у Hotkeys; без новой системы тем.
    private static final float RADIUS = HudPanel.RADIUS;
    private static final float OUTLINE = HudPanel.OUTLINE;
    private static final int TEXT = 0xFFF0F0F6;
    private static final int DIM = 0xFF9A9CA8;
    private static final int MUTE = 0xFF686A76;

    private final RollingValue fpsValue = new RollingValue();
    private final RollingValue timeValue = new RollingValue();

    /** Ширины колонок живут между кадрами: обратный отсчёт разрядов не сжимает плашку. */
    private final float[] widths = new float[LABELS.length];
    /** Настоящее содержимое — ник и сервер; только при его смене ширины считаются заново. */
    private String layoutKey = "";

    public void render(GuiGraphics graphics) {
        FontAtlas font = Fonts.richBold();
        String nick = mc.getUser().getName();
        String fps = Integer.toString(mc.getFps());
        String time = LocalTime.now().format(CLOCK);
        String[] values = {getPing() + "ms", fps, time, getServerIP()};
        long now = System.nanoTime();
        fpsValue.update(fps, now);
        timeValue.update(time, now);
        float fpsProgress = fpsValue.progress(now);
        float timeProgress = timeValue.progress(now);

        float brandWidth = Math.max(trackedWidth(BRAND, BRAND_SIZE, TRACKING),
                font.width(nick, NICK_SIZE));
        // Пересчитываем вёрстку только при смене ника или сервера: цифры в неё не входят.
        String key = nick + '\0' + values[3];
        boolean relayout = !key.equals(layoutKey);
        layoutKey = key;
        float statsWidth = GAP * (LABELS.length - 1);
        for (int i = 0; i < LABELS.length; i++) {
            // Равные ячейки цифр и шаблон вместо текущего значения: ширина колонки
            // не зависит от того, какие именно цифры нарисованы в этом кадре.
            float measured = Math.max(trackedWidth(LABELS[i], LABEL_SIZE, 0.7f),
                    Math.max(cellsWidth(PATTERNS[i]), cellsWidth(values[i])));
            if (relayout) {
                widths[i] = measured;
            } else {
                // Разряд мог вырасти — расширяем; обратно уже не сжимаем.
                widths[i] = Math.max(widths[i], measured);
            }
            statsWidth += widths[i];
        }
        float leftWidth = Math.round(PAD + TILE + 6f + brandWidth + GAP);
        float width = leftWidth + GAP + statsWidth + PAD;
        // Сохраняем позицию Lucid сверху по центру: слева уже находится Potions.
        float x = Math.max(0f, Math.round((graphics.guiWidth() - width) * 0.5f));
        float y = 4f;
        drawBackground(graphics, x, y, width, leftWidth);

        float tileX = x + PAD;
        float tileY = y + (HEIGHT - TILE) * 0.5f;
        drawLogo(graphics, tileX, tileY);

        float brandX = tileX + TILE + 6f;
        drawTracked(graphics, BRAND, brandX, y + 3.8f, BRAND_SIZE, TRACKING, TEXT);
        drawText(graphics, nick, brandX, y + 13f, NICK_SIZE, DIM);
        float cursor = x + leftWidth + GAP;
        for (int i = 0; i < LABELS.length; i++) {
            drawTracked(graphics, LABELS[i], cursor, y + 4f, LABEL_SIZE, 0.7f, MUTE);
            float right = cursor + widths[i];
            if (i == 1 || i == 2) {
                RollingValue rolling = i == 1 ? fpsValue : timeValue;
                float progress = i == 1 ? fpsProgress : timeProgress;
                // Анимация не вылезает за строку значений и не перекрывает подписи.
                graphics.enableScissor((int) Math.floor(cursor - 1f), (int) Math.floor(y + 11f),
                        (int) Math.ceil(right + 1f), (int) Math.ceil(y + HEIGHT - 1f));
                if (rolling.previous.isEmpty() || progress >= 1f) {
                    // В покое — та же сетка и то же прижатие вправо, что в анимации:
                    // иначе в последнем кадре строка прыгала бы на целую цифру.
                    drawCellsRight(graphics, rolling.current, right, y + 12.5f, TEXT);
                } else {
                    drawRoll(graphics, rolling, right, y + 12.5f, progress, PATTERNS[i]);
                }
                graphics.disableScissor();
            } else if (i == 0) {
                // У пинга нет анимации, но цифры всё равно в равных ячейках.
                drawCellsRight(graphics, values[i], right, y + 12.5f, TEXT);
            } else {
                drawText(graphics, values[i], cursor, y + 12.5f, VALUE_SIZE, TEXT);
            }
            cursor += widths[i] + GAP;
        }
    }

    /** Общий внешний контур; между цветами прямой стык без линии и скруглений. */
    private void drawBackground(GuiGraphics graphics, float x, float y, float width, float leftWidth) {
        float inset = Render2D.outlineInset(width, HEIGHT, OUTLINE);
        float radius = Math.max(0f, RADIUS - inset);
        // Как в HudPanel.draw: сначала тело, поверх него более тёмная шапка.
        Render2D.roundedRect(graphics, x + inset, y + inset,
                width - inset * 2f, HEIGHT - inset * 2f, radius, HudPanel.BACKGROUND);
        // Обрезаем полную скруглённую заливку по прямой границе левой части.
        // Скругления остаются только на внешних углах всей плашки.
        graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.round(x + leftWidth), (int) Math.ceil(y + HEIGHT));
        Render2D.roundedRect(graphics, x + inset, y + inset,
                width - inset * 2f, HEIGHT - inset * 2f, radius, HudPanel.HEADER_BACKGROUND);
        graphics.disableScissor();
        Render2D.roundedOutline(graphics, x, y, width, HEIGHT, RADIUS, OUTLINE, HudPanel.BORDER);
    }

    private void drawLogo(GuiGraphics graphics, float x, float y) {
        float size = TILE;
        Render2D.icon(graphics, Icons.LOGO, x, y, size, Theme.ACCENT);
        // В исходном Lucid буква L состоит из ножки LOGO и основания LOGO_FOOT.
        Render2D.icon(graphics, Icons.LOGO_FOOT, x, y, size, Theme.ACCENT);
    }

    /** Rich использует baseline = y + 0.95 * size; Lucid — ascender из метрик. */
    private static void drawText(GuiGraphics graphics, String text, float x, float y,
                                 float size, int color) {
        FontAtlas font = Fonts.richBold();
        Render2D.text(graphics, font, text, x, y + 0.95f * size - font.ascender(size), size, color);
    }

    private static float trackedWidth(String text, float size, float tracking) {
        String upper = text.toUpperCase(Locale.ROOT);
        float width = 0f;
        for (int i = 0; i < upper.length(); i++) {
            width += Fonts.richBold().width(upper.substring(i, i + 1), size);
            if (i + 1 < upper.length()) width += tracking;
        }
        return width;
    }

    private static void drawTracked(GuiGraphics graphics, String text, float x, float y,
                                    float size, float tracking, int color) {
        String upper = text.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            String letter = upper.substring(i, i + 1);
            drawText(graphics, letter, x, y, size, color);
            x += Fonts.richBold().width(letter, size) + tracking;
        }
    }

    /** Самая широкая цифра rich_bold: под неё выделяется ячейка для всех цифр. */
    private static float digitCell() {
        FontAtlas font = Fonts.richBold();
        float max = 0f;
        for (char c = '0'; c <= '9'; c++) {
            max = Math.max(max, font.width(String.valueOf(c), VALUE_SIZE));
        }
        return max;
    }

    private static float cellWidth(char c) {
        return c >= '0' && c <= '9'
                ? digitCell()
                : Fonts.richBold().width(String.valueOf(c), VALUE_SIZE);
    }

    /** Ширина строки в равных ячейках — не зависит от того, какие цифры в ней стоят. */
    private static float cellsWidth(String text) {
        float width = 0f;
        for (int i = 0; i < text.length(); i++) width += cellWidth(text.charAt(i));
        return width;
    }

    /** Узкая «1» центрируется в ячейке широкой «8» и не сдвигает соседей. */
    private static void drawCell(GuiGraphics graphics, char c, float x, float cell, float y,
                                 float offset, int color, float alpha) {
        String ch = String.valueOf(c);
        float glyph = Fonts.richBold().width(ch, VALUE_SIZE);
        drawText(graphics, ch, x + (cell - glyph) * 0.5f, y + offset, VALUE_SIZE,
                alpha >= 1f ? color : withAlpha(color, alpha));
    }

    private static void drawCells(GuiGraphics graphics, String text, float x, float y, int color) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float cell = cellWidth(c);
            drawCell(graphics, c, x, cell, y, 0f, color, 1f);
            x += cell;
        }
    }

    /** Числа прижаты вправо: смена разряда не переставляет остальные цифры. */
    private static void drawCellsRight(GuiGraphics graphics, String text, float right, float y, int color) {
        drawCells(graphics, text, right - cellsWidth(text), y, color);
    }

    /**
     * Сетка ячеек из шаблона: уходящая и приходящая цифры сидят в одной позиции,
     * поэтому «99 → 100» не перескакивает. Обе строки дополнены пробелами слева,
     * пустая ячейка остаётся в вёрстке и после окончания анимации.
     */
    private static void drawRoll(GuiGraphics graphics, RollingValue value, float right, float y,
                                 float progress, String pattern) {
        int cells = Math.max(pattern.length(),
                Math.max(value.current.length(), value.previous.length()));
        float[] grid = new float[cells];
        float total = 0f;
        for (int i = 0; i < cells; i++) {
            // Шаблон прижат к правому краю сетки: двоеточие в HH:mm всегда на своём месте.
            int index = i - (cells - pattern.length());
            char c = index >= 0 ? pattern.charAt(index) : '0';
            grid[i] = cellWidth(c);
            total += grid[i];
        }
        String next = " ".repeat(cells - value.current.length()) + value.current;
        String old = " ".repeat(cells - value.previous.length()) + value.previous;
        float eased = 1f - (float) Math.pow(1f - progress, 3);
        float x = right - total;
        for (int i = 0; i < cells; i++) {
            char nc = next.charAt(i);
            char oc = old.charAt(i);
            if (nc == oc) {
                if (nc != ' ') drawCell(graphics, nc, x, grid[i], y, 0f, TEXT, 1f);
            } else {
                if (oc != ' ') drawCell(graphics, oc, x, grid[i], y,
                        eased * ANIM_OFFSET, TEXT, 1f - eased);
                if (nc != ' ') drawCell(graphics, nc, x, grid[i], y,
                        -(1f - eased) * ANIM_OFFSET, TEXT, eased);
            }
            x += grid[i];
        }
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round((color >>> 24) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private int getPing() {
        var connection = mc.getConnection();
        if (connection == null || mc.player == null) return 0;
        var info = connection.getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    private String getServerIP() {
        var server = mc.getCurrentServer();
        return server == null ? "localhost" : server.ip;
    }

    private static final class RollingValue {
        private String current = "";
        private String previous = "";
        private long started;

        private void update(String value, long now) {
            if (value.equals(current)) return;
            previous = current;
            current = value;
            started = now;
        }

        private float progress(long now) {
            return Math.max(0f, Math.min(1f, (now - started) / (float) ANIM_NS));
        }
    }
}
