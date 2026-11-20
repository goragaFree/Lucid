package dev.lucid.sreen.alt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lucid.Lucid;
import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.util.Wrapper;
import dev.lucid.util.render.ColorUtil;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import dev.lucid.util.render.font.Icons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер ников в главном меню: плашка с текущим ником, пул ников под ней и поле ввода.
 *
 * <p>Ник меняется подменой {@code User.name}, а не созданием новой сессии: клиент берёт имя
 * именно оттуда, когда собирает {@code ServerboundHelloPacket}. Токен и UUID остаются от
 * аккаунта лаунчера — это осознанно: пароли и настоящие аккаунты сюда не входят, а значит
 * такой ник работает на серверах без online-mode. На сервере с проверкой лицензии сессия
 * просто не сойдётся с именем.</p>
 *
 * <p>Ввод сделан своим, а не ванильным {@code EditBox}: плашка рисуется теми же фигурами,
 * что ClickGUI, и не должна тащить за собой ванильную рамку, фокус и навигацию по виджетам.
 * Поэтому символы и клавиши приходят прямиком из {@code KeyboardHandler}, а клики — из
 * {@code TitleScreen}. Координаты клика уже в GUI-пространстве, как и у панелей HUD.</p>
 *
 * <p>Состояние живёт в {@code config/lucid/config.json} в секции {@code "alts"}: пул и
 * применённый ник. Применённый восстанавливается при загрузке, иначе после перезапуска
 * лаунчер вернул бы исходный ник.</p>
 */
public final class AltManager implements Wrapper {

    private static final float MARGIN = 12;
    private static final float WIDTH = 124;
    private static final float HEADER_HEIGHT = 22;
    private static final float ROW_HEIGHT = 14;
    private static final float INPUT_HEIGHT = 18;
    private static final float GAP = 4;
    private static final float PADDING = 5;
    private static final float RADIUS = 8;
    private static final float OUTLINE_WIDTH = 1;
    private static final int MAX_ROWS = 6;
    private static final int MAX_NAME_LENGTH = 16;

    private static final float HEADER_FONT = 9.5F;
    private static final float ROW_FONT = 8.5F;
    private static final float INPUT_FONT = 8;

    /** Порядок сохранён: первый добавленный ник остаётся первым в списке. */
    private static final List<String> NAMES = new ArrayList<>();
    /** {@code null} — ещё не применяли ничего, показываем ник сессии. */
    private static String active;

    private static String input = "";
    private static boolean expanded;
    private static boolean inputFocused;
    private static int scroll;
    private static boolean seeded;
    /** На секунду подсвечиваем поле красным, если ник невалидный или уже есть в пуле. */
    private static long invalidUntil;

    private AltManager() {}

    // ------------------------------------------------------------------ геометрия

    /** Плашка прижата к правому верхнему углу; считается от окна, а не от кадра рендера. */
    private static float x() {
        return Math.max(MARGIN, mc.getWindow().getGuiScaledWidth() - MARGIN - WIDTH);
    }

    private static float y() {
        return MARGIN;
    }

    private static float listTop() {
        return y() + HEADER_HEIGHT + GAP;
    }

    /** Пустой пул всё равно занимает строку — иначе некуда написать подсказку. */
    private static int visibleRows() {
        return Math.min(Math.max(NAMES.size(), 1), MAX_ROWS);
    }

    private static float listHeight() {
        return visibleRows() * ROW_HEIGHT;
    }

    private static float inputTop() {
        return listTop() + listHeight() + GAP;
    }

    private static float panelHeight() {
        return expanded ? inputTop() + INPUT_HEIGHT + PADDING - y() : HEADER_HEIGHT;
    }

    private static int maxScroll() {
        return Math.max(0, NAMES.size() - MAX_ROWS);
    }

