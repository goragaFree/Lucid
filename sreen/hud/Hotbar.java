package dev.lucid.sreen.hud;

import dev.lucid.mixin.GuiHotbarAccess;
import dev.lucid.util.Wrapper;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** Девять ячеек в стиле Armor. Номер показывается только в пустой ячейке. */
public final class Hotbar implements Wrapper {
    public static final float WIDTH = 182f;
    public static final float HEIGHT = 22f;
    private static final float CELL = 20f;
    private static final float NUMBER_SIZE = 8f;
    private static final int NUMBER_COLOR = (HudCard.TEXT_SECONDARY & 0x00FFFFFF) | 0xE6000000;

    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (mc.player == null || mc.player.isSpectator() || mc.getCameraEntity() != mc.player) return;
        ItemStack offhand = mc.player.getOffhandItem();
        boolean left = mc.player.getMainArm() == HumanoidArm.RIGHT;
        float extra = offhand.isEmpty() ? 0f : 26f;
        Anchor anchor = position(graphics);
        float x = anchor.x();
        float y = anchor.y();
        float groupX = x - (left ? extra : 0f);
        HotbarStatus.render(graphics);
        panel(graphics, x, y, WIDTH);
        int selected = mc.player.getInventory().getSelectedSlot();
        Render2D.roundedOutline(graphics, x + 1f + selected * CELL, y + 1f,
                CELL, HEIGHT - 2f, HudCard.RADIUS - 1f, 1f, HudCard.ACCENT_LIGHT);
        float offX = left ? groupX : x + WIDTH + 4f;
        if (!offhand.isEmpty()) panel(graphics, offX, y, HEIGHT);
        // Штатный renderSlot сохраняет прочность, кулдаун и анимацию подбора;
        // только количество заменяется на наш шрифт в ограниченной области вызова.
        graphics.nextStratum();
        GuiHotbarAccess renderer = (GuiHotbarAccess) mc.gui;
        boolean previousCountScope = HotbarItemCount.begin();
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty()) renderer.lucid$renderHotbarSlot(graphics,
                        Math.round(x + 3f + i * CELL), Math.round(y + 3f), delta, mc.player, stack, i + 1);
            }
            if (!offhand.isEmpty()) renderer.lucid$renderHotbarSlot(graphics,
                    Math.round(offX + 3f), Math.round(y + 3f), delta, mc.player, offhand, 10);
        } finally {
            HotbarItemCount.end(previousCountScope);
        }
        graphics.nextStratum();
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) continue;
            String text = Integer.toString(i + 1);
            float centerX = x + 1f + (i + 0.5f) * CELL;
            Render2D.textInRow(graphics, Fonts.medium(), text,
                    centerX - Armor.visibleTextCenter(Fonts.medium(), text, NUMBER_SIZE),
                    y, HEIGHT, NUMBER_SIZE, NUMBER_COLOR);
        }
        // Настройка индикатора атаки HOTBAR не теряется при замене ванильной панели.
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float progress = mc.player.getAttackStrengthScale(0f);
            if (progress < 1f) {
                float barX = left ? x + WIDTH + 5f : x - 8f;
                Render2D.roundedRect(graphics, barX, y + 3f, 3f, 16f, 1f, HudCard.HEADER_BACKGROUND);
                float filled = Math.max(0f, Math.min(1f, progress)) * 16f;
                if (filled > 0f) Render2D.roundedRect(graphics, barX, y + 19f - filled,
                        3f, filled, 1f, HudCard.TEXT_SECONDARY);
            }
        }
    }

    /** Общая точка привязки для предметов, статусов и XP, в том числе в ванильном проходе XP. */
    public record Anchor(float x, float y) {}

    static float topInset(int goldRows) {
        return 27f + Math.max(0, goldRows) * 10f;
    }

    static Anchor mainAnchor(DragHud.Rect group, boolean offhandLeft, float extra, float top) {
        return new Anchor(Math.round(group.x()) + (offhandLeft ? extra : 0f), Math.round(group.y()) + top);
    }

    public static Anchor position(GuiGraphics graphics) {
        boolean offhandLeft = mc.player != null && mc.player.getMainArm() == HumanoidArm.RIGHT;
        float extra = mc.player == null || mc.player.getOffhandItem().isEmpty() ? 0f : 26f;
        float top = topInset(HotbarStatus.visibleAbsorptionRows(graphics.guiHeight()));
        float groupWidth = WIDTH + extra;
        float groupHeight = HEIGHT + top;
        DragHud.Rect placed = DragHud.place(DragHud.Slot.HOTBAR, graphics,
                (graphics.guiWidth() - WIDTH) * .5f - (offhandLeft ? extra : 0f),
                Math.max(0f, graphics.guiHeight() - groupHeight), groupWidth, groupHeight);
        return mainAnchor(placed, offhandLeft, extra, top);
    }

    private static void panel(GuiGraphics graphics, float x, float y, float width) {
        Render2D.roundedRectOutlined(graphics, x, y, width, HEIGHT, HudCard.RADIUS,
                HudCard.OUTLINE, HudCard.BACKGROUND, HudCard.BORDER);
    }
}
