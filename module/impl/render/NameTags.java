package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.WorldProjection;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Свои нейм-теги над игроками и выброшенными предметами.
 *
 * <p>Рисуется так же, как {@link EspOverlay}: точка над головой проецируется матрицами
 * кадра в координаты HUD, а плашка собирается обычным 2D-рендером. Каждая часть тега
 * включается своей галочкой, и плашка ужимается под то, что осталось:</p>
 * <ul>
 *     <li>Player: Nickname и Health — текст в плашке, Armor и Hand Item — ряд предметов над ней
 *     (Hand Item — обе руки: основная слева, вторая справа от брони);</li>
 *     <li>Item: Icon — иконка предмета, Nametags — его название и количество.</li>
 * </ul>
 * <p>Пока модуль показывает тег игроку, ванильный ник над ним прячется
 * (EntityRendererMixin), чтобы не было двух подписей.</p>
 */
public class NameTags extends Module implements HudElement {

    public static final String NICKNAME = "Nickname";
    public static final String HAND_ITEM = "Hand Item";
    public static final String ARMOR = "Armor";
    public static final String HEALTH = "Health";
    public static final String ICON = "Icon";
    public static final String NAMETAGS = "Nametags";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Размеры в GUI-пикселях — от стиля HUD, а не с рисунка.
    private static final float FONT_SIZE = 7.5F;
    private static final float PAD_X = 4F;
    private static final float PLATE_HEIGHT = 12F;
    private static final float RADIUS = 3F;
    private static final float ITEM_SIZE = 10F;
    private static final float ITEM_GAP = 1.5F;
    private static final float ROW_GAP = 2F;
    private static final float TEXT_GAP = 4F;
    private static final double MAX_DISTANCE = 64.0;
    /** На этой дистанции неадаптивный тег совпадает по размеру с адаптивным. */
    private static final double REFERENCE_DISTANCE = 6.0;

    private static final int BACKGROUND = 0xB4101015;
    private static final int TEXT = Theme.TEXT;
    private static final int TEXT_SECONDARY = Theme.TEXT_SECONDARY;

    private final MultiSelectSetting player = new MultiSelectSetting("Player",
            List.of(NICKNAME, HAND_ITEM, ARMOR, HEALTH), NICKNAME, HAND_ITEM, ARMOR, HEALTH);
    private final MultiSelectSetting item = new MultiSelectSetting("Item",
            List.of(ICON, NAMETAGS), ICON, NAMETAGS);

    // Вкл — тег всегда одного размера на экране; выкл — как объект в мире, меньше с расстоянием.
    private final BooleanSetting adaptive = new BooleanSetting("Adaptive Size", true);

    private final Vector2f screen = new Vector2f();
    private final List<Entity> sorted = new ArrayList<>();
    private final List<ItemStack> row = new ArrayList<>(6);

    public NameTags() {
        super("NameTags", "Custom name tags for players and items", Category.RENDER);
        addSettings(player, item, adaptive);
    }

    /** Нужен миксину: прятать ли ванильный ник над игроком. */
    public boolean replacesVanilla(Entity entity) {
        return isEnabled() && entity instanceof Player && player.count() > 0;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!isEnabled() || mc.player == null || mc.level == null || !WorldProjection.isReady()) return;
        if (player.count() == 0 && item.count() == 0) return;

        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        float partial = WorldProjection.partialTick();
        Vec3 eye = mc.gameRenderer.getMainCamera().position();

        // Глубина: рисуем от дальних к ближним, и каждый тег — в своём слое (nextStratum).
        // Внутри слоя GUI рисует предметы поверх плашек независимо от порядка вызовов,
        // поэтому без отдельного слоя предмет дальнего тега перекрывал бы ближнюю плашку.
        sorted.clear();
        for (Entity entity : mc.level.entitiesForRendering()) sorted.add(entity);
        sorted.sort((a, b) -> Double.compare(
                b.getPosition(partial).distanceToSqr(eye), a.getPosition(partial).distanceToSqr(eye)));

