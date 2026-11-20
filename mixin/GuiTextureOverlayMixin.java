package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.NoRender;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoRender → Screen → Pumpkin. Через renderTextureOverlay идёт и иней от рыхлого снега,
 * поэтому отсекаем по текстуре: остальные оверлеи остаются штатными.
 */
@Mixin(Gui.class)
public abstract class GuiTextureOverlayMixin {
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void lucid$noPumpkin(GuiGraphics graphics, Identifier texture, float alpha, CallbackInfo ci) {
        if (texture == null || !texture.getPath().contains("pumpkinblur")) return;
        var client = Lucid.getInstance();
        if (client == null || client.getModuleManager() == null) return;
        NoRender noRender = client.getModuleManager().get(NoRender.class);
        if (noRender != null && noRender.hidesPumpkin()) ci.cancel();
    }
}
