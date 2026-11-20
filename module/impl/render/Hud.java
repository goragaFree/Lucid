package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.sreen.hud.Armor;
import dev.lucid.sreen.hud.Cooldowns;
import dev.lucid.sreen.hud.DragHud;
import dev.lucid.sreen.hud.Hotkeys;
import dev.lucid.sreen.hud.Hotbar;
import dev.lucid.sreen.hud.Potions;
import dev.lucid.sreen.hud.Watermark;
import dev.lucid.sreen.hud.TargetHud;
import dev.lucid.sreen.hud.Staff;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.List;

/**
 * HUD клиента: набор элементов, которые рисуются поверх игры.
 *
 * <p>Модуль — только переключатель и список выбранных элементов; сам рисунок живёт
 * отдельными классами в {@code dev.lucid.sreen.hud}. Так добавление нового элемента
 * (координаты, броня, скорость) не трогает ни модуль, ни регистрацию HUD: дописывается
 * вариант в настройку и одна ветка в {@link #render}.</p>
 *
 * <p>Рисуется как обычный HUD-элемент (Fabric HudElementRegistry), а не через миксин
 * в InGameHud: так кадр идёт в общем GuiRenderState вместе с остальным интерфейсом,
 * и наши прямоугольники и текст попадают в те же слои, что и ванильные, — без отдельных
 * буферов и ручной сортировки по глубине.</p>
 */
public class Hud extends Module implements HudElement {

    // Порядок вариантов = порядок отрисовки. Пустой элемент ничего не рисует: панель
    // появляется только когда есть что показать, — так список эффектов или кулдаунов
    // не оставляет на экране пустую рамку.
    private final MultiSelectSetting elements = new MultiSelectSetting("Elements",
            List.of("Watermark", "Potions", "Hotkeys", "Cooldowns", "Armor", "Hotbar", "Target HUD", "Staff"),
            "Watermark", "Potions", "Hotkeys", "Cooldowns", "Armor", "Hotbar", "Target HUD", "Staff");

    private final Watermark watermark = new Watermark();
    private final Potions potions = new Potions();
    private final Hotkeys hotkeys = new Hotkeys();
    private final Cooldowns cooldowns = new Cooldowns();
    private final Armor armor = new Armor();
    private final Hotbar hotbar = new Hotbar();
    private final TargetHud targetHud = new TargetHud();
    private final Staff staff = new Staff();

    public Hud() {
        super("HUD", "Shows HUD elements on screen", Category.RENDER);
        addSettings(elements);
    }

    /** Карточка Potions заменяет ванильные иконки справа сверху, как в доноре. */
    public boolean showsPotions() {
        return isEnabled() && elements.isSelected("Potions");
    }

    public boolean showsHotbar() {
        return isEnabled() && elements.isSelected("Hotbar") && mc.player != null
                && !mc.player.isSpectator() && mc.getCameraEntity() == mc.player;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        // HUD-элемент висит в игре всегда, поэтому включённость проверяет сам модуль.
        // Кадр перетаскивания гасим и здесь: панелей на экране нет — и хватать нечего,
        // иначе захват остался бы висеть на прямоугольниках давно прошедшего кадра.
        if (!isEnabled() || mc.options.hideGui || mc.player == null) {
            DragHud.beginFrame(false);
            return;
        }

        draw(graphics, mc.screen instanceof ChatScreen, delta);
    }

    /**
     * Панели. {@code preview} — открыт чат: пустые панели рисуются пустыми плашками
     * (см. {@link dev.lucid.sreen.hud.HudPanel#drawEmpty}).
     *
     * <p>Иначе HUD не увидеть там, где его как раз и хочется разглядеть: эффектов и кулдаунов
     * на игроке обычно нет, включённых биндов один-два, и место на экране занимает пустота.
     * Чат для этого удобен тем, что не закрывает мир: его {@code renderBackground} пустой,
     * затемнения нет — HUD под ним виден как обычно, только строки чата ложатся поверх.</p>
     *
     * <p>Содержимое при этом не выдумывается: пустая панель показывает только шапку с
     * названием элемента. Настоящие строки, если они есть, остаются настоящими.</p>
     */
    private void draw(GuiGraphics graphics, boolean preview, DeltaTracker delta) {
        // Кадр перетаскивания открывается здесь и закрывается следующим: панели запоминают,
        // где они встали, а нажатие ловится по этому прошлому кадру (см. DragHud)
        DragHud.beginFrame(preview);

        if (elements.isSelected("Watermark")) watermark.render(graphics);
        if (elements.isSelected("Potions")) potions.render(graphics, preview);
        if (elements.isSelected("Hotkeys")) hotkeys.render(graphics, preview);
        if (elements.isSelected("Cooldowns")) cooldowns.render(graphics, preview);
        if (elements.isSelected("Armor")) armor.render(graphics);
        if (showsHotbar()) hotbar.render(graphics, delta);
        if (elements.isSelected("Target HUD")) targetHud.render(graphics, preview);
        if (elements.isSelected("Staff")) staff.render(graphics, preview);
    }
}
