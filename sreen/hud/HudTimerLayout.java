package dev.lucid.sreen.hud;

import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphics;

/** Стабильные размеры таймера: равные ячейки цифр и ширина панели без скачков от отсчёта. */
public final class HudTimerLayout {
    private String contents;
    private float panelWidth;
    private float timeWidth;

    /** Пересчитываем ширину заново только при изменении настоящего содержимого списка. */
    public float fit(String contents, float leftWidth, float desiredTimeWidth, String title) {
        if (!contents.equals(this.contents)) {
            this.contents = contents;
            timeWidth = desiredTimeWidth;
            panelWidth = HudCard.width(leftWidth, timeWidth, title);
        } else {
            // Сервер может продлить таймер до большего количества разрядов.
            // Разрешаем расширение под новое значение, но не сжатие при обратном отсчёте.
            timeWidth = Math.max(timeWidth, desiredTimeWidth);
            panelWidth = Math.max(panelWidth, HudCard.width(leftWidth, timeWidth, title));
        }
        return panelWidth;
    }

    public void reset() {
        contents = null;
        panelWidth = 0f;
        timeWidth = 0f;
    }

    private static float digitWidth() {
        float max = 0f;
        for (char c = '0'; c <= '9'; c++) max = Math.max(max, HudCard.width(String.valueOf(c)));
        return max;
    }

    public static float width(String text) {
        float digit = digitWidth();
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += c >= '0' && c <= '9' ? digit : HudCard.width(String.valueOf(c));
        }
        return width;
    }

    /** Рисуем цифры в равных ячейках; сам шрифт остаётся прежним, без подложек. */
    public static void drawRight(GuiGraphics graphics, String text, float right, float rowY,
                                 float rowHeight, int color) {
        float digit = digitWidth();
        float x = right - width(text);
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            float glyphWidth = HudCard.width(ch);
            char c = text.charAt(i);
            float cell = c >= '0' && c <= '9' ? digit : glyphWidth;
            Render2D.textInRow(graphics, HudCard.font(), ch, x + (cell - glyphWidth) * 0.5f,
                    rowY, rowHeight, HudCard.FONT_SIZE, color);
            x += cell;
        }
    }
}
