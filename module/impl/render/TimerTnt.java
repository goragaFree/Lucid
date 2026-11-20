package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.WorldProjection;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Таймер до взрыва над каждой ТНТ.
 *
 * <p>Время не засекается по факту появления сущности, а берётся прямо из фьюза
 * ({@link PrimedTnt#getFuse()}): это синхронизируемые данные сущности, сервер
 * рассылает их каждый тик. Поэтому таймер честный для любого сервера — если у
 * сервера свой фитиль (длиннее или короче ванильных 80 тиков), либо плагин
 * пере-поджигает ТНТ, показанное время каждый кадр совпадает с тем, что реально
 * считает сервер, а не с локальным секундомером клиента.</p>
 *
 * <p>Рисуется как {@link NameTags}: точка над головой проецируется матрицами кадра
 * ({@link WorldProjection}) в координаты HUD, плашка собирается обычным 2D-рендером.
 * Цвет полоски по остатку фьюза: зелёный → жёлтый → красный.</p>
 */
public class TimerTnt extends Module implements HudElement {

    // Размеры в GUI-пикселях — та же плашка, что у нейм-тегов.
    private static final float FONT_SIZE = 7.5F;
    private static final float PAD_X = 4F;
    private static final float PLATE_HEIGHT = 12F;
    private static final float RADIUS = 3F;
    private static final double MAX_DISTANCE = 64.0;

    /** Ванильная длина фитиля в тиках — по ней считается доля остатка для цвета. */
    private static final int VANILLA_FUSE = 80;
    /** Фьюз живёт в тиках, секунда — 20 тиков. */
    private static final int TICKS_PER_SECOND = 20;

    private static final int BACKGROUND = 0xB4101015;
    private static final int COLOR_GREEN = 0xFF5CE65C;
    private static final int COLOR_YELLOW = 0xFFFFC85C;
    private static final int COLOR_RED = 0xFFFF5C5C;

    private final Vector2f screen = new Vector2f();
    private final List<PrimedTnt> fuseOrder = new ArrayList<>();

    public TimerTnt() {
        super("TimerTNT", "Shows time until TNT explodes", Category.RENDER);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!isEnabled() || mc.player == null || mc.level == null || !WorldProjection.isReady()) return;

        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        float partial = WorldProjection.partialTick();
        Vec3 eye = mc.gameRenderer.getMainCamera().position();

        // Дальние ТНТ рисуются первыми, ближние ложатся поверх — как в NameTags.
        fuseOrder.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt tnt && entity.isAlive()) fuseOrder.add(tnt);
        }
        fuseOrder.sort((a, b) -> Double.compare(
                b.getPosition(partial).distanceToSqr(eye), a.getPosition(partial).distanceToSqr(eye)));

        for (PrimedTnt tnt : fuseOrder) {
            Vec3 pos = tnt.getPosition(partial);
            if (pos.distanceTo(eye) > MAX_DISTANCE) continue;
            double top = pos.y + tnt.getBbHeight() + 0.25;
            if (!WorldProjection.project(pos.x, top, pos.z, width, height, screen)) continue;
            if (screen.x < -100 || screen.x > width + 100 || screen.y < -50 || screen.y > height + 50) continue;

            // Позиция дробная, без округления — округление каждый кадр дрожало бы,
            // как это было у тегов до перехода на translate.
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(screen.x, screen.y);
                drawTimer(graphics, tnt.getFuse(), 0F, 0F);
            } finally {
                graphics.pose().popMatrix();
            }
            graphics.nextStratum();
        }
    }

    /** Плашка с временем, центрированная по X, низом в точке {@code bottom}. */
    private void drawTimer(GuiGraphics graphics, int fuse, float cx, float bottom) {
        FontAtlas font = Fonts.semibold();
        String label = formatSeconds(fuse);
        float plateW = PAD_X * 2 + font.width(label, FONT_SIZE);
        float x = cx - plateW * 0.5F;
        float y = bottom - PLATE_HEIGHT;

        Render2D.roundedRect(graphics, x, y, plateW, PLATE_HEIGHT, RADIUS, BACKGROUND);
        Render2D.textInRow(graphics, font, label, x + PAD_X, y, PLATE_HEIGHT, FONT_SIZE, fuseColor(fuse));
    }

    /** Зелёный → жёлтый → красный по доле фьюза от ванильных 80 тиков. */
    private static int fuseColor(int fuse) {
        float fraction = Math.max(0F, Math.min(1F, fuse / (float) VANILLA_FUSE));
        if (fraction > 0.6F) return COLOR_GREEN;
        if (fraction > 0.3F) return COLOR_YELLOW;
        return COLOR_RED;
    }

    /**
     * Секунды с точностью до тика (два знака — шаг 0.05с): {@code 4s}, {@code 3.25s},
     * {@code 2.5s}. Хвостовые нули срезаются, чтобы плашка не дышала шириной.
     */
    private static String formatSeconds(int fuse) {
        double seconds = Math.max(0, fuse) / (double) TICKS_PER_SECOND;
        double rounded = Math.round(seconds * 100.0) / 100.0;
        if (rounded == Math.floor(rounded)) return (int) rounded + "s";
        String plain = String.format(Locale.ROOT, "%.2f", rounded);
        return (plain.endsWith("0") ? plain.substring(0, plain.length() - 1) : plain) + "s";
    }
}
