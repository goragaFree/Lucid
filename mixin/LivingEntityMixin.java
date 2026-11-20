package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.NoRender;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NoRender → Effect → Nausea. Сила тошноты для камеры (GameRenderer) и пелены (Gui)
 * берётся из одного места — getEffectBlendFactor, поэтому обнуляем её здесь.
 * Сам эффект остаётся на игроке: меняется только отрисовка на твоём клиенте.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
    private void lucid$noNausea(Holder<MobEffect> effect, float partialTick,
                                CallbackInfoReturnable<Float> cir) {
        if (effect != MobEffects.NAUSEA) return;
        if ((Object) this != Minecraft.getInstance().player) return;
        var client = Lucid.getInstance();
        if (client == null || client.getModuleManager() == null) return;
        NoRender noRender = client.getModuleManager().get(NoRender.class);
        if (noRender != null && noRender.hidesNausea()) cir.setReturnValue(0f);
    }
}
