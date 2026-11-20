package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.NoRender;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NoRender → Particles. Через createParticle проходят все частицы клиента
 * (ClientLevel.addParticle → LevelRenderer → ParticleEngine). null — штатный ответ
 * «частица не создана», вызывающий код его уже проверяет.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void lucid$noParticles(ParticleOptions options, double x, double y, double z,
                                   double dx, double dy, double dz,
                                   CallbackInfoReturnable<Particle> cir) {
        var client = Lucid.getInstance();
        if (client == null || client.getModuleManager() == null) return;
        NoRender noRender = client.getModuleManager().get(NoRender.class);
        if (noRender == null) return;

        ParticleType<?> type = options.getType();
        if (noRender.hidesEffectParticles() && (type == ParticleTypes.EFFECT
                || type == ParticleTypes.ENTITY_EFFECT || type == ParticleTypes.INSTANT_EFFECT)) {
            cir.setReturnValue(null);
        }
    }
}
