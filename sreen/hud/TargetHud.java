package dev.lucid.sreen.hud;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.util.Wrapper;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import dev.lucid.util.render.pipelines.PlayerHeadRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.lang.ref.WeakReference;
import java.util.UUID;

/** Компоновка Rich: лицо, имя, горизонтальный HP-бар и шесть предметов с крестиками. */
public final class TargetHud implements Wrapper {
    public static final float WIDTH = 109f;
    public static final float HEIGHT = 44f;
    static final float ITEM_SIZE = 8f;
    static final float ITEM_PITCH = 11f;
    static final float BAR_WIDTH = 61f;
    static final float BAR_HEIGHT = 4f;
    private static final float RADIUS = HudCard.RADIUS;
    private static final int EMPTY_CROSS_COLOR = (HudCard.TEXT_SECONDARY & 0x00FFFFFF) | 0xCC000000;
    private UUID shownTarget;
    private long animationAt;
    private float displayedHealth;
    private float displayedAbsorption;
    private static final String PLAYER_ICON = "\uE100";
    private static final String HEART_ICON = "\uE101";
    private static final int GOLD = 0xFFE8C76A;
    private static final long HOLD_NS = 3_000_000_000L;
    private static WeakReference<AbstractClientPlayer> recent = new WeakReference<>(null);
    private static long seenAt;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static void remember(Entity entity) {
        if (entity instanceof AbstractClientPlayer player && player != mc.player) {
            recent = new WeakReference<>(player);
            seenAt = System.nanoTime();
        }
    }

