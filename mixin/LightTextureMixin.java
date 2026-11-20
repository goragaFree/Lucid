package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.Gamma;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    /**
     * В updateLightTexture единственный Math.max — это ванильное max(0, gamma - затемнение),
     * которое уезжает в шейдер карты света как BrightnessFactor. Пока модуль включён,
     * подставляем вместо него значение слайдера. Один вызов на метод, поэтому ordinal не нужен.
     */
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float lucid$brightnessFactor(float zero, float gamma) {
        Gamma gammaModule = Lucid.getInstance().getModuleManager().get(Gamma.class);
        if (gammaModule != null && gammaModule.isEnabled()) {
            return gammaModule.getBrightness();
        }
        return Math.max(zero, gamma);
    }
}
