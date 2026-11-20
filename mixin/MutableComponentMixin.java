package dev.lucid.mixin;

import dev.lucid.module.impl.player.NameProtect;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Сбрасывает кэш визуального порядка компонента, когда состояние NameProtect меняется.
 *
 * <p>{@link MutableComponent} кэширует {@code visualOrderText} и пересчитывает его
 * только при смене объекта {@code Language}. Без этого хука компоненты, уже показанные
 * до включения модуля (старые строки чата, имена в таб-листе), навсегда остались бы
 * с настоящим ником: {@code StringDecomposerMixin} вызывается как раз в момент сборки
 * кэша, а повторно ваниль его не зовёт.</p>
 *
 * <p>Сверяем номер состояния, а не просто «модуль включён»: так пересборка происходит
 * один раз на компонент при переключении, а не каждый кадр на каждом сообщении чата.
 * Смена языка ваниль по-прежнему ловит сама — мы только обнуляем {@code decomposedWith}.</p>
 */
@Mixin(MutableComponent.class)
public class MutableComponentMixin {

    @Shadow
    private Language decomposedWith;

    // 0 = «состояние ещё не менялось»: компонент собран при том же состоянии, кэш валиден.
    @Unique
    private int lucid$nameProtectStamp;

    @Inject(method = "getVisualOrderText()Lnet/minecraft/util/FormattedCharSequence;", at = @At("HEAD"))
    private void lucid$invalidateNameProtectCache(CallbackInfoReturnable<FormattedCharSequence> cir) {
        int current = NameProtect.stamp();
        if (current == lucid$nameProtectStamp) return;
        lucid$nameProtectStamp = current;
        decomposedWith = null;
    }
}
