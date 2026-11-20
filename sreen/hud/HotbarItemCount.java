package dev.lucid.sreen.hud;

import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Замена количества только внутри отрисовки предметов нашего хотбара. */
public final class HotbarItemCount {
    static final float FONT_SIZE = 7.5f;
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private HotbarItemCount() {}

    public static boolean begin() {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        return previous;
    }

    public static void end(boolean previous) {
        ACTIVE.set(previous);
    }

    public static boolean active() {
        return ACTIVE.get();
    }

    static String text(int count, String override) {
        return override != null ? override : count == 1 ? "" : Integer.toString(count);
    }

    public static void render(GuiGraphics graphics, ItemStack stack, int x, int y, String override) {
        String text = text(stack.getCount(), override);
        if (text.isEmpty()) return;
        // Как у ванильного количества: правый нижний угол, но атлас и шрифт Lucid.
        float left = x + 17f - Fonts.semibold().width(text, FONT_SIZE);
        Render2D.textInRow(graphics, Fonts.semibold(), text, left,
                y + 9f, 8f, FONT_SIZE, HudCard.TEXT);
    }
}