    private static void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    // --------------------------------------------------------------------- рендер

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!(mc.screen instanceof TitleScreen)) return;
        ensureSeeded();
        clampScroll();

        float x = x();
        float y = y();
        float width = WIDTH;
        float height = panelHeight();
        float inset = Render2D.outlineInset(width, height, OUTLINE_WIDTH);
        float innerRadius = Math.max(RADIUS - inset, 0);

        // Одна фигура на всю плашку: и в свёрнутом, и в раскрытом виде это тот же прямоугольник
        Render2D.squircleShadow(graphics, x, y, width, height, RADIUS,
                Theme.SHADOW_BLUR, 0, 0, 0, Theme.SHADOW);
        Render2D.squircleRectOutlined(graphics, x, y, width, height, RADIUS, OUTLINE_WIDTH,
                Theme.PANEL_BACKGROUND, Theme.OUTLINE);

        // Шапка — та же фигура, обрезанная по своему низу: углы совпадают точка в точку
        graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(x + width), (int) Math.ceil(y + HEADER_HEIGHT));
        Render2D.squircle(graphics, x + inset, y + inset, width - inset * 2,
                height - inset * 2, innerRadius, Theme.HEADER_BACKGROUND);
        graphics.disableScissor();

        FontAtlas headerFont = Fonts.medium();
        float textLeft = x + PADDING + 2;
        float chevronCenter = x + width - PADDING - 5;
        // Длинный ник не должен заезжать под шеврон
        graphics.enableScissor((int) Math.floor(textLeft), (int) Math.floor(y),
                (int) Math.ceil(chevronCenter - 8), (int) Math.ceil(y + HEADER_HEIGHT));
        Render2D.textInRow(graphics, headerFont, currentName(), textLeft, y,
                HEADER_HEIGHT, HEADER_FONT, Theme.TEXT);
        graphics.disableScissor();
        Render2D.iconCentered(graphics, Icons.CHEVRON_DOWN, chevronCenter,
                y + HEADER_HEIGHT / 2, 10, expanded ? Theme.ACCENT : Theme.TEXT_SECONDARY);

        if (!expanded) return;

        float line = 1F / mc.getWindow().getGuiScale();
        Render2D.rect(graphics, x + PADDING, y + HEADER_HEIGHT, width - PADDING * 2,
                line, Theme.DIVIDER);

        renderList(graphics, mouseX, mouseY, x);
        renderInput(graphics, x);
    }

    private static void renderList(GuiGraphics graphics, int mouseX, int mouseY, float x) {
        float top = listTop();
        float height = listHeight();
        FontAtlas font = Fonts.regular();

        graphics.enableScissor((int) Math.floor(x), (int) Math.floor(top),
                (int) Math.ceil(x + WIDTH), (int) Math.ceil(top + height));
        if (NAMES.isEmpty()) {
            Render2D.textInRow(graphics, font, "No alts yet", x + PADDING + 2,
                    top, ROW_HEIGHT, ROW_FONT, Theme.TEXT_SECONDARY);
        }
        for (int row = 0; row < visibleRows(); row++) {
            int index = scroll + row;
            if (index >= NAMES.size()) break;

            String name = NAMES.get(index);
            float rowY = top + row * ROW_HEIGHT;
            boolean current = name.equalsIgnoreCase(currentName());
            boolean hovered = Render2D.isHovered(mouseX, mouseY, x, rowY, WIDTH, ROW_HEIGHT);

            if (current || hovered) {
                int fill = current
                        ? ColorUtil.withAlpha(Theme.ACCENT, hovered ? 0x38 : 0x26)
                        : Theme.HOVER;
                Render2D.roundedRect(graphics, x + PADDING - 1, rowY,
                        WIDTH - (PADDING - 1) * 2, ROW_HEIGHT, 3, fill);
            }
            if (current) {
                Render2D.iconCentered(graphics, Icons.CHECK, x + PADDING + 5,
                        rowY + ROW_HEIGHT / 2, 8, Theme.ACCENT);
            }
            Render2D.textInRow(graphics, font, name, x + PADDING + 12, rowY,
                    ROW_HEIGHT, ROW_FONT, current ? Theme.TEXT : Theme.TEXT_SECONDARY);
        }
        graphics.disableScissor();
    }

    private static void renderInput(GuiGraphics graphics, float x) {
        float top = inputTop();
        float fieldX = x + PADDING;
        float fieldWidth = WIDTH - PADDING * 2;
        int outline = invalidUntil > System.currentTimeMillis()
                ? 0xFFFF5555
                : (inputFocused ? Theme.ACCENT : Theme.OUTLINE);

        Render2D.roundedRectOutlined(graphics, fieldX, top, fieldWidth, INPUT_HEIGHT,
                4, OUTLINE_WIDTH, Theme.SETTING_BACKGROUND, outline);

        FontAtlas font = Fonts.regular();
        float textX = fieldX + 4;
        if (input.isEmpty()) {
            Render2D.textInRow(graphics, font, "Add nickname...", textX, top,
                    INPUT_HEIGHT, INPUT_FONT, Theme.TEXT_SECONDARY);
        } else {
            Render2D.textInRow(graphics, font, input, textX, top,
                    INPUT_HEIGHT, INPUT_FONT, Theme.TEXT);
        }

        // Каретка мигает, только пока поле в фокусе; пишем её после текста, чтобы не перекрывать буквы
        if (inputFocused && System.currentTimeMillis() % 1000 < 500) {
            float caretX = textX + (input.isEmpty() ? 0 : font.width(input, INPUT_FONT));
            Render2D.rect(graphics, caretX, top + 4, 1, INPUT_HEIGHT - 8, Theme.ACCENT);
        }
    }

    // ----------------------------------------------------------------------- ввод

    /**
     * @return {@code true}, если клик забрала плашка — тогда главному меню его отдавать не надо.
     *         Клик мимо плашки только закрывает список и уходит в ваниль ({@code false}).
     */
    public static boolean onMouseClicked(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !(mc.screen instanceof TitleScreen)) return false;
        ensureSeeded();

        double mouseX = event.x();
        double mouseY = event.y();
        float x = x();
        float y = y();

        if (Render2D.isHovered(mouseX, mouseY, x, y, WIDTH, HEADER_HEIGHT)) {
            expanded = !expanded;
            if (!expanded) inputFocused = false;
            return true;
        }
        if (!expanded) return false;

        if (Render2D.isHovered(mouseX, mouseY, x, listTop(), WIDTH, listHeight())) {
            int row = (int) ((mouseY - listTop()) / ROW_HEIGHT);
            int index = scroll + row;
            if (index >= 0 && index < NAMES.size()) apply(NAMES.get(index));
            inputFocused = false;
            return true;
        }
        if (Render2D.isHovered(mouseX, mouseY, x, inputTop(), WIDTH, INPUT_HEIGHT + PADDING)) {
            inputFocused = true;
            return true;
        }
        if (Render2D.isHovered(mouseX, mouseY, x, y, WIDTH, panelHeight())) {
            inputFocused = false;
            return true;
        }

        expanded = false;
        inputFocused = false;
        return false;
    }

    /** Клавиши поля ввода. Возвращает {@code true}, если клавишу нужно съесть у ванили. */
    public static boolean onKeyPressed(KeyEvent event, int action) {
        if (!(mc.screen instanceof TitleScreen) || !expanded) return false;

        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (action == GLFW.GLFW_PRESS) {
                expanded = false;
                inputFocused = false;
            }
            return true;
        }
        if (!inputFocused) return false;
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return false;

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // Повтор от зажатого Enter не должен добавить один и тот же ник дважды
            if (action == GLFW.GLFW_PRESS) {
                add(input);
                input = "";
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        return false;
    }

    /** Символы поля ввода. Пока поле в фокусе, забираем все: ванили там печатать нечего. */
    public static boolean onCharTyped(CharacterEvent event) {
        if (!(mc.screen instanceof TitleScreen) || !expanded || !inputFocused) return false;

        String typed = event.codepointAsString();
        if (typed != null && typed.length() == 1
                && input.length() < MAX_NAME_LENGTH
                && isNameChar(typed.charAt(0))) {
            input += typed;
        }
        return true;
    }

    /** Прокрутка списка колёсиком, когда их больше, чем влезает в плашку. */
    public static boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!(mc.screen instanceof TitleScreen) || !expanded) return false;
        if (!Render2D.isHovered(mouseX, mouseY, x(), listTop(), WIDTH, listHeight())) return false;
        if (maxScroll() == 0) return false;

        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), maxScroll()));
        return true;
    }

    // ------------------------------------------------------------------- состояние

    /** При первом запуске (конфига ещё нет) пул начинается с ника сессии. */
    private static void ensureSeeded() {
        if (seeded) return;
        seeded = true;

        String session = mc.getUser().getName();
        if (active == null) active = session;
        if (NAMES.isEmpty()) NAMES.add(session);
    }

    private static String currentName() {
        return active != null ? active : mc.getUser().getName();
    }

    private static boolean contains(String name) {
        for (String existing : NAMES) {
            if (existing.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Enter только кладёт ник в пул; применяется он отдельным кликом по строке. */
    private static void add(String name) {
        if (!isValid(name) || contains(name)) {
            invalidUntil = System.currentTimeMillis() + 900;
            return;
        }
        NAMES.add(name);
        scroll = maxScroll(); // свежий ник должен быть виден сразу
        save();
    }

    private static void apply(String name) {
        if (!isValid(name)) return;
        // accesswidener снимает final с User.name; сессию не пересоздаём (см. javadoc класса)
        mc.getUser().name = name;
        active = name;
        save();
    }

    private static void save() {
        Lucid lucid = Lucid.getInstance();
        if (lucid != null && lucid.getConfigManager() != null) {
            lucid.getConfigManager().save();
        }
    }

    private static boolean isValid(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) return false;
        for (int i = 0; i < name.length(); i++) {
            if (!isNameChar(name.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isNameChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_';
    }

    // --------------------------------------------------------------------- конфиг

    /** {@code "alts": { "names": [...], "active": "..." }}. */
    public static void write(JsonObject root) {
        ensureSeeded();

        JsonObject alts = new JsonObject();
        JsonArray names = new JsonArray();
        for (String name : NAMES) names.add(name);
        alts.add("names", names);
        alts.addProperty("active", currentName());
        root.add("alts", alts);
    }

    /** Восстанавливает пул и сразу применяет сохранённый ник — до того, как игрок зайдёт на сервер. */
    public static void read(JsonObject root) {
        JsonObject alts = root.getAsJsonObject("alts");
        if (alts == null) return;

        NAMES.clear();
        JsonArray names = alts.getAsJsonArray("names");
        if (names != null) {
            for (JsonElement element : names) {
                if (element == null || !element.isJsonPrimitive()) continue;
                String name = element.getAsString();
                if (isValid(name) && !contains(name)) NAMES.add(name);
            }
        }

        JsonElement saved = alts.get("active");
        if (saved != null && saved.isJsonPrimitive()) {
            String name = saved.getAsString();
            if (isValid(name)) {
                if (!contains(name)) NAMES.add(name);
                mc.getUser().name = name;
                active = name;
            }
        }
        // Пул мог оказаться пустым (старый конфиг без "names", битый "active") —
        // тогда показываем в нём тот ник, который реально применён
        if (NAMES.isEmpty()) NAMES.add(mc.getUser().getName());

        seeded = true;
        clampScroll();
    }
}
