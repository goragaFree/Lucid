package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;

/**
 * Показывает невидимых сущностей полупрозрачными — как в режиме наблюдателя.
 * EntityMixin: пока модуль включён, isInvisibleTo(игрок) возвращает false,
 * и ванильный LivingEntityRenderer рисует тело прозрачным.
 * LivingEntityRendererMixin: подменяет ванильную прозрачность (~15%) на значение слайдера.
 * Работает только на твоём клиенте.
 */
public class SeeInvisible extends Module {

    // Непрозрачность тела в процентах
    private final SliderSetting alpha = new SliderSetting("Alpha", 30, 5, 100, 5);

    public SeeInvisible() {
        super("SeeInvisible", "Shows invisible entities", Category.RENDER);
        addSettings(alpha);
    }

    /** Цвет ARGB: белый с выбранной прозрачностью. */
    public int getColor() {
        int a = (int) Math.round(alpha.getValue() / 100.0 * 255);
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | 0xFFFFFF;
    }
}
