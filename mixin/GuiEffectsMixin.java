package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.Hud;
import dev.lucid.module.impl.render.NoRender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Не дублируем эффекты под карточкой Potions; при отключении возвращается ваниль.
 * NoRender → Screen → Effects прячет ванильные иконки и без карточки.
 */
@Mixin(Gui.class)
public abstract class GuiEffectsMixin {
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void lucid$customPotions(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        var client = Lucid.getInstance();
        if (client == null || client.getModuleManager() == null) return;
        Hud hud = client.getModuleManager().get(Hud.class);
        if (hud != null && hud.showsPotions()) {
            ci.cancel();
            return;
        }
        NoRender noRender = client.getModuleManager().get(NoRender.class);
        if (noRender != null && noRender.hidesEffects()) ci.cancel();
    }
}
