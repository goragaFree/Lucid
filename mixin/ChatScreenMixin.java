package dev.lucid.mixin;

import dev.lucid.command.CommandManager;
import dev.lucid.sreen.hud.DragHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Перетаскивание HUD-панелей в чате: клик приходит от самого экрана.
 *
 * <p>Событие берётся у {@code ChatScreen}, а не у {@code MouseHandler}: экран — тот, кто
 * реально получает клик, и координаты в событии уже в GUI-пространстве панелей, ровно те,
 * что видит игрок под курсором. Хук на мышь ниже по течению ({@code MouseHandler.onButton})
 * тоже срабатывает, но между ним и отрисовкой успевает пройти кадр, и нажатие можно
 * потерять — именно так панели и отказывались таскаться.</p>
 *
 * <p>Отпускание ловится в {@link MouseHandlerMixin}: у {@code ChatScreen} своего
 * {@code mouseReleased} нет, а координаты для конца перетаскивания не нужны.</p>
 *
 * <p>Здесь же перехватываются клиентские команды с префиксом {@code "."}. Хук стоит
 * в HEAD {@code handleChatInput}: ваниль дальше отправила бы строку как обычный чат
 * ({@code sendChat} → {@code ServerboundChatPacket}), а нам нужно, чтобы она не
 * покидала клиент вообще. Нормализацию зовём сами — она была бы первым шагом
 * отменённого метода. Историю чата (стрелка вверх) добавляем вручную, потому что
 * вместе с отправкой отменяется и {@code addRecentChat}.</p>
 *
 * <p>Usage-строка для {@code .friend add <name>} рисуется в TAIL {@code render}:
 * ванильный {@code CommandSuggestions} строит usage из Brigadier-разбора, который
 * существует только для "/", поэтому для "." своей строки у него нет. Позиция и
 * подложка повторяют {@code CommandSuggestions#renderUsage} — над полем ввода.</p>
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lucid$mouseClicked(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        // Панель забрала клик — чату его не отдаём, иначе каретка прыгнет в поле ввода
        if (DragHud.onPress(event.x(), event.y())) cir.setReturnValue(true);
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void lucid$onChatInput(String message, boolean addToHistory, CallbackInfo ci) {
        String normalized = ((ChatScreen) (Object) this).normalizeChatMessage(message);
        if (!CommandManager.isClientCommand(normalized)) return;

        Minecraft mc = Minecraft.getInstance();
        if (addToHistory && mc != null && mc.gui != null) {
            mc.gui.getChat().addRecentChat(normalized);
        }
        CommandManager.execute(normalized);
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void lucid$renderCommandUsage(GuiGraphics graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        String hint = CommandManager.getUsageHint(input.getValue());
        if (hint == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        int x = 4;
        int y = ((ChatScreen) (Object) this).height - 27;
        int width = mc.font.width(hint);
        // Та же подложка, что у ванильного usage: полупрозрачный чёрный без тени
        graphics.fill(x - 1, y - 1, x + width + 1, y + 11, 0xD0000000);
        graphics.drawString(mc.font, hint, x, y + 2, 0xFFC0C0C0);
    }
}
