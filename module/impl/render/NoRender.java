package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.MultiSelectSetting;

import java.util.List;

/**
 * Убирает лишнюю ванильную отрисовку.
 * Screen — то, что рисуется поверх экрана:
 * Pumpkin — затемнение от надетой тыквы (GuiTextureOverlayMixin);
 * Effects — ванильные иконки эффектов справа сверху (GuiEffectsMixin);
 * Fire — пламя перед лицом, когда горишь (ScreenEffectRendererMixin).
 * Effect — визуальная часть эффектов зелий:
 * Nausea — качание и искажение камеры и зелёная пелена (LivingEntityMixin).
 * Particles — частицы мира (ParticleEngineMixin):
 * Effects — завихрения от эффектов зелий (EFFECT, ENTITY_EFFECT, INSTANT_EFFECT).
 * Работает только на твоём клиенте.
 */
public class NoRender extends Module {

    public static final String PUMPKIN = "Pumpkin";
    public static final String EFFECTS = "Effects";
    public static final String FIRE = "Fire";
    public static final String NAUSEA = "Nausea";
    public static final String PARTICLE_EFFECTS = "Effects";

    private final MultiSelectSetting screen = new MultiSelectSetting("Screen",
            List.of(PUMPKIN, EFFECTS, FIRE), PUMPKIN, EFFECTS, FIRE);

    private final MultiSelectSetting effect = new MultiSelectSetting("Effect",
            List.of(NAUSEA), NAUSEA);

    private final MultiSelectSetting particles = new MultiSelectSetting("Particles",
            List.of(PARTICLE_EFFECTS), PARTICLE_EFFECTS);

    public NoRender() {
        super("NoRender", "Removes unwanted rendering", Category.RENDER);
        addSettings(screen, effect, particles);
    }

    public boolean hidesPumpkin() {
        return isEnabled() && screen.isSelected(PUMPKIN);
    }

    public boolean hidesEffects() {
        return isEnabled() && screen.isSelected(EFFECTS);
    }

    public boolean hidesFire() {
        return isEnabled() && screen.isSelected(FIRE);
    }

    public boolean hidesNausea() {
        return isEnabled() && effect.isSelected(NAUSEA);
    }

    public boolean hidesEffectParticles() {
        return isEnabled() && particles.isSelected(PARTICLE_EFFECTS);
    }
}
