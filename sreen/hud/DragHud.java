package dev.lucid.sreen.hud;

import com.google.gson.JsonObject;
import dev.lucid.Lucid;
import dev.lucid.util.Wrapper;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumMap;
import java.util.Map;

/**
 * Перетаскивание панелей HUD мышью, пока открыт чат.
 *
 * <p>Чат — единственное место, где курсор свободен (в игре он заперт в центре экрана
 * pointer-lock'ом) и мир при этом виден целиком ({@code ChatScreen#renderBackground} пустой,
 * затемнения нет). Поэтому перетаскивание живёт только здесь.</p>
 *
 * <p>Захват ставится <b>в обработчике события мыши</b>, а не при отрисовке. Нажатие сверяется
 * с прямоугольниками, которые панели заняли в прошлом кадре ({@link #LAST_RECTS}): это ровно
 * то, что игрок видел под курсором, когда нажимал. Раньше нажатие лишь поднимало флаг, который
 * поглощался следующей отрисовкой панели, — и если между нажатием и этой отрисовкой успевал
 * пройти кадр, флаг гасился впустую и клик молча терялся. Терять теперь нечего: тащимая панель
 * известна сразу.</p>
 *
 * <p>Хранится смещение от штатного места, а не позиция: панели у нижнего края должны
 * оставаться у нижнего края при смене размера окна, а не висеть там, куда их бросили
 * на прошлом разрешении.</p>
 */
public final class DragHud implements Wrapper {

    /** Что двигаем. Имена констант — они же ключи в конфиге. */
    public enum Slot {
        /** Слева сверху. */
        POTIONS,
        /** Слева снизу. */
        HOTKEYS,
        /** Справа сверху под Potions. */
        COOLDOWNS,
        /** По центру над хотбаром; можно переместить в чате. */
        ARMOR,
        /** Карточка текущей цели. */
        TARGET,
        /** Список персонала сервера. */
        STAFF,
        /** Хотбар со второй рукой и статусными рядами. */
        HOTBAR
    }

    /** Куда панель встала в этом кадре. */
    public record Rect(float x, float y) {}

    /** Смещения от штатного места: {@code [0] = x}, {@code [1] = y}. */
    private static final Map<Slot, float[]> OFFSETS = new EnumMap<>(Slot.class);

    /**
     * Прямоугольники прошлого кадра: {@code {x, y, ширина, высота}}. По ним ищется нажатие —
     * это единственный кадр, который игрок успел увидеть.
     */
    private static final Map<Slot, float[]> LAST_RECTS = new EnumMap<>(Slot.class);
    /** Прямоугольники текущего кадра; в конце кадра становятся {@link #LAST_RECTS}. */
    private static final Map<Slot, float[]> FRAME_RECTS = new EnumMap<>(Slot.class);

    /** Перетаскивание разрешено только в чате. */
    private static boolean active;

    /** Что тащим прямо сейчас. */
    private static Slot dragging;
    /** Смещение точки захвата внутри панели — чтобы панель не прыгала под курсор. */
    private static float grabX;
    private static float grabY;

    /** Курсор в GUI-координатах; обновляется каждый кадр, пока тащим. */
    private static double mouseX;
    private static double mouseY;

    private DragHud() {}

    /**
     * Раз в кадр, до панелей: закрываем прошлый кадр (его прямоугольники становятся
     * «теми, что видит игрок») и запоминаем курсор для движения за ним.
     */
    public static void beginFrame(boolean preview) {
        LAST_RECTS.clear();
        LAST_RECTS.putAll(FRAME_RECTS);
        FRAME_RECTS.clear();

        active = preview;
        if (!preview) {
            // Вышли из чата во время перетаскивания — фиксируем позицию
            stop();
            return;
        }

        // Курсор опрашиваем у мыши каждый кадр — только для ДВИЖЕНИЯ за курсором.
        // До первого захода курсора в окно координаты приходят Infinity — такой кадр пропускаем.
        double x = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double y = mc.mouseHandler.getScaledYPos(mc.getWindow());
        if (Double.isFinite(x) && Double.isFinite(y)) {
            mouseX = x;
            mouseY = y;
        }
    }

