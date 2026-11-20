package dev.lucid.module.impl.player;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.TextSetting;
import dev.lucid.util.players.friend.FriendManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.User;

/**
 * Прячет свой ник (а по настройке — и ники друзей) за заменяющим текстом во всём,
 * что клиент рисует: чат, таб-лист, имена над головами, скорборд, GUI.
 *
 * <p>Подмена живёт в {@code StringDecomposerMixin}: это единственная точка, через
 * которую ваниль пропускает и сырые строки, и содержимое компонентов перед замером
 * ширины и отправкой глифов в рендер. Пакеты и {@code Component#getString()} хук не
 * трогает, поэтому сервер, подписи чата и буфер обмена получают настоящий текст —
 * закрывается ровно то, что видно на экране и в записи, а не логика игры.</p>
 *
 * <p>Тексты замены задаются в ClickGUI и живут в {@link TextSetting}: сверху поле
 * «Text» — что будет написано вместо своего ника, под ним галочка «Hide Friends»,
 * а когда она включена — поле «Friend Text» с заменой для друзей. Пустое поле не
 * ломает защиту: подмена откатывается на дефолт ({@link #DEFAULT_SELF_TEXT} /
 * {@link #DEFAULT_FRIEND_TEXT}), а не на «ничего не заменять» — иначе очистка поля
 * в GUI молча выключала бы защиту до перезапуска конфига.</p>
 *
 * <p>Свой ник берём из {@code mc.getUser().getName()}, а не из {@code GameProfile}:
 * именно это поле меняет {@code AltManager}, и после смены ника защищаться должен
 * новый. Регистр не приводим ни к какому виду — в этом клиенте {@code haroHw243} и
 * {@code harohw243} разные ники, и подменить чужой было бы хуже, чем не подменить
 * свой. Друзья читаются из {@link FriendManager}, не из своего списка: реестр один,
 * поэтому {@code .friend add} и {@code ClickFriend} сразу видны и здесь.</p>
 *
 * <p>Заменяется только целое слово: {@code Alexander} не превратится в
 * {@code Protecteder}, зато {@code <Nick>}, {@code [Nick]:} и {@code Nick joined}
 * закрываются. §-код перед ником не считается частью слова, иначе цветной ник в чате
 * остался бы открытым. Сам заменяющий текст цветовых кодов не содержит — их режет
 * {@link TextSetting#setValue} на входе, чтобы подмена не ломала раскраску строки.</p>
 *
 * <p>{@code stamp} нужен из-за кэша {@code MutableComponent#getVisualOrderText()}:
 * ваниль пересчитывает визуальный порядок только при смене объекта {@code Language}.
 * Включи модуль посреди игры — старые компоненты чата и таб-листа остались бы с
 * настоящим ником. Смена состояния сбрасывает этот кэш один раз на компонент, а не
 * каждый кадр.</p>
 */
public final class NameProtect extends Module {

    /** Подмена по умолчанию: пока пользователь не написал свой текст в поле. */
    public static final String DEFAULT_SELF_TEXT = "Protected";
    public static final String DEFAULT_FRIEND_TEXT = "Friend";

    /** Ник не длиннее 16 символов — замене длиннее некуда приходить. */
    private static final int MAX_TEXT_LENGTH = 16;

    // Миксин зовётся на каждом фрагменте текста; пробегать весь ModuleManager на
    // каждом вызове незачем. Модуль создаётся один раз в ModuleManager.init().
    private static NameProtect instance;

    private final TextSetting selfText = new TextSetting("Text", DEFAULT_SELF_TEXT, MAX_TEXT_LENGTH);
    private final BooleanSetting hideFriends = new BooleanSetting("Hide Friends", true);
    private final TextSetting friendText = new TextSetting("Friend Text", DEFAULT_FRIEND_TEXT, MAX_TEXT_LENGTH);
    {
        friendText.visibleWhen(hideFriends::getValue);
    }

    /** Версия состояния подмены. Растёт при включении/выключении и смене любого входа. */
    private static int stamp;
    private String lastSignature;

    public NameProtect() {
        super("NameProtect", "Replace your and friends' nicknames with custom text", Category.PLAYER);
        addSettings(selfText, hideFriends, friendText);
        instance = this;
    }

    @Override
    protected void onEnable() {
        lastSignature = stateSignature();
        bumpStamp();
    }

    @Override
    protected void onDisable() {
        lastSignature = null;
        bumpStamp();
    }

    /** Список друзей, галочку и тексты замены можно поменять в ClickGUI — ловим раз в тик. */
    @Override
    public void onTick() {
        String current = stateSignature();
        if (current.equals(lastSignature)) return;
        lastSignature = current;
        bumpStamp();
    }

    /** Точка входа для {@code StringDecomposerMixin}. Выключен — строка уходит как есть. */
    public static String filter(String text) {
        NameProtect module = instance;
        if (text == null || text.isEmpty() || module == null || !module.isEnabled()) return text;
        return module.protectNames(text);
    }

    /** Точка входа для {@code MutableComponentMixin}: изменилась — кэш надо пересобрать. */
    public static int stamp() {
        return stamp;
    }

    private static void bumpStamp() {
        stamp++;
    }

    private String stateSignature() {
        User user = mc.getUser();
        String self = user == null ? "" : user.getName();
        return self + "|" + hideFriends.getValue() + "|" + selfText.getValue()
                + "|" + friendText.getValue() + "|" + FriendManager.names();
    }

    private String protectNames(String text) {
        User user = mc.getUser();
        String result = protectName(text, user == null ? null : user.getName(), selfReplacement());
        if (!hideFriends.getValue()) return result;
        for (String friend : FriendManager.names()) {
            result = protectName(result, friend, friendReplacement());
        }
        return result;
    }

    private String selfReplacement() {
        return selfText.getValue().isEmpty() ? DEFAULT_SELF_TEXT : selfText.getValue();
    }

    private String friendReplacement() {
        return friendText.getValue().isEmpty() ? DEFAULT_FRIEND_TEXT : friendText.getValue();
    }

    /**
     * Заменяет все вхождения ника, которые не прилипли к чужому слову. Строитель
     * создаётся только после первой реальной замены: большинство строк чужие.
     */
    private static String protectName(String text, String name, String replacement) {
        if (name == null || name.isEmpty()) return text;
        StringBuilder out = null;
        int copied = 0;
        int from = 0;
        while (true) {
            int start = text.indexOf(name, from);
            if (start < 0) break;
            int end = start + name.length();
            if (isWholeWord(text, start, end)) {
                if (out == null) out = new StringBuilder(text.length());
                out.append(text, copied, start).append(replacement);
                copied = end;
            }
            from = end;
        }
        if (out == null) return text;
        return out.append(text, copied, text.length()).toString();
    }

    /** Граница слова: ванильные ники — латиница, цифры и подчёркивание. */
    private static boolean isWholeWord(String text, int start, int end) {
        int before = start - 1;
        // "§cNick": буква цветового кода не часть ника, отступаем на весь код.
        while (before >= 1 && text.charAt(before - 1) == '§'
                && ChatFormatting.getByCode(text.charAt(before)) != null) {
            before -= 2;
        }
        if (before >= 0 && isNameChar(text.charAt(before))) return false;
        return end >= text.length() || !isNameChar(text.charAt(end));
    }

    private static boolean isNameChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_';
    }
}
