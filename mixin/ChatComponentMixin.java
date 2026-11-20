package dev.lucid.mixin;

import dev.lucid.module.impl.misc.ChatSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * AntiSpam, Smooth Chat и лимит истории из модуля Chat Setting. Всё живёт в одном
 * миксине, потому что точка входа общая — {@link ChatComponent}.
 *
 * <p><b>AntiSpam — как сворачиваются повторы.</b> Ваниль складывает сообщение
 * в два списка: {@code allMessages} (история) и {@code trimmedMessages}
 * (строки для рендера). При повторе новая запись не добавляется — верхняя
 * запись заменяется копией с приписанным «xN»: первая копия остаётся на
 * месте, счётчик растёт на ней самой. Для сравнения берётся текст
 * компонента: у двух одинаковых сообщений с сервера компоненты являются
 * разными объектами, а equals у GuiMessage смотрит ещё и на время
 * добавления. Исходный текст повторов хранится отдельно — после первого
 * сворачивания верхняя строка уже «коч x2», и со строкой «коч» её не
 * сравнить. Повторами считаются только подряд идущие сообщения не старше
 * окна в 10 секунд; время берётся у первой копии, так что окно не
 * продлевается каждым повтором бесконечно.</p>
 *
 * <p><b>Smooth Chat.</b> {@code scrollChat(int)} — единственная точка смены
 * позиции (колесо мыши, PgUp/PgDn, подтяжка при новом сообщении). Ванильный
 * перескок отменяется: {@code chatScrollbarPos} ставится в цель сразу
 * (скроллбар и границы ванили остаются верными), а видимая позиция
 * {@code lucid$scrollPos} догоняет её в рендере — рендер рисует целевое окно
 * строк, поза сдвигает его на недоигранный остаток, и текст чата «едет»
 * плавно вместо прыжка по 7 строк. Никакой анимации появления новых сообщений
 * здесь больше нет: выезжающий низ выглядел дёргано, и к скроллу отношения не
 * имеет. Сдвиг ставится парой инжектов HEAD/RETURN вокруг публичного
 * {@code render} — вызывать целевой метод из его же HEAD-инжекта нельзя
 * (рекурсия), поэтому сдвиг ставится до ванильного тела и снимается после.
 * Прогресс считается по System.nanoTime() между кадрами, скорость не зависит
 * от FPS; догоняет цель экспоненциально — быстрый разгон и мягкая остановка.</p>
 *
 * <p><b>History.</b> Ваниль обрезает обе истории константой 100 — в
 * {@code addMessageToDisplayQueue} ({@code trimmedMessages}) и в
 * {@code addMessageToQueue} ({@code allMessages}). Обе константы заменяются
 * слайдером «History» (100..1000). {@code require = 0}: если в другой версии
 * игры константа переедет, клиент не упадёт — лимит просто останется ванильным,
 * а остальные фичи модуля продолжат работать.</p>
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;
    @Shadow @Final private Minecraft minecraft;

    @Shadow protected abstract void refreshTrimmedMessages();

    @Shadow public abstract int getLinesPerPage();

    /** Исходный текст верхнего сообщения — до приписывания «xN». */
    @Unique private String lucid$foldedText;

    /** Исходный компонент верхнего сообщения: с него пересобирается «xN». */
    @Unique private Component lucid$foldedOriginal;

    /** Сколько раз верхнее сообщение уже повторилось; 1 — повторов не было. */
    @Unique private int lucid$repeatCount = 1;

    /** Видимая (анимируемая) позиция скролла, в строках. */
    @Unique private float lucid$scrollPos;

    /** Время последнего кадра рендера чата, наносекунды. */
    @Unique private long lucid$lastRenderNanos;

    /** Поза чата сейчас сдвинута анимацией — на RETURN её надо снять. */
    @Unique private boolean lucid$posePushed;

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    /**
     * Лимит строк рендера. Кривое значение из конфига не должно ронять игру,
     * поэтому и минимум, и максимум страхуются ещё раз здесь.
     */
    @ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100), require = 0)
    private int lucid$historyLimitTrimmed(int original) {
        return Mth.clamp(ChatSetting.getHistory(), 100, 1000);
    }

    /** Лимит полной истории — та же настройка, тот же страховочный диапазон. */
    @ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100), require = 0)
    private int lucid$historyLimitAll(int original) {
        return Mth.clamp(ChatSetting.getHistory(), 100, 1000);
    }

    // ------------------------------------------------------------------
    // AntiSpam
    // ------------------------------------------------------------------

    /**
     * AntiSpam: HEAD трёхаргументного {@code addMessage} — через него
     * проходят все сообщения (сервер, системы, команды клиента).
     */
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void lucid$foldRepeat(Component message, MessageSignature signature,
                                  net.minecraft.client.GuiMessageTag tag, CallbackInfo ci) {
        if (ChatSetting.isAntiSpam() && lucid$isRepeat(message)) {
            lucid$repeatCount++;
            lucid$bumpTopMessage();
            // Повтор уже виден в чате — никакой анимации появления не нужно
            ci.cancel();
            return;
        }
        lucid$repeatCount = 1;
        lucid$foldedText = message.getString();
        lucid$foldedOriginal = message.copy();
    }

    /** Верхнее сообщение совпадает по тексту и попало в окно повтора. */
    @Unique
    private boolean lucid$isRepeat(Component message) {
        if (lucid$foldedText == null || allMessages.isEmpty()) return false;
        GuiMessage top = allMessages.get(0);
        int age = this.minecraft.gui.getGuiTicks() - top.addedTime();
        if (age > ChatSetting.getRepeatWindowTicks()) return false;
        if (!lucid$foldedText.equals(message.getString())) return false;
        // Верхней записью должен быть тот самый сворачиваемый текст: историю
        // под нами могли изменить (удаление модератором) — тогда сворачивать
        // повтор поверх чужого сообщения нельзя.
        String topText = top.content().getString();
        return topText.equals(lucid$foldedText)
                || topText.equals(lucid$foldedText + " x" + lucid$repeatCount);
    }

    /**
     * Заменяет верхнюю запись копией исходного сообщения с актуальным «xN».
     * Счётчик приписывается всегда к исходнику, а не к прошлой свёрнутой
     * строке — иначе получалось бы «коч x2 x3». Счётчик серым: он не часть
     * сообщения, и с цветным текстом читается лучше.
     */
    @Unique
    private void lucid$bumpTopMessage() {
        GuiMessage top = allMessages.get(0);
        Component folded = lucid$foldedOriginal.copy()
                .append(Component.literal(" x" + lucid$repeatCount)
                        .withStyle(ChatFormatting.GRAY));
        // addedTime берём у шаблона: окно повтора отсчитывается от первой копии
        allMessages.set(0, new GuiMessage(top.addedTime(), folded, top.signature(), top.tag()));
        refreshTrimmedMessages();
    }

    // ------------------------------------------------------------------
    // Smooth Chat
    // ------------------------------------------------------------------

    /**
     * Плавный скролл: перехватываем ванильный {@code scrollChat}.
     * Ваниль ставит {@code chatScrollbarPos} мгновенно — мы отменяем это и
     * ставим цель сами. Серия вызовов (колесо, многострочное сообщение)
     * аккумулируется, потому что базой служит уже-committed цель.
     */
    @Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
    private void lucid$smoothScroll(int amount, CallbackInfo ci) {
        if (amount == 0) {
            ci.cancel();
            return;
        }
        if (!ChatSetting.isSmoothChat()) {
            lucid$scrollPos = chatScrollbarPos; // выровняться с ванилью
            return; // без плавности ваниль сама ставит позицию
        }

        int oldPos = chatScrollbarPos;
        ci.cancel();
        int target = Mth.clamp(oldPos + amount, 0,
                Math.max(0, trimmedMessages.size() - getLinesPerPage()));
        // Скроллбар и границы ванили смотрят на chatScrollbarPos — там цель.
        // Видимая позиция уже стартовала раньше (идёт анимация) либо равна
        // oldPos (первый вызов серии) — стартовать заново не нужно.
        chatScrollbarPos = target;
        if (target == 0) newMessageSinceScroll = false;
    }

    /**
     * HEAD публичного рендера: сдвигаем позу чата на недоигранный скролл.
     * Вызвать ванильный рендер из этого инжекта нельзя (рекурсия) — поэтому
     * только сдвиг, а снятие на RETURN.
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"))
    private void lucid$smoothRenderPush(GuiGraphics graphics, Font font, int tickCount,
                                        int mouseX, int mouseY, boolean focused, boolean changeCursor,
                                        CallbackInfo ci) {
        lucid$posePushed = false;
        if (!ChatSetting.isSmoothChat()) {
            lucid$scrollPos = chatScrollbarPos;
            return;
        }

        float offsetLines = lucid$advanceAnimations();
        if (offsetLines > -0.01F && offsetLines < 0.01F) return;

        double scale = (Double) this.minecraft.options.chatScale().get();
        double spacing = (Double) this.minecraft.options.chatLineSpacing().get();
        float lineHeight = (float) (9.0 * (spacing + 1.0));

        // Рендер внутри сам масштабирует позу, поэтому сдвиг в экранных
        // пикселях умножаем на масштаб чата.
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, offsetLines * lineHeight * (float) scale);
        lucid$posePushed = true;
    }

    /** RETURN того же рендера: снимаем сдвиг, поставленный HEAD-инжектом. */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("RETURN"))
    private void lucid$smoothRenderPop(GuiGraphics graphics, Font font, int tickCount,
                                       int mouseX, int mouseY, boolean focused, boolean changeCursor,
                                       CallbackInfo ci) {
        if (lucid$posePushed) {
            graphics.pose().popMatrix();
            lucid$posePushed = false;
        }
    }

    /**
     * Двигает скролл на один кадр и возвращает недоигранный остаток в строках:
     * положительный — чат ещё не доехал вверх.
     *
     * <p>Догоняет цель экспоненциально, причём с шагом, приведённым к реальному
     * времени кадра: множитель {@code 1 - exp(-dt / tau)} даёт одинаковую картинку
     * на любом FPS. За {@code duration} позиция доезжает до цели на ~95% (tau =
     * duration / 3), дальше срезается порогом — иначе хвост анимации тянулся бы
     * вечно.</p>
     */
    @Unique
    private float lucid$advanceAnimations() {
        long now = System.nanoTime();
        float frameSeconds = lucid$lastRenderNanos == 0L
                ? 0.0F : Math.min((now - lucid$lastRenderNanos) / 1.0E9F, 0.1F);
        lucid$lastRenderNanos = now;

        if (lucid$scrollPos != chatScrollbarPos) {
            float duration = Math.max(ChatSetting.getSmoothDuration(), 0.05F);
            float tau = duration / 3.0F;
            float gain = frameSeconds <= 0.0F ? 0.0F : 1.0F - (float) Math.exp(-frameSeconds / tau);
            lucid$scrollPos += (chatScrollbarPos - lucid$scrollPos) * gain;
            if (Math.abs(chatScrollbarPos - lucid$scrollPos) < 0.01F) {
                lucid$scrollPos = chatScrollbarPos;
            }
        }

        return chatScrollbarPos - lucid$scrollPos;
    }

    // ------------------------------------------------------------------
    // Сбросы
    // ------------------------------------------------------------------

    /** Очистка чата: анимации указывали бы в удалённые строки. */
    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void lucid$onClear(CallbackInfo ci) {
        lucid$scrollPos = chatScrollbarPos;
        lucid$foldedText = null;
        lucid$repeatCount = 1;
    }

    /** Рескейл чата: ваниль ставит позицию напрямую, подтягиваемся за ней. */
    @Inject(method = "rescaleChat", at = @At("HEAD"))
    private void lucid$onRescale(CallbackInfo ci) {
        lucid$scrollPos = chatScrollbarPos;
    }

    /**
     * Закрытие экрана чата: ваниль ставит позицию в 0 напрямую. Без сброса
     * чат после закрытия долго ехал бы к низу через всю историю.
     * TAIL, а не HEAD: в HEAD позиция ещё старая.
     */
    @Inject(method = "resetChatScroll", at = @At("TAIL"))
    private void lucid$onResetScroll(CallbackInfo ci) {
        lucid$scrollPos = chatScrollbarPos;
    }
}
