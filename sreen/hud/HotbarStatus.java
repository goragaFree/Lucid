package dev.lucid.sreen.hud;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.Hud;
import dev.lucid.util.Wrapper;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import dev.lucid.util.render.ColorUtil;

/** Только цветные квадраты HP/защиты/еды и тонкая полоса опыта, без карточек и значков. */
public final class HotbarStatus implements Wrapper {
    static final float SQUARE_SIZE = 6f;
    static final float SQUARE_PITCH = 8f;
    static final float SQUARE_RADIUS = 1.5f;
    private static final int HEALTH = 0xE6E46B71;
    private static final int DEFENSE = 0xE69DBBCB;
    private static final int FOOD = 0xE6E3BD61;
    private static final int ABSORPTION = 0xE6F2D580;
    private static final int LABEL = 0xE6E6E6E8;

    private HotbarStatus() {}

    public static boolean active() {
        var client = Lucid.getInstance();
        if (client == null || client.getModuleManager() == null) return false;
        Hud hud = client.getModuleManager().get(Hud.class);
        return hud != null && hud.showsHotbar();
    }

    public static void render(GuiGraphics graphics) {
        if (mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        Hotbar.Anchor anchor = Hotbar.position(graphics);
        float x = anchor.x();
        float y = anchor.y() - 17f;
        float maximum = Math.max(1f, mc.player.getMaxHealth());
        float health = Math.max(0f, Math.min(maximum, mc.player.getHealth()));
        float absorption = Math.max(0f, mc.player.getAbsorptionAmount());
        int armor = Math.max(0, mc.player.getArmorValue());
        int goldRows = visibleAbsorptionRows(graphics.guiHeight());
        // HP слева, еда справа. Золото — над HP, защита — над золотом.
        segments(graphics, x + 1f, y, health, maximum, HEALTH);
        segments(graphics, x + 103f, y, mc.player.getFoodData().getFoodLevel(), 20f, FOOD);
        segments(graphics, x + 1f, y - 10f * (goldRows + 1), armor, 20f, DEFENSE);
        absorptionSegments(graphics, x + 1f, y - 10f, absorption, goldRows);
    }

    static int absorptionCellCount(float value) {
        if (!Float.isFinite(value) || value <= 0f) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(value / 2d));
    }

    static int absorptionRows(float value) {
        return (int) ((absorptionCellCount(value) + 9L) / 10L);
    }

    static float absorptionCellFill(float value, int index) {
        if (!Float.isFinite(value) || value <= 0f || index < 0) return 0f;
        return (float) Math.max(0d, Math.min(1d, value / 2d - index));
    }

    static int visibleAbsorptionRows(int screenHeight) {
        if (mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return 0;
        // Ограничение только границами экрана, а не десятью сердцами.
        return Math.min(absorptionRows(mc.player.getAbsorptionAmount()), Math.max(0, (screenHeight - 64) / 10));
    }

    private static void absorptionSegments(GuiGraphics g, float x, float y, float value, int rows) {
        int count = (int) Math.min(absorptionCellCount(value), rows * 10L);
        for (int i = 0; i < count; i++) {
            float fill = absorptionCellFill(value, i) * SQUARE_SIZE;
            if (fill <= 0f) continue;
            // Только реально имеющиеся золотые HP, без пустых фоновых квадратов.
            Render2D.roundedRect(g, x + (i % 10) * SQUARE_PITCH, y - (i / 10) * 10f,
                    fill, SQUARE_SIZE, Math.min(SQUARE_RADIUS, fill * .5f), ABSORPTION);
        }
    }

    public static void experience(GuiGraphics graphics) {
        if (mc.player == null || mc.gameMode == null || !mc.gameMode.hasExperience()) return;
        Hotbar.Anchor anchor = Hotbar.position(graphics);
        float x = anchor.x();
        float y = anchor.y() - 6f;
        Render2D.roundedRectOutlined(graphics, x, y, Hotbar.WIDTH, 4f, 2f,
                HudCard.OUTLINE, HudCard.BACKGROUND, HudCard.BORDER);
        float width = 180f * ratio(mc.player.experienceProgress, 1f);
        if (width > 0f) Render2D.roundedRect(graphics, x + 1f, y + 1f,
                width, 2f, Math.min(1f, width * 0.5f), HudCard.ACCENT_LIGHT);
        if (mc.player.experienceLevel == 0) level(graphics, 0);
    }

    public static void level(GuiGraphics graphics, int level) {
        String text = Integer.toString(Math.max(0, level));
        Hotbar.Anchor anchor = Hotbar.position(graphics);
        float center = anchor.x() + Hotbar.WIDTH * 0.5f;
        Render2D.textInRow(graphics, Fonts.medium(), text,
                center - Armor.visibleTextCenter(Fonts.medium(), text, 7f),
                anchor.y() - 16f, 9f, 7f, LABEL);
    }

    /** Десять квадратов с зазором 2px. Пустые — тёмный оттенок того же цвета. */
    private static void segments(GuiGraphics g, float x, float y, float value, float maximum, int color) {
        for (int i = 0; i < 10; i++) {
            float cellX = x + i * SQUARE_PITCH;
            float fraction = segmentFill(value, maximum, i);
            if (fraction < 1f) Render2D.roundedRect(g, cellX, y, SQUARE_SIZE, SQUARE_SIZE,
                    SQUARE_RADIUS, ColorUtil.multiplyAlpha(color, 0.45f));
            float fill = fraction * SQUARE_SIZE;
            if (fill > 0f) Render2D.roundedRect(g, cellX, y, fill, SQUARE_SIZE,
                    Math.min(SQUARE_RADIUS, fill * 0.5f), color);
        }
    }

    static float ratio(float value, float maximum) {
        if (!Float.isFinite(value) || !Float.isFinite(maximum) || maximum <= 0f) return 0f;
        return Math.max(0f, Math.min(1f, value / maximum));
    }

    static float segmentFill(float value, float maximum, int index) {
        return Math.max(0f, Math.min(1f, ratio(value, maximum) * 10f - index));
    }

}
