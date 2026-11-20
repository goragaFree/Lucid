package dev.lucid.module.impl.misc;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.SliderSetting;

/**
 * Настройки чата: схлопывание повторов, плавная прокрутка и размер истории.
 *
 * <p><b>AntiSpam.</b> Повторяющиеся подряд сообщения не плодят строки: первая
 * копия остаётся в чате, а каждая следующая поднимает у неё счётчик — «коч»,
 * «коч», «коч» превращаются в одну строку «коч x3». Сравнивается не Component, а
 * его текст: у серверных сообщений с одинаковым текстом компоненты всё равно
 * разные объекты, а у системных (join/leave) ещё и время добавления. Смотрит
 * только на <i>подряд идущие</i> сообщения: «а б а» — это три строки, не «а x2».
 * Счётчик пишется в конец первой копии через {@code Component.copy()}, поэтому
 * кликабельные ссылки и цвета исходного сообщения сохраняются.</p>
 *
 * <p><b>Smooth Chat.</b> Колесо мыши и PgUp/PgDn меняют не позицию скролла
 * напрямую, а целевую — рендер каждый кадр подтягивает видимую позицию к цели,
 * и текст чата едет плавно, а не прыгает по 7 строк. Никакого «выезда» новых
 * сообщений тут больше нет: сама прокрутка — единственное, что анимируется
 * (выезжающий низ чата и правда выглядел ушлепски, а скролл от него не зависит).
 * Работает и в свёрнутом чате, и в открытом экране чата — рендер у них общий.</p>
 *
 * <p><b>History.</b> Сколько сообщений ваниль хранит для прокрутки: 100 (как в
 * ванили) до 1000. Значение подставляется в оба лимита {@code ChatComponent} —
 * и для строк рендера ({@code trimmedMessages}), и для полной истории
 * ({@code allMessages}); новое значение вступает в силу по мере поступления
 * сообщений, задним числом старые не дописываются.</p>
 *
 * <p>Модуль ловит сообщения в {@code ChatComponentMixin#lucid$addMessage},
 * поэтому покрываются все источники: и серверный чат, и системные сообщения,
 * и команды клиента ({@code .friend}), и другие моды. Весь код — в миксине,
 * модуль хранит только настройки и быстрый доступ к ним (как NameProtect).</p>
 */
public final class ChatSetting extends Module {

    /** Повтор, старше этого числа тиков, — уже не повтор, а новое сообщение. */
    private static final int REPEAT_WINDOW_TICKS = 200;

    private static ChatSetting instance;

    private final BooleanSetting antiSpam = new BooleanSetting("AntiSpam", true);
    private final BooleanSetting smoothChat = new BooleanSetting("Smooth Chat", true);

    /** Длительность подгонки скролла к цели, в секундах. */
    private final SliderSetting smoothDuration = new SliderSetting("Duration", 0.2, 0.05, 0.5, 0.05);
    {
        smoothDuration.visibleWhen(smoothChat::getValue);
    }

    /** Сколько сообщений хранить для прокрутки: ванильные 100 — максимум 1000. */
    private final SliderSetting history = new SliderSetting("History", 100, 100, 1000, 50);

    public ChatSetting() {
        super("Chat Setting", "Anti-spam folding, smooth chat scrolling and history size", Category.MISC);
        addSettings(antiSpam, smoothChat, smoothDuration, history);
        instance = this;
    }

    /** Точка входа для {@code ChatComponentMixin}: включён ли AntiSpam. */
    public static boolean isAntiSpam() {
        return instance != null && instance.isEnabled() && instance.antiSpam.getValue();
    }

    /** Точка входа для {@code ChatComponentMixin}: включён ли плавный скролл. */
    public static boolean isSmoothChat() {
        return instance != null && instance.isEnabled() && instance.smoothChat.getValue();
    }

    /** Точка входа для {@code ChatComponentMixin}: время анимации скролла, секунды. */
    public static float getSmoothDuration() {
        return instance == null ? 0.0F : instance.smoothDuration.getValue().floatValue();
    }

    /** Точка входа для {@code ChatComponentMixin}: лимит истории, сообщений. */
    public static int getHistory() {
        return instance == null ? 100 : instance.history.getInt();
    }

    /** Окно повтора, в тиках GUI: за пределами «коч x2» не продлевается. */
    public static int getRepeatWindowTicks() {
        return REPEAT_WINDOW_TICKS;
    }

    public BooleanSetting getAntiSpamSetting() { return antiSpam; }
    public BooleanSetting getSmoothChatSetting() { return smoothChat; }
    public SliderSetting getSmoothDurationSetting() { return smoothDuration; }
    public SliderSetting getHistorySetting() { return history; }
}
