package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** При выключенном Hotbar ванильная панель остаётся нетронутой. */
@Mixin(Gui.class)
public abstract class GuiHotbarMixin {
    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void lucid$customHotbar(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        var client = Lucid.getInstance();
        if (client == null || client.getModuleManager() == null) return;
        Hud hud = client.getModuleManager().get(Hud.class);
        if (hud != null && hud.showsHotbar()) ci.cancel();
    }
}
