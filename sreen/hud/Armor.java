package dev.lucid.sreen.hud;

import dev.lucid.util.Wrapper;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Fonts;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Glyph;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Четыре слота брони с нейтральными процентами; пустой слот обозначен крестиком. */
public final class Armor implements Wrapper {
    public static final float WIDTH = 82f;
    public static final float HEIGHT = 22f;
    private static final float SLOT_SIZE = 20f;
    private static final float EDGE = 1f;
    private static final float ICON_SIZE = 14f;
    private static final float FONT_SIZE = 5.8f;
    private static final float ICON_TOP = 2f;
    private static final int PERCENT_COLOR = 0xE6E6E6E8;
    private static final int CROSS_COLOR = (HudCard.TEXT_SECONDARY & 0x00FFFFFF) | 0xE6000000;
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public void render(GuiGraphics graphics) {
        if (mc.player == null) return;
        DragHud.Rect placed = DragHud.place(DragHud.Slot.ARMOR, graphics,
                (graphics.guiWidth() - WIDTH) * 0.5f,
                Math.max(0f, graphics.guiHeight() - HEIGHT - HudPanel.BOTTOM_RESERVE
                        - (HotbarStatus.active() ? HotbarStatus.visibleAbsorptionRows(graphics.guiHeight()) * 10f : 0f)), WIDTH, HEIGHT);
        float x = Math.round(placed.x());
        float y = Math.round(placed.y());
        Render2D.roundedRectOutlined(graphics, x, y, WIDTH, HEIGHT, HudCard.RADIUS, HudCard.OUTLINE,
                HudCard.BACKGROUND, HudCard.BORDER);

        ItemStack[] stacks = new ItemStack[SLOTS.length];
        // Сначала фон/крестики, затем предметы, затем текст отдельным слоем.
        for (int i = 0; i < SLOTS.length; i++) {
            stacks[i] = mc.player.getItemBySlot(SLOTS[i]);
            if (stacks[i].isEmpty()) drawCross(graphics, centerX(x, i), y + HEIGHT * 0.5f);
        }
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i].isEmpty()) continue;
            graphics.pose().pushMatrix();
            try {
                // Целочисленный левый верхний угол: не смещаем текстуру на полпикселя.
                graphics.pose().translate(centerX(x, i) - ICON_SIZE * 0.5f, y + ICON_TOP);
                graphics.pose().scale(ICON_SIZE / 16f, ICON_SIZE / 16f);
                graphics.renderItem(stacks[i], 0, 0);
            } finally {
                graphics.pose().popMatrix();
            }
        }
        graphics.nextStratum();
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i].isEmpty()) continue;
            String text = durabilityText(stacks[i].getMaxDamage(), stacks[i].getDamageValue());
            // Приглушённый светло-серый, без изменения цвета по износу и без подложек.
            Render2D.textInRow(graphics, Fonts.medium(), text,
                    centerX(x, i) - visibleTextCenter(Fonts.medium(), text, FONT_SIZE),
                    y + 15f, 6f, FONT_SIZE, PERCENT_COLOR);
        }
    }

    /** Центр видимых глифов, без боковых пробелов advance у первого/последнего знака. */
    static float visibleTextCenter(FontAtlas font, String text, float size) {
        float pen = 0f;
        float left = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        int previous = -1;
        for (int index = 0; index < text.length();) {
            int code = text.codePointAt(index);
            index += Character.charCount(code);
            Glyph glyph = font.glyph(code);
            if (glyph == null) continue;
            pen += font.kerning(previous, code) * size;
            if (glyph.drawable()) {
                left = Math.min(left, pen + glyph.left() * size);
                right = Math.max(right, pen + glyph.right() * size);
            }
            pen += glyph.advance() * size;
            previous = code;
        }
        return Float.isFinite(left) ? (left + right) * 0.5f : pen * 0.5f;
    }

    private static float centerX(float x, int slot) {
        return x + EDGE + (slot + 0.5f) * SLOT_SIZE;
    }

    static void drawCross(GuiGraphics graphics, float centerX, float centerY) {
        drawCross(graphics, centerX, centerY, 1f, CROSS_COLOR);
    }

    static void drawCross(GuiGraphics graphics, float centerX, float centerY, float scale, int color) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(centerX, centerY);
            graphics.pose().scale(scale, scale);
            graphics.pose().rotate((float) (Math.PI / 4d));
            Render2D.rect(graphics, -5.2f, -0.55f, 10.4f, 1.1f, color);
            Render2D.rect(graphics, -0.55f, -5.2f, 1.1f, 10.4f, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    static String durabilityText(int maxDamage, int damage) {
        // Например, тыква в слоте головы не имеет шкалы прочности.
        if (maxDamage <= 0) return "--";
        long remaining = Math.max(0L, (long) maxDamage - Math.max(0, damage));
        int percent = (int) Math.round(remaining * 100d / maxDamage);
        return Math.max(0, Math.min(100, percent)) + "%";
    }
}
