package dev.lucid.sreen.clickgui;

import dev.lucid.Lucid;
import dev.lucid.setting.BindSetting;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.util.Wrapper;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Колонка категории фиксированной высоты. Если модули не влезают —
 * прокрутка колёсиком или перетаскиванием полосы прокрутки.
 */
public class CategoryPanel {

    private final Category category;
    private final ClickGuiScreen screen;
    private final List<ModuleButton> buttons = new ArrayList<>();

    private float x;
    private float y;
    private float height = Theme.PANEL_HEIGHT;

    private float scroll;        // текущая (анимированная) прокрутка
    private float targetScroll;  // куда едем
    private long lastFrame;

    private boolean draggingBar;
    private float dragOffset;    // где схватили ползунок относительно его верха

    public CategoryPanel(Category category, ClickGuiScreen screen) {
        this.category = category;
        this.screen = screen;
        for (Module module : Lucid.getInstance().getModuleManager().getModules(category)) {
            buttons.add(new ModuleButton(module, this));
        }
    }

    /** Состояние «ждём бинд» живёт на экране: он один ловит ввод за все колонки. */
    void beginBind(Module module) {
        screen.beginBind(module);
    }

    boolean isListening(Module module) {
        return screen.isListening(module);
    }

    public void beginBind(BindSetting setting) {
        screen.beginBind(setting);
    }

    public boolean isListening(BindSetting setting) {
        return screen.isListening(setting);
    }

    public void setBounds(float x, float y, float height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    // ---- Геометрия ----

    private float inset() {
        return Render2D.outlineInset(Theme.PANEL_WIDTH, height, Theme.OUTLINE_WIDTH);
    }

    private float innerX() { return x + inset(); }
    private float innerWidth() { return Theme.PANEL_WIDTH - inset() * 2; }
    private float innerRadius() { return Math.max(Theme.PANEL_RADIUS - inset(), 0); }

    /** Область со списком модулей (под шапкой). */
    private float viewTop() { return y + Theme.HEADER_HEIGHT; }
    private float viewBottom() { return y + height - inset(); }
    private float viewHeight() { return viewBottom() - viewTop(); }

    private float contentHeight() {
        float total = 0;
        for (ModuleButton button : buttons) total += button.getHeight();
        return total;
    }

    private float maxScroll() {
        return Math.max(0, contentHeight() - viewHeight());
    }

    private boolean isInView(double mouseX, double mouseY) {
        return Render2D.isHovered(mouseX, mouseY, x, viewTop(), Theme.PANEL_WIDTH, viewHeight());
    }

    // ---- Рендер ----

    /**
     * Только тень. Рисуется отдельным проходом до подложек всех колонок: тень соседа справа
     * заходит на край предыдущей панели, и если рисовать панель сразу за её тенью,
     * этот край успевает потемнеть — вдоль панелей идут тёмные полосы.
     */
    public void renderShadow(GuiGraphics graphics) {
        Render2D.squircleShadow(graphics, x, y, Theme.PANEL_WIDTH, height, Theme.PANEL_RADIUS,
                Theme.SHADOW_BLUR, 0, 0, 0, Theme.SHADOW);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        updateScroll(mouseY);

        float width = Theme.PANEL_WIDTH;
        float inset = inset();
        float innerX = innerX();
        float innerY = y + inset;
        float innerWidth = innerWidth();
        float innerRadius = innerRadius();

        // Фон и шапка ужаты под обводку — их сглаженные края прячутся под ней
        Render2D.squircle(graphics, innerX, innerY, innerWidth, height - inset * 2, innerRadius, Theme.PANEL_BACKGROUND);

        // Шапка — та же фигура, что и панель, обрезанная по её низу: только так её верхние
        // углы совпадают с углами панели при любом радиусе. Отдельным скруглением этого
        // не сделать: у полосы высотой в шапку шейдер урежет радиус до её полувысоты,
        // и стоит радиусу панели перерасти шапку, как углы разойдутся и наружу полезут
        // светлые заусенцы. Обрезка же даёт ровно нужное: углы точка в точку, низ прямой.
        graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(x + width), (int) Math.ceil(y + Theme.HEADER_HEIGHT));
        Render2D.squircle(graphics, innerX, innerY, innerWidth, height - inset * 2, innerRadius, Theme.HEADER_BACKGROUND);
        graphics.disableScissor();

        // Иконка категории + название, вместе по центру
        FontAtlas font = Fonts.medium();
        String name = category.getDisplayName();
        float gap = 4;
        float contentWidth = Theme.ICON_SIZE + gap + font.width(name, Theme.HEADER_FONT_SIZE);
        float contentX = x + (width - contentWidth) / 2;
        Render2D.iconCentered(graphics, category.getIcon(), contentX + Theme.ICON_SIZE / 2, y + Theme.HEADER_HEIGHT / 2, Theme.ICON_SIZE, Theme.ACCENT);
        Render2D.textInRow(graphics, font, name, contentX + Theme.ICON_SIZE + gap, y, Theme.HEADER_HEIGHT, Theme.HEADER_FONT_SIZE, Theme.TEXT);

        // Список модулей обрезается по области под шапкой
        boolean mouseInView = isInView(mouseX, mouseY) && !draggingBar;
        // Вне области — подсветки нет. X не трогаем: по нему тянется слайдер, даже если курсор ушёл
        int hoverY = mouseInView ? mouseY : Integer.MIN_VALUE;

        graphics.enableScissor((int) Math.floor(x), (int) Math.floor(viewTop()),
                (int) Math.ceil(x + width), (int) Math.ceil(viewBottom()));
        float buttonY = viewTop() - scroll;
        float lineThickness = 1F / Wrapper.mc.getWindow().getGuiScale(); // ровно 1 физический пиксель
        for (ModuleButton button : buttons) {
            float buttonHeight = button.getHeight();
            button.setBounds(x, buttonY, innerX, innerWidth);
            // Невидимые строки не рисуем
            if (buttonY + buttonHeight > viewTop() && buttonY < viewBottom()) {
                button.render(graphics, mouseX, hoverY);
                // Разделитель короче строки: с отступами как у текста модуля
                Render2D.rect(graphics, x + Theme.MODULE_PADDING, snap(buttonY), Theme.PANEL_WIDTH - Theme.MODULE_PADDING * 2,
                        lineThickness, Theme.DIVIDER);
            }
            buttonY += buttonHeight;
        }
        graphics.disableScissor();

        renderScrollbar(graphics, mouseX, mouseY);

        // Обводка последней — поверх краёв всех заливок
        Render2D.squircleOutline(graphics, x, y, width, height, Theme.PANEL_RADIUS, Theme.OUTLINE_WIDTH, Theme.OUTLINE);
    }

