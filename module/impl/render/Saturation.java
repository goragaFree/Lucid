package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;

/**
 * Насыщенность цвета мира: 0 — чёрно-белый, 1 — ваниль, 2 — вдвое сочнее.
 * Работает только на своём клиенте.
 *
 * Постэффект assets/lucid/post_effect/saturation.json накладывает GameRendererMixin
 * сразу после отрисовки мира и до GUI, поэтому HUD, чат и меню остаются как были.
 */
public class Saturation extends Module {

    private final SliderSetting saturation = new SliderSetting("Saturation", 1.5, 0, 3, 0.05);
    // 1 — ваниль; больше — тени темнее, светлое ярче; меньше — картинка «мягче»
    private final SliderSetting contrast = new SliderSetting("Contrast", 1.0, 0.5, 2, 0.05);

    public Saturation() {
        super("Saturation", "Changes world color saturation", Category.RENDER);
        addSettings(saturation, contrast);
    }

    public float getSaturation() {
        return saturation.getValue().floatValue();
    }

    public float getContrast() {
        return contrast.getValue().floatValue();
    }
}
