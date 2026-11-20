package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;

/**
 * Полная освещённость: карта света упирается в белый, и мир виден так, будто
 * везде стоит свет 15-го уровня. Работает только на своём клиенте.
 *
 * В 1.21.11 карту света считает не Java, а шейдер assets/minecraft/shaders/core/lightmap.fsh:
 * Java лишь кладёт в UBO семь чисел. Седьмое — BrightnessFactor, множитель микса цвета
 * с notGamma(color). Ваниль берёт его как max(0, gamma - затемнение), а gamma прижата
 * к [0, 1] (Options.<init> строит её на OptionInstance.UnitDouble). При множителе больше
 * единицы микс экстраполируется: тёмные тона уезжают за 1.0, а RGBA8 текстуры прижимает
 * их к белому.
 *
 * Подмену делает LightTextureMixin. Побочно перестаёт темнить эффект Darkness (страж):
 * ваниль вычитает его из gamma, а мы это значение игнорируем.
 */
public class Gamma extends Module {

    // 1 = ванильный максимум gamma («Ярко»), 12 = белая карта света:
    // при 12 даже нулевой уровень света даёт 1.0 (сверено повтором math шейдера)
    private final SliderSetting brightness = new SliderSetting("Bright", 12, 1, 12, 1);

    public Gamma() {
        super("Gamma", "Makes the world fully lit", Category.RENDER);
        addSettings(brightness);
    }

    /** Значение для BrightnessFactor в шейдере карты света. */
    public float getBrightness() {
        return brightness.getValue().floatValue();
    }
}