    /**
     * Полоса на всю внутреннюю ширину, повторяющая скругление панели: рисуем ту же фигуру,
     * что и фон панели, и обрезаем её по строке.
     *
     * Так подсветка у самого низа не вылезает за скругления, а полупрозрачный цвет
     * не накладывается дважды. Отдельной фигурой на высоту строки этого не сделать:
     * у неё половинная высота меньше радиуса, и шейдер урезал бы радиус до неё —
     * угол вышел бы полнее панельного и вылез за его край.
     */
    void fillRow(GuiGraphics graphics, float rowY, float rowHeight, int color) {
        graphics.enableScissor((int) Math.floor(x), (int) Math.floor(rowY),
                (int) Math.ceil(x + Theme.PANEL_WIDTH), (int) Math.ceil(rowY + rowHeight));
        Render2D.squircle(graphics, innerX(), y + inset(), innerWidth(), height - inset() * 2, innerRadius(), color);
        graphics.disableScissor();
    }

    // ---- Прокрутка ----

    private float trackTop() {
        return viewTop() + Theme.SCROLLBAR_PADDING;
    }

    /**
     * Высота дорожки полосы прокрутки.
     *
     * Низ приподнят на скругление панели: полоса стоит вплотную к правому краю, а у низа
     * край уже ушёл внутрь — без поправки ползунок в крайнем нижнем положении торчал бы
     * из панели. Насколько именно ушёл край, считаем той же формулой, что в шейдере.
     */
    private float trackHeight() {
        return Math.max(viewHeight() - Theme.SCROLLBAR_PADDING * 2 - cornerDrop(), 1);
    }

