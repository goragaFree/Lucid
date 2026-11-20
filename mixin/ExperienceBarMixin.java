package dev.lucid.mixin;

import dev.lucid.sreen.hud.HotbarStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Меняем лишь XP-полосу: переключение игры на locator/jump bar остаётся штатным. */
@Mixin(ExperienceBarRenderer.class)
public abstract class ExperienceBarMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void lucid$experience(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!HotbarStatus.active()) return;
        HotbarStatus.experience(graphics);
        ci.cancel();
    }
}
