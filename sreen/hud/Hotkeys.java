package dev.lucid.sreen.hud;

import dev.lucid.Lucid;
import dev.lucid.module.Module;
import dev.lucid.util.KeyNames;
import dev.lucid.util.Wrapper;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Бинды включённых модулей: название слева, клавиша справа.
 *
 * <p>Только включённые. Список отвечает на вопрос «что сейчас на клавишах», а выключенный
 * модуль клавишу не занимает — его строка была бы ложной подсказкой. Обратная сторона:
 * панель появляется и исчезает — нет ни одного включённого модуля с биндом, нет и плашки.</p>
 *
 * <p>Порядок по алфавиту, а не по регистрации: модули добавляются группами по категориям,
 * и в списке из десяти строк алфавит ищется глазом быстрее.</p>
 */
public class Hotkeys implements Wrapper {

    private static final String TITLE = "Hotkeys";

    /** Ширина пустой плашки в превью: содержимого нет, поэтому берётся числом. */
    private static final float EMPTY_WIDTH = HudCard.MIN_WIDTH;
    private static final float ROW_HEIGHT = HudCard.ROW_HEIGHT;
    /** Пустая плашка: полная шапка и укороченная нижняя часть; одинаково у всех панелей. */
    private static final float EMPTY_HEIGHT = HudCard.emptyHeight();

    /** Готовая строка: модуль спрашивают за клавишей один раз, на этапе сборки списка. */
    private record Row(String name, String key) {}

    public void render(GuiGraphics graphics, boolean preview) {
        List<Row> rows = rows();

        if (rows.isEmpty()) {
            // Превью без содержимого: шапка целиком, высота нижней части задана в emptyHeight().
            if (preview) {
                DragHud.Rect rect = DragHud.place(DragHud.Slot.HOTKEYS, graphics,
                        graphics.guiWidth() - HudCard.MARGIN - EMPTY_WIDTH,
                        bottom(graphics, EMPTY_HEIGHT), EMPTY_WIDTH, EMPTY_HEIGHT);
                HudCard.draw(graphics, rect.x(), rect.y(), EMPTY_WIDTH, EMPTY_HEIGHT, TITLE);
            }
            return;
        }

        float nameWidth = 0;
        float keyWidth = 0;
        for (Row row : rows) {
            nameWidth = Math.max(nameWidth, HudCard.width(row.name()));
            keyWidth = Math.max(keyWidth, keyWidth(row.key()));
        }

        float width = HudCard.width(nameWidth, keyWidth, TITLE);
        float height = HudCard.height(rows.size(), ROW_HEIGHT);

        DragHud.Rect rect = DragHud.place(DragHud.Slot.HOTKEYS, graphics,
                graphics.guiWidth() - HudCard.MARGIN - width,
                bottom(graphics, height), width, height);

        HudCard.draw(graphics, rect.x(), rect.y(), width, height, TITLE);

        float keyRight = rect.x() + width - HudCard.PADDING;
        float rowY = HudCard.bodyTop(rect.y());

        for (Row row : rows) {
            // Все строки включённые, поэтому цвет один на всех: приглушать нечего
            HudCard.rowText(graphics, row.name(), rect.x() + HudCard.PADDING, rowY, ROW_HEIGHT, HudCard.TEXT);
            HudCard.rowTextRight(graphics, row.key(), keyRight, rowY, ROW_HEIGHT, HudCard.ACCENT);

            rowY += ROW_HEIGHT;
        }
    }

    /** Положение из донора: справа у нижнего края экрана. */
    private static float bottom(GuiGraphics graphics, float height) {
        return Math.max(0f, graphics.guiHeight() - height);
    }

    private static float keyWidth(String key) {
        return HudCard.width(key);
    }

    private List<Row> rows() {
        List<Module> active = new ArrayList<>();
        for (Module module : Lucid.getInstance().getModuleManager().getModules()) {
            if (module.isEnabled() && module.isBound()) active.add(module);
        }
        active.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

        List<Row> rows = new ArrayList<>(active.size());
        for (Module module : active) {
            rows.add(new Row(module.getName(), bindOf(module)));
        }
        return rows;
    }

    /** Короткое имя бинда: {@code R}, {@code LShift}, {@code MMB} — та же таблица, что в ClickGUI. */
    private String bindOf(Module module) {
        return KeyNames.of(module.getKey(), module.getMouseButton());
    }
}