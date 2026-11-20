package dev.lucid.mixin;

import dev.lucid.sreen.hud.HotbarItemCount;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Инвентарь и контейнеры используют обычный шрифт; наш хотбар — Lucid. */
@Mixin(GuiGraphics.class)
public abstract class GuiItemCountMixin {
    @Inject(method = "renderItemCount", at = @At("HEAD"), cancellable = true)
    private void lucid$hotbarCount(Font font, ItemStack stack, int x, int y, String override, CallbackInfo ci) {
        if (!HotbarItemCount.active()) return;
        HotbarItemCount.render((GuiGraphics) (Object) this, stack, x, y, override);
        ci.cancel();
    }
}
