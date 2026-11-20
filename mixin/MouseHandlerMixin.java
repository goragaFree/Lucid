package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.CameraSetting;
import dev.lucid.event.impl.MouseInputEvent;
import dev.lucid.event.impl.MouseRotationEvent;
import dev.lucid.sreen.alt.AltManager;
import dev.lucid.sreen.hud.DragHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    /**
     * Отпускание ЛКМ в чате закрывает перетаскивание HUD-панели, нажатие — бинды модулей.
     *
     * <p>Нажатие по панели ловит сам {@code ChatScreen} (см. {@link ChatScreenMixin}): там
     * нужны точные координаты и приоритет над полем ввода. Отпусканию координаты не нужны,
     * а объявленного {@code mouseReleased} у {@code ChatScreen} нет — проще взять событие
     * мыши, которое приходит всегда.</p>
     */
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void lucid$onButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        Lucid lucid = Lucid.getInstance();
        if (lucid != null) {
            MouseInputEvent event = lucid.getEventBus().post(
                    new MouseInputEvent(window, button.button(), action));
            if (event.isCancelled()) {
                ci.cancel();
                return;
            }
        }

        if (action == GLFW.GLFW_RELEASE
                && button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mc.screen instanceof ChatScreen) {
            DragHud.onRelease();
            return;
        }

        // Бинды модулей — только в игре, без открытых экранов: в экранах кнопка принадлежит им
        if (action != GLFW.GLFW_PRESS) return;
        if (mc.screen != null) return;

        if (lucid != null && lucid.getModuleManager() != null) {
            lucid.getModuleManager().onMousePressed(button.button());
        }
    }
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void lucid$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().handle()) return;

        // Колёсико над списком ников забирает плашка, а не зум камеры и не хотбар
        if (AltManager.onMouseScrolled(
                mc.mouseHandler.getScaledXPos(mc.getWindow()),
                mc.mouseHandler.getScaledYPos(mc.getWindow()), vertical)) {
            ci.cancel();
            return;
        }

        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return;
        CameraSetting settings = lucid.getModuleManager().get(CameraSetting.class);
        if (settings != null && settings.onZoomScroll(vertical)) {
            // Intercept before vanilla accumulates wheel input or changes hotbar slots.
            ci.cancel();
        }
    }

    /**
     * Intercept the actual turn, not onMove: cursor movement in menus is untouched.
     * Vanilla has already applied sensitivity, smoothing and axis inversion here.
     */
    @Redirect(
            method = "turnPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),
            require = 1
    )
    private void lucid$onMouseRotation(LocalPlayer player, double cursorDeltaX, double cursorDeltaY) {
        Lucid lucid = Lucid.getInstance();
        if (lucid == null) {
            player.turn(cursorDeltaX, cursorDeltaY);
            return;
        }

        MouseRotationEvent event = lucid.getEventBus().post(
                new MouseRotationEvent(cursorDeltaX, cursorDeltaY));
        if (!event.isCancelled()) {
            player.turn(event.getCursorDeltaX(), event.getCursorDeltaY());
        }
    }
}
