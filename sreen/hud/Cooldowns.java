package dev.lucid.sreen.hud;

import dev.lucid.util.Wrapper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemCooldowns.CooldownInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Кулдауны предметов: иконка, название и сколько ещё ждать.
 *
 * <p>Список собирается по инвентарю, а не по внутренней карте кулдаунов: ключ в ней —
 * группа кулдауна ({@link ItemCooldowns#getCooldownGroup(ItemStack)}), а не предмет, и по
 * одной группе нельзя понять, что рисовать. Инвентарь — единственный способ показать
 * иконку; обратная сторона в том, что предмет, выброшенный сразу после использования,
 * из списка пропадёт, хотя кулдаун ещё идёт.</p>
 *
 * <p>Дубли по группе отсекаются: два стака одного предмета с общим кулдауном — одна
 * строка, иначе список пухнет от копий.</p>
 *
 * <p>Полоски остатка нет намеренно: ваниль уже показывает заливку поверх иконки в хотбаре,
 * а здесь достаточно числа — оно читается быстрее, чем длина полосы.</p>
 */
public class Cooldowns implements Wrapper {

    private static final String TITLE = "Cooldowns";
    private final HudTimerLayout timerLayout = new HudTimerLayout();

    /** Ширина пустой плашки в превью: содержимого нет, поэтому берётся числом. */
    private static final float EMPTY_WIDTH = HudCard.MIN_WIDTH;
    private static final float ICON = 8;
    /** Компактная строка и иконка 8×8, как в новом архиве. */
    private static final float ROW_HEIGHT = HudCard.ROW_HEIGHT;
    private static final float ICON_GAP = 5;
    /** Пустая плашка: полная шапка и укороченная нижняя часть; одинаково у всех панелей. */
    private static final float EMPTY_HEIGHT = HudCard.emptyHeight();

    public void render(GuiGraphics graphics, boolean preview) {
        List<Entry> entries = collect();
        if (entries.isEmpty()) {
            timerLayout.reset();
            // Превью без содержимого: шапка целиком, высота нижней части задана в emptyHeight().
            if (preview) {
                DragHud.Rect rect = DragHud.place(DragHud.Slot.COOLDOWNS, graphics,
                        graphics.guiWidth() - HudCard.MARGIN - EMPTY_WIDTH,
                        bottom(graphics, EMPTY_HEIGHT),
                        EMPTY_WIDTH, EMPTY_HEIGHT);
                HudCard.draw(graphics, rect.x(), rect.y(), EMPTY_WIDTH, EMPTY_HEIGHT, TITLE);
            }
            return;
        }

        float nameWidth = 0;
        float timeWidth = HudTimerLayout.width("00.0s");
        StringBuilder contents = new StringBuilder();
        contents.append(mc.player.getUUID()).append(':').append(mc.player.getId()).append(';');
        for (Entry entry : entries) {
            contents.append(entry.group()).append(':').append(name(entry)).append('\0');
            nameWidth = Math.max(nameWidth, HudCard.width(name(entry)));
            timeWidth = Math.max(timeWidth, HudTimerLayout.width(seconds(entry.remaining())));
        }

        float width = timerLayout.fit(contents.toString(), ICON + ICON_GAP + nameWidth, timeWidth, TITLE);
        float height = HudCard.height(entries.size(), ROW_HEIGHT);

        // Положение из донора: справа, Y=80; смещения перетаскивания сохраняются.
        DragHud.Rect rect = DragHud.place(DragHud.Slot.COOLDOWNS, graphics,
                graphics.guiWidth() - HudCard.MARGIN - width, bottom(graphics, height),
                width, height);

        float x = rect.x();
        float y = rect.y();

        HudCard.draw(graphics, x, y, width, height, TITLE);

        float textX = x + HudCard.PADDING + ICON + ICON_GAP;
        float rightEdge = x + width - HudCard.PADDING;
        float rowY = HudCard.bodyTop(y);

        float iconRowY = rowY;
        for (Entry entry : entries) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + HudCard.PADDING + ICON * 0.5f, iconRowY + ROW_HEIGHT * 0.5f);
            graphics.pose().scale(ICON / 16f, ICON / 16f);
            graphics.renderItem(entry.stack(), -8, -8);
            graphics.pose().popMatrix();
            iconRowY += ROW_HEIGHT;
        }
        // Как у донора: текст на слое после ванильных предметов.
        graphics.nextStratum();
        for (Entry entry : entries) {
            HudCard.rowText(graphics, name(entry), textX, rowY, ROW_HEIGHT, HudCard.TEXT);
            HudTimerLayout.drawRight(graphics, seconds(entry.remaining()), rightEdge, rowY, ROW_HEIGHT, HudCard.TEXT_SECONDARY);
            rowY += ROW_HEIGHT;
        }
    }

    /** Что показать: стак для иконки и названия плюс остаток в тиках. */
    private record Entry(Identifier group, ItemStack stack, int remaining) {}

    /** По умолчанию справа сверху, под Potions; на маленьком экране ограничиваем низом. */
    private static float bottom(GuiGraphics graphics, float height) {
        return Math.min(80f, Math.max(0f, graphics.guiHeight() - height));
    }

    private List<Entry> collect() {
        Player player = mc.player;
        ItemCooldowns cooldowns = player.getCooldowns();

        // Публичные методы отдают только долю остатка, а для «3.2s» нужны точные тики:
        // endTime - tickCount. Оба поля открыты в lucid.accesswidener.
        Map<Identifier, CooldownInstance> active = cooldowns.cooldowns;
        if (active.isEmpty()) return List.of();

        List<Entry> entries = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            add(entries, seen, cooldowns, active, inventory.getItem(slot));
        }
        add(entries, seen, cooldowns, active, player.getOffhandItem());

        entries.sort(Comparator.comparing(entry -> entry.group().toString()));
        return entries;
    }

    private void add(List<Entry> entries, Set<Identifier> seen, ItemCooldowns cooldowns,
                     Map<Identifier, CooldownInstance> active, ItemStack stack) {
        if (stack.isEmpty()) return;

        Identifier group = cooldowns.getCooldownGroup(stack);
        CooldownInstance instance = active.get(group);
        if (instance == null) return;
        if (!seen.add(group)) return;

        int remaining = instance.endTime() - cooldowns.tickCount;
        // Кулдаун уже истёк, а запись ещё лежит в карте — такая строка не нужна.
        if (remaining <= 0) return;

        entries.add(new Entry(group, stack, remaining));
    }

    private String name(Entry entry) {
        return entry.stack().getHoverName().getString();
    }

    /** Секунды с десятой долей до 10 с, дальше целые: «3.2s» полезно, «34.0s» — шум. */
    private String seconds(int ticks) {
        float value = ticks / 20.0F;
        if (value >= 10) return Math.round(value) + "s";
        return Math.round(value * 10) / 10.0F + "s";
    }
}