package dev.lucid.mixin;

import dev.lucid.command.CommandManager;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

/**
 * Отдаёт ванильному списку подсказок наших кандидатов вместо серверных completion'ов.
 *
 * <p>{@code CommandSuggestions#updateCommandInfo} для ввода без "/" берёт слова из
 * {@code ClientSuggestionProvider#getCustomTabSugggestions()}. Подменяя только этот
 * вызов, мы получаем тот же выпадающий список, что у серверных команд: Tab и стрелки
 * подставляют слово, колёсико листает длинный список, свой рендер не нужен.
 * Сужать кандидатов по набранному префиксу не надо — это уже делает
 * {@code SharedSuggestionProvider.matchesSubStr} внутри {@code suggest(...)}.</p>
 *
 * <p>Второй инжект нужен потому, что в не-командной ветке ваниль сама список не
 * показывает: {@code showSuggestions} там зовётся только из {@code updateUsageInfo},
 * которая работает лишь с Brigadier-разбором "/"-команд. Для "." показываем список
 * сразу после пересчёта кандидатов — по мере набора, как у обычных команд.</p>
 */
@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {

    @Shadow
    private EditBox input;

    @Redirect(method = "updateCommandInfo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientSuggestionProvider;getCustomTabSugggestions()Ljava/util/Collection;"))
    private Collection<String> lucid$customTabSuggestions(ClientSuggestionProvider provider) {
        String value = input.getValue();
        int cursor = Math.max(0, Math.min(input.getCursorPosition(), value.length()));
        String beforeCursor = value.substring(0, cursor);
        if (!CommandManager.isClientCommand(beforeCursor)) {
            return provider.getCustomTabSugggestions();
        }

        List<String> suggestions = CommandManager.getSuggestions(beforeCursor);
        return suggestions == null ? List.of() : suggestions;
    }

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void lucid$showClientCommandSuggestions(CallbackInfo ci) {
        if (!CommandManager.isClientCommand(input.getValue())) return;
        // Кандидаты уже посчитаны синхронно (CompletableFuture от buildFuture готов),
        // поэтому список можно показать в том же вызове, без ожидания кадра.
        ((CommandSuggestions) (Object) this).showSuggestions(false);
    }
}
