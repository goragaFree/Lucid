package dev.lucid.sreen.hud;

import dev.lucid.sreen.clickgui.Theme;
import dev.lucid.util.Wrapper;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Эффекты, которые сейчас висят на игроке: иконка, название с уровнем и остаток времени.
 *
 * <p>Порядок фиксированный, а не «как пришли от сервера»: сначала вредные, потом полезные,
 * внутри группы — по постоянному ID эффекта. Так строки не меняются местами, когда
 * эффект продлевают или снимают, и список нельзя читать боковым зрением.</p>
 *
 * <p>Иконка — ванильный спрайт эффекта ({@link Gui#getMobEffectSprite}), а не цветная точка:
 * по значку эффект узнаётся мгновенно, тогда как цвет приходилось помнить. Цвет остался
 * только у текста: полезное белое, вредное красное — так плохое видно поперёк списка,
 * не разглядывая значки.</p>
 */
public class Potions implements Wrapper {

    private static final String TITLE = "Potions";
    private final HudTimerLayout timerLayout = new HudTimerLayout();

    /** Ширина пустой плашки в превью: содержимого нет, поэтому берётся числом. */
    private static final float EMPTY_WIDTH = HudCard.MIN_WIDTH;
    private static final float ROW_HEIGHT = HudCard.ROW_HEIGHT;
    /** Пустая плашка: полная шапка и укороченная нижняя часть; одинаково у всех панелей. */
    private static final float EMPTY_HEIGHT = HudCard.emptyHeight();

    /** Половина ванильных 18 px: ровно влезает в строку и остаётся по пикселю сверху и снизу. */
    private static final float ICON = 9;
    private static final float ICON_GAP = 5;

    private static final int HARMFUL = 0xFFFF6B6B;
    private static final int NEUTRAL = Theme.TEXT_SECONDARY;

    /** Пока эффект бесконечный, времени у него нет: прочерки вместо чисел. */
    private static final String INFINITE_TIME = "--:--";

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public void render(GuiGraphics graphics, boolean preview) {
        List<MobEffectInstance> effects = activeEffects();
        if (effects.isEmpty()) {
            timerLayout.reset();
            // Превью без содержимого: шапка целиком, высота нижней части задана в emptyHeight().
            if (preview) {
                DragHud.Rect rect = DragHud.place(DragHud.Slot.POTIONS, graphics,
                        graphics.guiWidth() - HudCard.MARGIN - EMPTY_WIDTH, HudCard.MARGIN, EMPTY_WIDTH, EMPTY_HEIGHT);
                HudCard.draw(graphics, rect.x(), rect.y(), EMPTY_WIDTH, EMPTY_HEIGHT, TITLE);
            }
            return;
        }

        float leftWidth = 0;
        float rightWidth = HudTimerLayout.width("00:00");
        StringBuilder contents = new StringBuilder();
        contents.append(mc.player.getUUID()).append(':').append(mc.player.getId()).append(';');
        for (MobEffectInstance effect : effects) {
            contents.append(effectId(effect)).append(':').append(label(effect)).append('\0');
            leftWidth = Math.max(leftWidth, ICON + ICON_GAP + HudCard.width(label(effect)));
            rightWidth = Math.max(rightWidth, HudTimerLayout.width(time(effect)));
        }

        float width = timerLayout.fit(contents.toString(), leftWidth, rightWidth, TITLE);
        float height = HudCard.height(effects.size(), ROW_HEIGHT);

        // Положение из донора: правый верхний угол, отступ 4.
        DragHud.Rect rect = DragHud.place(DragHud.Slot.POTIONS, graphics,
                graphics.guiWidth() - HudCard.MARGIN - width, HudCard.MARGIN, width, height);
        float x = rect.x();
        float y = rect.y();

        HudCard.draw(graphics, x, y, width, height, TITLE);

        float textX = x + HudCard.PADDING + ICON + ICON_GAP;
        float rightEdge = x + width - HudCard.PADDING;
        float rowY = HudCard.bodyTop(y);
        int iconY = Math.round((ROW_HEIGHT - ICON) * 0.5F);

        for (MobEffectInstance effect : effects) {
            // Иконка целыми пикселями: спрайт на дроби размазывается
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect.getEffect()),
                    Math.round(x + HudCard.PADDING), Math.round(rowY) + iconY,
                    Math.round(ICON), Math.round(ICON));

            HudCard.rowText(graphics, label(effect), textX, rowY, ROW_HEIGHT, textColor(effect));
            HudTimerLayout.drawRight(graphics, time(effect), rightEdge, rowY, ROW_HEIGHT, HudCard.TEXT_SECONDARY);

            rowY += ROW_HEIGHT;
        }
    }

    private List<MobEffectInstance> activeEffects() {
        return sorted(mc.player.getActiveEffects());
    }

    /** Порядок строк: вредное наверх, внутри категории — постоянный ID эффекта. */
    private static List<MobEffectInstance> sorted(Collection<MobEffectInstance> source) {
        List<MobEffectInstance> effects = new ArrayList<>(source);
        effects.sort(Comparator.comparingInt(Potions::categoryOrder).thenComparing(Potions::effectId));
        return effects;
    }

    /** Вредное — наверх: его замечают первым. */
    private static int categoryOrder(MobEffectInstance effect) {
        return switch (effect.getEffect().value().getCategory()) {
            case HARMFUL -> 0;
            case BENEFICIAL -> 1;
            case NEUTRAL -> 2;
        };
    }

    /** Стабильный ключ вместо сортировки по постоянно меняющемуся остатку времени. */
    private static String effectId(MobEffectInstance effect) {
        return String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()));
    }

    /** Название с уровнем: первый уровень не подписывается, дальше римские цифры как в ванили. */
    private String label(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        int level = effect.getAmplifier() + 1;
        if (level <= 1) return name;
        return name + " " + (level <= ROMAN.length ? ROMAN[level - 1] : "+" + (level - 1));
    }

    private String time(MobEffectInstance effect) {
        return effect.isInfiniteDuration() ? INFINITE_TIME : HudCard.clock(effect.getDuration());
    }

    /** Баффы белые, дебаффы красные: плохое видно, не разглядывая значки. */
    private int textColor(MobEffectInstance effect) {
        return switch (effect.getEffect().value().getCategory()) {
            case HARMFUL -> HARMFUL;
            case BENEFICIAL -> HudCard.TEXT;
            case NEUTRAL -> NEUTRAL;
        };
    }
}