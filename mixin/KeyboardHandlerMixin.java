package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.event.impl.KeyInputEvent;
import dev.lucid.sreen.alt.AltManager;
import dev.lucid.sreen.clickgui.ClickGuiScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void lucid$onKeyPress(long window, int action, KeyEvent key, CallbackInfo ci) {
        Lucid lucid = Lucid.getInstance();
        if (lucid == null) return;
        KeyInputEvent event = lucid.getEventBus().post(new KeyInputEvent(window, key.key(), action));
        if (event.isCancelled()) ci.cancel();
    }

    /**
     * Enter/Backspace/Escape поля ввода ников.
     *
     * <p>Свой ввод ловится здесь, а не в {@code Screen#charTyped}: {@code TitleScreen} не
     * объявляет ни {@code charTyped}, ни {@code mouseScrolled} — они приходят как default-методы
     * интерфейса, и инъекция в экран их не нашла бы. {@code KeyboardHandler} — общая точка,
     * через которую проходят и символы, и клавиши, независимо от того, кто их объявил.</p>
     */
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void lucid$altKeyPress(long window, int action, KeyEvent key, CallbackInfo ci) {
        if (AltManager.onKeyPressed(key, action)) ci.cancel();
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true)
    private void lucid$altCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        if (AltManager.onCharTyped(event)) ci.cancel();
    }

    /**
     * Символы для полей ввода ClickGUI. Ловим здесь по той же причине, что и альты:
     * {@code ClickGuiScreen} не объявляет {@code charTyped}, injection в экран его
     * не нашла бы. Пока поле в фокусе, забираем все символы — ванили в GUI печатать нечего.
     */
    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true)
    private void lucid$clickGuiCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ClickGuiScreen gui && gui.onCharTyped(event)) ci.cancel();
    }
}