    /**
     * Нажали ЛКМ в чате. Координаты — из события мыши, в GUI-пространстве панелей.
     *
     * <p>Панель ищется по прошлому кадру: если курсор был над ней, когда игрок нажимал,
     * панель считается схваченной. Порядок обхода — порядок {@link Slot}, то есть порядок
     * отрисовки; панели не перекрываются, поэтому порядок ни на что не влияет.</p>
     *
     * @return {@code true}, если нажатие забрала панель: тогда чату клик отдавать незачем
     *         (иначе в поле ввода прыгнула бы каретка)
     */
    public static boolean onPress(double x, double y) {
        if (!active) return false;

        for (Slot slot : Slot.values()) {
            float[] rect = LAST_RECTS.get(slot);
            if (rect == null) continue;
            if (x < rect[0] || x >= rect[0] + rect[2]) continue;
            if (y < rect[1] || y >= rect[1] + rect[3]) continue;

            dragging = slot;
            grabX = (float) x - rect[0];
            grabY = (float) y - rect[1];
            // Курсор мог не двигаться с прошлого кадра — точку нажатия считаем текущей
            mouseX = x;
            mouseY = y;
            return true;
        }
        return false;
    }

    /** Отпустили ЛКМ — перетаскивание закончено, позиция уходит в конфиг. */
    public static void onRelease() {
        stop();
    }

    /**
     * Место панели на этот кадр: штатная точка плюс смещение. Пока панель тащат — она идёт
     * за курсором, и смещение пересчитывается от штатной точки.
     */
    public static Rect place(Slot slot, GuiGraphics graphics, float defaultX, float defaultY,
                             float width, float height) {
        float[] offset = OFFSETS.computeIfAbsent(slot, key -> new float[2]);
        float x = defaultX + offset[0];
        float y = defaultY + offset[1];

        float maxX = Math.max(0, graphics.guiWidth() - width);
        float maxY = Math.max(0, graphics.guiHeight() - height);

        if (active && dragging == slot) {
            x = clamp((float) mouseX - grabX, 0, maxX);
            y = clamp((float) mouseY - grabY, 0, maxY);
            offset[0] = x - defaultX;
            offset[1] = y - defaultY;
        }

        Rect rect = new Rect(clamp(x, 0, maxX), clamp(y, 0, maxY));
        FRAME_RECTS.put(slot, new float[]{rect.x(), rect.y(), width, height});
        return rect;
    }

    /** Закончить перетаскивание, если оно идёт, и сохранить позицию. */
    private static void stop() {
        if (dragging == null) return;
        dragging = null;
        Lucid.getInstance().getConfigManager().save();
    }

    /** Смещения в конфиг: {@code "hud": { "POTIONS": { "x": 0, "y": 0 } }}. */
    public static void write(JsonObject root) {
        JsonObject hud = new JsonObject();
        for (Slot slot : Slot.values()) {
            float[] offset = OFFSETS.get(slot);
            if (offset == null || (offset[0] == 0 && offset[1] == 0)) continue;

            JsonObject entry = new JsonObject();
            entry.addProperty("x", offset[0]);
            entry.addProperty("y", offset[1]);
            hud.add(slot.name(), entry);
        }
        root.add("hud", hud);
    }

    /** Читает то, что писала {@link #write}; чего нет в файле — остаётся на штатном месте. */
    public static void read(JsonObject root) {
        JsonObject hud = root.getAsJsonObject("hud");
        if (hud == null) return;

        for (Slot slot : Slot.values()) {
            JsonObject entry = hud.getAsJsonObject(slot.name());
            if (entry == null) continue;
            if (!entry.has("x") || !entry.has("y")) continue;

            OFFSETS.put(slot, new float[]{entry.get("x").getAsFloat(), entry.get("y").getAsFloat()});
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