        for (Entity entity : sorted) {
            if (entity == mc.player || !entity.isAlive()) continue;
            boolean isPlayer = entity instanceof Player;
            boolean isItem = entity instanceof ItemEntity;
            if (isPlayer ? player.count() == 0 : !isItem || item.count() == 0) continue;
            if (isPlayer && entity.isInvisibleTo(mc.player)) continue;

            Vec3 pos = entity.getPosition(partial);
            if (pos.distanceTo(eye) > MAX_DISTANCE) continue;
            double top = pos.y + entity.getBbHeight() + (isPlayer ? 0.35 : 0.25);
            if (!WorldProjection.project(pos.x, top, pos.z, width, height, screen)) continue;
            if (screen.x < -100 || screen.x > width + 100 || screen.y < -50 || screen.y > height + 50) continue;

            // Рисуем вокруг (0,0) и двигаем pose: позиция дробная, без округления —
            // округление каждый кадр и давало дрожание тега и предметов.
            float scale = adaptive.getValue() ? 1F
                    : (float) Math.max(0.25, Math.min(1.5, REFERENCE_DISTANCE / Math.max(0.5, pos.distanceTo(eye))));
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(screen.x, screen.y);
                graphics.pose().scale(scale, scale);
                if (isPlayer) drawPlayer(graphics, (Player) entity, 0F, 0F);
                else drawItem(graphics, ((ItemEntity) entity).getItem(), 0F, 0F);
            } finally {
                graphics.pose().popMatrix();
            }
            graphics.nextStratum();
        }
    }

    /** Плашка ник + хп, над ней ряд: основная рука, броня, вторая рука. */
    private void drawPlayer(GuiGraphics graphics, Player target, float cx, float bottom) {
        FontAtlas font = Fonts.semibold();
        String name = player.isSelected(NICKNAME) ? target.getName().getString() : null;
        String hp = player.isSelected(HEALTH) ? formatHealth(target.getHealth() + target.getAbsorptionAmount()) : null;

        float plateTop = bottom;
        if (name != null || hp != null) {
            float nameW = name != null ? font.width(name, FONT_SIZE) : 0F;
            float hpW = hp != null ? font.width(hp, FONT_SIZE) : 0F;
            float gap = name != null && hp != null ? TEXT_GAP : 0F;
            float plateW = PAD_X * 2 + nameW + gap + hpW;
            float x = cx - plateW * 0.5F;
            plateTop = bottom - PLATE_HEIGHT;
            Render2D.roundedRect(graphics, x, plateTop, plateW, PLATE_HEIGHT, RADIUS, BACKGROUND);
            float tx = x + PAD_X;
            if (name != null) {
                Render2D.textInRow(graphics, font, name, tx, plateTop, PLATE_HEIGHT, FONT_SIZE, TEXT);
                tx += nameW + gap;
            }
            if (hp != null) {
                Render2D.textInRow(graphics, font, hp, tx, plateTop, PLATE_HEIGHT, FONT_SIZE,
                        healthColor(target.getHealth() / Math.max(1F, target.getMaxHealth())));
            }
        }

        row.clear();
        if (player.isSelected(HAND_ITEM)) addIfPresent(target.getMainHandItem());
        if (player.isSelected(ARMOR)) {
            for (EquipmentSlot slot : ARMOR_SLOTS) addIfPresent(target.getItemBySlot(slot));
        }
        if (player.isSelected(HAND_ITEM)) addIfPresent(target.getOffhandItem());
        if (row.isEmpty()) return;

        float rowW = row.size() * ITEM_SIZE + (row.size() - 1) * ITEM_GAP;
        float x = cx - rowW * 0.5F;
        float y = plateTop - ROW_GAP - ITEM_SIZE;
        for (int i = 0; i < row.size(); i++) drawStack(graphics, row.get(i), x + i * (ITEM_SIZE + ITEM_GAP), y);
    }

    /** Плашка предмета: иконка и/или «Название x3». Без обеих частей — ничего. */
    private void drawItem(GuiGraphics graphics, ItemStack stack, float cx, float bottom) {
        if (stack.isEmpty()) return;
        FontAtlas font = Fonts.semibold();
        boolean icon = item.isSelected(ICON);
        String label = item.isSelected(NAMETAGS)
                ? stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "")
                : null;

        float labelW = label != null ? font.width(label, FONT_SIZE) : 0F;
        float iconW = icon ? ITEM_SIZE : 0F;
        float gap = icon && label != null ? 3F : 0F;
        float plateW = (label != null ? PAD_X * 2 : 3F) + iconW + gap + labelW;
        float x = cx - plateW * 0.5F;
        float y = bottom - PLATE_HEIGHT;

        if (label != null) Render2D.roundedRect(graphics, x, y, plateW, PLATE_HEIGHT, RADIUS, BACKGROUND);
        float tx = x + (label != null ? PAD_X : 1.5F);
        if (icon) {
            drawStack(graphics, stack, tx, y + (PLATE_HEIGHT - ITEM_SIZE) * 0.5F);
            tx += iconW + gap;
        }
        if (label != null) Render2D.textInRow(graphics, font, label, tx, y, PLATE_HEIGHT, FONT_SIZE, TEXT_SECONDARY);
    }

    private void addIfPresent(ItemStack stack) {
        if (!stack.isEmpty()) row.add(stack);
    }

    private static void drawStack(GuiGraphics graphics, ItemStack stack, float x, float y) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(ITEM_SIZE / 16F, ITEM_SIZE / 16F);
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static String formatHealth(float health) {
        if (!Float.isFinite(health) || health < 0) health = 0;
        float rounded = Math.round(health * 10F) / 10F;
        return rounded == (int) rounded ? Integer.toString((int) rounded) : Float.toString(rounded);
    }

    /** Зелёный → жёлтый → красный по доле здоровья. */
    private static int healthColor(float fraction) {
        fraction = Math.max(0F, Math.min(1F, fraction));
        if (fraction > 0.6F) return 0xFF5CE65C;
        if (fraction > 0.3F) return 0xFFFFC85C;
        return 0xFFFF5C5C;
    }
}