    /** Насколько край панели выше её низа на расстоянии от правой стороны до полосы. */
    private float cornerDrop() {
        float radius = innerRadius();
        float distance = inset() + Theme.SCROLLBAR_PADDING;
        if (distance >= radius) return 0;
        // Тот же показатель степени, что у SHAPE в шейдере.
        float power = 5;
        float reach = (float) Math.pow(radius, power) - (float) Math.pow(radius - distance, power);
        return radius - (float) Math.pow(reach, 1.0 / power);
    }

    private void updateScroll(int mouseY) {
        if (draggingBar) {
            float track = trackHeight();
            float thumb = thumbHeight(track);
            float percent = (mouseY - dragOffset - trackTop()) / Math.max(track - thumb, 1);
            targetScroll = percent * maxScroll();
            scroll = targetScroll = clamp(targetScroll, 0, maxScroll()); // при перетаскивании — без анимации
        }

        targetScroll = clamp(targetScroll, 0, maxScroll()); // модуль свернули — контент стал короче

        // Плавная анимация, не зависящая от FPS
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000F, 0.1F);
        lastFrame = now;
        float factor = 1 - (float) Math.exp(-18 * seconds);
        scroll += (targetScroll - scroll) * factor;
        if (Math.abs(targetScroll - scroll) < 0.01F) scroll = targetScroll;
    }

    private float thumbHeight(float trackHeight) {
        return Math.max(trackHeight * viewHeight() / contentHeight(), 12);
    }

    private float thumbX() {
        return x + Theme.PANEL_WIDTH - inset() - Theme.SCROLLBAR_PADDING - Theme.SCROLLBAR_WIDTH;
    }

    private float thumbY(float trackHeight) {
        float max = maxScroll();
        float percent = max == 0 ? 0 : scroll / max;
        return trackTop() + (trackHeight - thumbHeight(trackHeight)) * percent;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        // Зона захвата шире самой полосы, чтобы было легко попасть
        return maxScroll() > 0 && Render2D.isHovered(mouseX, mouseY, thumbX() - 3, viewTop(), Theme.SCROLLBAR_WIDTH + 6, viewHeight());
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;
        float track = trackHeight();
        int color = draggingBar || isOverScrollbar(mouseX, mouseY) ? Theme.SCROLLBAR_ACTIVE : Theme.SCROLLBAR;
        Render2D.roundedRect(graphics, thumbX(), thumbY(track), Theme.SCROLLBAR_WIDTH, thumbHeight(track),
                Theme.SCROLLBAR_WIDTH / 2, color);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!Render2D.isHovered(mouseX, mouseY, x, y, Theme.PANEL_WIDTH, height)) return false;
        targetScroll = clamp(targetScroll - (float) amount * Theme.SCROLL_STEP, 0, maxScroll());
        return true;
    }

    // ---- Мышь ----

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (Render2D.isHovered(mouseX, mouseY, x, y, Theme.PANEL_WIDTH, Theme.HEADER_HEIGHT)) {
            return true; // клик по шапке просто поглощаем
        }

        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            float track = trackHeight();
            float thumbY = thumbY(track);
            float thumbHeight = thumbHeight(track);
            // Схватили ползунок — держим за то же место; ткнули мимо — центр ползунка прыгает к курсору
            dragOffset = mouseY >= thumbY && mouseY <= thumbY + thumbHeight ? mouseY - thumbY : thumbHeight / 2;
            draggingBar = true;
            return true;
        }

        // Скрытые прокруткой строки кликаться не должны
        if (!isInView(mouseX, mouseY)) {
            return Render2D.isHovered(mouseX, mouseY, x, y, Theme.PANEL_WIDTH, height);
        }
        for (ModuleButton moduleButton : buttons) {
            if (moduleButton.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return true; // клик внутри панели не проходит к панелям под ней
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) draggingBar = false;
        for (ModuleButton moduleButton : buttons) {
            moduleButton.mouseReleased(mouseX, mouseY, button);
        }
    }

    private static float snap(float value) {
        float scale = Wrapper.mc.getWindow().getGuiScale();
        return Math.round(value * scale) / scale;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public Category getCategory() {
        return category;
    }
}
