package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.SeeInvisible;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    /**
     * В submit() прозрачное тело рисуется цветом 654311423 (0x26FFFFFF, ~15%).
     * Пока включён SeeInvisible — берём прозрачность из слайдера.
     */
    @ModifyConstant(method = "submit", constant = @Constant(intValue = 654311423))
    private int lucid$invisibleAlpha(int original) {
        SeeInvisible module = Lucid.getInstance().getModuleManager().get(SeeInvisible.class);
        return module != null && module.isEnabled() ? module.getColor() : original;
    }
}
