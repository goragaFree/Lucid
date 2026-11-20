package dev.lucid.mixin;

import dev.lucid.module.impl.player.ItemScroller;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lucid$itemClick(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        ItemScroller module = ItemScroller.getActive();
        if (module != null && module.onClick((AbstractContainerScreen<?>) (Object) this,
                event.x(), event.y(), event.button())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void lucid$itemDrag(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        ItemScroller module = ItemScroller.getActive();
        if (module != null && module.onDrag((AbstractContainerScreen<?>) (Object) this,
                event.x(), event.y(), event.button())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void lucid$itemRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        ItemScroller module = ItemScroller.getActive();
        if (module != null && module.onRelease((AbstractContainerScreen<?>) (Object) this,
                event.button())) cir.setReturnValue(true);
    }

}