    private static AbstractClientPlayer target(boolean preview) {
        if (mc.player == null || mc.level == null) return null;
        if (preview) return mc.player;
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof AbstractClientPlayer player
                && player != mc.player && player.isAlive() && !player.isRemoved()) remember(player);
        AbstractClientPlayer player = recent.get();
        if (player == null || player.level() != mc.level || !player.isAlive() || player.isRemoved()
                || System.nanoTime() - seenAt > HOLD_NS) {
            recent.clear();
            return null;
        }
        return player;
    }

    public void render(GuiGraphics graphics, boolean preview) {
        AbstractClientPlayer target = target(preview);
        if (target == null) return;
        DragHud.Rect placed = DragHud.place(DragHud.Slot.TARGET, graphics,
                (graphics.guiWidth() - WIDTH) * 0.5f,
                Math.max(4f, Math.min(graphics.guiHeight() * 0.5f + 30f,
                        graphics.guiHeight() - HEIGHT - 96f)), WIDTH, HEIGHT);
        float x = Math.round(placed.x());
        float y = Math.round(placed.y());
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            renderCard(graphics, target);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void renderCard(GuiGraphics graphics, AbstractClientPlayer target) {
        // Один общий фон — ровно цвет основной части Hotkeys, без тёмной секции головы.
        Render2D.roundedRectOutlined(graphics, 0f, 0f, WIDTH, HEIGHT, RADIUS, 0.6f,
                HudCard.BACKGROUND, HudCard.BORDER);
        float health = finitePositive(target.getHealth());
        float maximum = Math.max(1f, finitePositive(target.getMaxHealth()));
        float absorption = finitePositive(target.getAbsorptionAmount());
        BarParts parts = animatedParts(target, barParts(health, maximum, absorption));
        drawBar(graphics, 40f, 26f, parts);
        for (int i = 0; i < 6; i++) {
            if (stack(target, i).isEmpty()) Armor.drawCross(graphics, 42f + i * ITEM_PITCH, 37f, 0.65f, EMPTY_CROSS_COLOR);
        }
        graphics.nextStratum();
        graphics.guiRenderState.submitGuiElement(new PlayerHeadRenderState(graphics,
                target.getSkin().body().texturePath(), 6f, 8f, 28f, 3f));
        for (int i = 0; i < 6; i++) {
            ItemStack stack = stack(target, i);
            if (stack.isEmpty()) continue;
            graphics.pose().pushMatrix();
            try {
                // 8x8, масштаб ровно 1/2, целочисленные начала — без общего уменьшения HUD.
                graphics.pose().translate(38f + i * ITEM_PITCH, 33f);
                graphics.pose().scale(ITEM_SIZE / 16f, ITEM_SIZE / 16f);
                graphics.renderItem(stack, 0, 0);
            } finally {
                graphics.pose().popMatrix();
            }
        }
        graphics.nextStratum();
        Render2D.text(graphics, Fonts.targetIcons(), PLAYER_ICON, 38f, 4f, 8f, HudCard.ACCENT_LIGHT);
        Render2D.text(graphics, Fonts.targetIcons(), HEART_ICON, 38.5f, 15.5f, 7f, HudCard.ACCENT_LIGHT);
        String name = fitName(target.getName().getString(), Fonts.richBold(), 7f, 54f);
        alignedText(graphics, Fonts.richBold(), name, 48f, 8f, 7f, HudCard.TEXT);
        alignedText(graphics, Fonts.richBold(), "HEALTH", 48f, 19f, 5.2f, HudCard.TEXT_SECONDARY);
        drawHealthValue(graphics, health, absorption);
    }

    private static ItemStack stack(AbstractClientPlayer target, int index) {
        return index < 4 ? target.getItemBySlot(ARMOR[index])
                : index == 4 ? target.getMainHandItem() : target.getOffhandItem();
    }

    // Rich's atlas has no capHeight: its ascender is not the visible height of capitals.
    private static void alignedText(GuiGraphics graphics, FontAtlas font, String text,
                                    float x, float centerY, float size, int color) {
        float cap = font == Fonts.richBold() ? .7152575f * size : font.capHeight(size);
        float baseline = centerY + cap * .5f;
        Render2D.text(graphics, font, text, x, baseline - font.ascender(size), size, color);
    }

    static float healthTextWidth(String hp, String bonus, float size) {
        float width = Fonts.richBold().width(hp, size);
        if (!bonus.isEmpty()) width += Fonts.richBold().width("  " + bonus, size)
                + Fonts.semibold().width("+", size);
        return width;
    }

    private static void drawHealthValue(GuiGraphics graphics, float health, float absorption) {
        String hp = number(health);
        String bonus = absorption > 0f ? number(absorption) : "";
        float size = 6f;
        float width = healthTextWidth(hp, bonus, size);
        if (width > 27f) {
            size *= 27f / width;
            width = healthTextWidth(hp, bonus, size);
        }
        float left = 88f - width * .5f;
        alignedText(graphics, Fonts.richBold(), hp, left, 19f, size, HudCard.TEXT);
        if (!bonus.isEmpty()) {
            float gap = Fonts.richBold().width(" ", size);
            float plusX = left + Fonts.richBold().width(hp, size) + gap;
            // Rich Bold has no '+' glyph. Use a real SDF plus, never the '?' fallback.
            alignedText(graphics, Fonts.semibold(), "+", plusX, 19f, size, GOLD);
            alignedText(graphics, Fonts.richBold(), bonus,
                    plusX + Fonts.semibold().width("+", size) + gap, 19f, size, GOLD);
        }
    }

    private BarParts animatedParts(AbstractClientPlayer target, BarParts desired) {
        long now = System.nanoTime();
        if (!target.getUUID().equals(shownTarget) || animationAt == 0L || now - animationAt > 500_000_000L) {
            shownTarget = target.getUUID();
            displayedHealth = desired.health();
            displayedAbsorption = desired.absorption();
        } else {
            float seconds = Math.max(0f, Math.min(.1f, (now - animationAt) / 1_000_000_000f));
            displayedHealth = smoothFraction(displayedHealth, desired.health(), seconds);
            displayedAbsorption = smoothFraction(displayedAbsorption, desired.absorption(), seconds);
        }
        animationAt = now;
        return new BarParts(displayedHealth, displayedAbsorption);
    }

    static float smoothFraction(float current, float desired, float seconds) {
        float amount = (float) -Math.expm1(-Math.max(0f, seconds) / .08f);
        return current + (desired - current) * amount;
    }

    /** Максимум HP + текущая ёмкость поглощения: здоровье и бонус занимают одну шкалу. */
    static BarParts barParts(float health, float maximum, float absorption) {
        double max = Math.max(1d, finitePositive(maximum));
        double hp = Math.min(max, finitePositive(health));
        double gold = finitePositive(absorption);
        double capacity = max + gold;
        return new BarParts((float) (hp / capacity), (float) (gold / capacity));
    }

    record BarParts(float health, float absorption) {}

    private static float finitePositive(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    static String number(float value) {
        return Long.toString((long) Math.ceil(finitePositive(value)));
    }

    static String fitName(String name, FontAtlas font, float size, float width) {
        if (font.width(name, size) <= width) return name;
        int end = name.length();
        while (end > 0) {
            end = name.offsetByCodePoints(end, -1);
            String candidate = name.substring(0, end) + "...";
            if (font.width(candidate, size) <= width) return candidate;
        }
        return "...";
    }

    private static void drawBar(GuiGraphics g, float x, float y, BarParts parts) {
        Render2D.roundedRect(g, x, y, BAR_WIDTH, BAR_HEIGHT, 2f, 0x80484A55);
        float healthWidth = Math.min(BAR_WIDTH, parts.health() * BAR_WIDTH);
        float goldWidth = Math.min(BAR_WIDTH - healthWidth, parts.absorption() * BAR_WIDTH);
        // Два цвета одной горизонтальной шкалы. На стыке нет внутренних скруглений.
        if (healthWidth > 0f) {
            g.enableScissor((int) x, (int) y, (int) Math.ceil(x + healthWidth), (int) (y + BAR_HEIGHT));
            try { Render2D.roundedRect(g, x, y, BAR_WIDTH, BAR_HEIGHT, 2f, Theme.ACCENT); }
            finally { g.disableScissor(); }
        }
        if (goldWidth > 0f) {
            g.enableScissor((int) Math.floor(x + healthWidth), (int) y,
                    (int) Math.ceil(x + healthWidth + goldWidth), (int) (y + BAR_HEIGHT));
            try { Render2D.roundedRect(g, x, y, BAR_WIDTH, BAR_HEIGHT, 2f, GOLD); }
            finally { g.disableScissor(); }
        }
    }
}
