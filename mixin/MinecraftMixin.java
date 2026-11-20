package dev.lucid.mixin;

import dev.lucid.module.impl.movement.InventoryMove;
import dev.lucid.module.impl.render.FreeCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FreeCam: пока камера-призрак летает, удары, ломание, ПКМ и средний клик гасим на
 * входе — иначе они пошли бы туда, куда смотрит персонаж, а не камера.
 *
 * <p>Все четыре метода зовёт {@code Minecraft.handleKeybinds}, а не отдельные экраны,
 * поэтому точка общая и покрывает и удержание ЛКМ ({@code continueAttack}), и
 * повторное нажатие.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void lucid$freeCamAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCam.shouldCancelInteractions()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void lucid$freeCamContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (FreeCam.shouldCancelInteractions()) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void lucid$freeCamUseItem(CallbackInfo ci) {
        if (FreeCam.shouldCancelInteractions()) ci.cancel();
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void lucid$freeCamPickBlock(CallbackInfo ci) {
        if (FreeCam.shouldCancelInteractions()) ci.cancel();
    }

    /**
     * InventoryMove: пока персонаж ещё движется или есть придержанные клики по слотам,
     * закрытие инвентаря откладывается до остановки.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void lucid$deferClose(Screen next, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (next == null && mc.screen != null && InventoryMove.INSTANCE != null
                && InventoryMove.INSTANCE.deferClose(mc.screen)) ci.cancel();
    }
}
