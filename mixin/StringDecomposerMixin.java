package dev.lucid.mixin;

import dev.lucid.module.impl.player.NameProtect;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Подменяет ники в тексте прямо перед тем, как ваниль разберёт его на глифы.
 *
 * <p>В 1.21.11 это {@link StringDecomposer} (в Yarn-маппингах — {@code TextVisitFactory}).
 * Четырёхаргументная {@code iterateFormatted} — общая точка для сырых строк
 * ({@code Font.prepareText}, {@code StringSplitter.stringWidth}) и для содержимого
 * компонентов ({@code SubStringSource}, {@code iterateFormatted(FormattedText, ...)}):
 * обе сходятся сюда перед уходом в пятиаргументную перегрузку.</p>
 *
 * <p>Именно 4 аргумента, а не внутренняя 5-аргументная: {@code StringSplitter.splitLines}
 * зовёт её напрямую с ненулевым {@code startIndex}. Подмена строки другой длины там
 * сбила бы индексы переноса и текст поехал бы. {@code ModifyArg} меняет только
 * аргумент ухода внутрь, локальная строка 4-аргументного метода остаётся нетронутой.</p>
 */
@Mixin(StringDecomposer.class)
public class StringDecomposerMixin {

    @ModifyArg(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
                    ordinal = 0),
            index = 0)
    private static String lucid$protectNames(String text) {
        return NameProtect.filter(text);
    }
}
