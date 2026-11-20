package dev.lucid.mixin;

import dev.lucid.sreen.alt.AltManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Плашка менеджера ников в главном меню.
 *
 * <p>Рисуется в хвосте {@code render}: к этому моменту ваниль уже вывела фон, логотип и
 * кнопки, поэтому плашка всегда поверх них. Клик перехватывается в HEAD и, если попал по
 * плашке, не доходит до кнопок меню — иначе раскрытие списка могло бы заодно нажать кнопку
 * под ним.</p>
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void lucid$renderAlts(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AltManager.render(graphics, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void lucid$altMouseClicked(MouseButtonEvent event, boolean doubled,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (AltManager.onMouseClicked(event)) cir.setReturnValue(true);
    }
}
