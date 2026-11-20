package dev.lucid.util.players.friend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lucid.Lucid;
import dev.lucid.util.config.ConfigManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Список друзей — чисто клиентский статус: ни один пакет с этими никами не уходит,
 * сервер о них не знает. Друзья не привязаны ни к одному модулю, это отдельный
 * реестр, который позже смогут читать и другие части клиента.
 *
 * <p>Ник хранится ровно в том регистре, в котором его ввели, и сравнивается точным
 * {@code equals}: на сервере {@code haroHw243} и {@code harohw243} — разные имена,
 * поэтому приводить к нижнему регистру было бы молчаливым изменением смысла.
 * {@link LinkedHashSet} выбран вместо {@code HashSet}, чтобы {@code list} и JSON
 * сохраняли порядок добавления, а не выдавали ники в случайном порядке.</p>
 *
 * <p>Состояние лежит в уже существующем {@code config/lucid/config.json} секцией
 * {@code "friends": {"names": [...]}} — рядом с {@code "hud"} и {@code "alts"}.
 * Свой файл не заводим: так конфиг клиента остаётся одним, а запись по-прежнему
 * атомарная. Каждое изменение сразу переписывает файл целиком, поэтому удалённые
 * {@code remove}/{@code clear} ники физически исчезают из JSON, а не «висят» там
 * до выхода из игры.</p>
 */
public final class FriendManager {

    private static final String SECTION = "friends";
    private static final String NAMES_KEY = "names";
    private static final int MAX_NAME_LENGTH = 16;

    private static final LinkedHashSet<String> NAMES = new LinkedHashSet<>();

    private FriendManager() {}

    /** @return {@code true}, если ник действительно положили в список. */
    public static boolean add(String name) {
        if (!isValid(name) || !NAMES.add(name)) return false;
        save();
        return true;
    }

    /** @return {@code true}, если такой ник был в списке и удалён. */
    public static boolean remove(String name) {
        if (!NAMES.remove(name)) return false;
        save();
        return true;
    }

    /** @return сколько ников было удалено — нужно для ответа в чат. */
    public static int clear() {
        int count = NAMES.size();
        if (count == 0) return 0;
        NAMES.clear();
        save();
        return count;
    }

    /** Точное совпадение, без приведения регистра. */
    public static boolean contains(String name) {
        return name != null && NAMES.contains(name);
    }

    /** Копия в порядке добавления: вызывающий не может испортить реестр. */
    public static List<String> names() {
        return new ArrayList<>(NAMES);
    }

    public static int size() {
        return NAMES.size();
    }

    /**
     * Та же валидация, что в {@code AltManager}: ник лаунчера — это латиница, цифры
     * и подчёркивание, не длиннее 16 символов. Мусор в список не кладём, иначе он
     * никогда не совпадёт с {@code Player#getPlainTextName()} и будет только мешать.
     */
    public static boolean isValid(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) return false;
        for (int i = 0; i < name.length(); i++) {
            if (!isNameChar(name.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isNameChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_';
    }

    /** {@link ConfigManager#save()} переписывает корень целиком — сюда кладём свою секцию. */
    public static void write(JsonObject root) {
        JsonObject friends = new JsonObject();
        JsonArray names = new JsonArray();
        for (String name : NAMES) names.add(name);
        friends.add(NAMES_KEY, names);
        root.add(SECTION, friends);
    }

    /** Читаем до применения настроек модулей: порядок секций в файле значения не имеет. */
    public static void read(JsonObject root) {
        NAMES.clear();
        JsonObject friends = root.getAsJsonObject(SECTION);
        if (friends == null) return;

        JsonArray names = friends.getAsJsonArray(NAMES_KEY);
        if (names == null) return;
        for (JsonElement element : names) {
            if (element == null || !element.isJsonPrimitive()) continue;
            String name = element.getAsString();
            // Регистр не трогаем: два ника, отличающиеся только им, обязаны выжить
            // после перезапуска — LinkedHashSet сам уберёт только точные дубли.
            if (isValid(name)) NAMES.add(name);
        }
    }

    private static void save() {
        Lucid lucid = Lucid.getInstance();
        if (lucid != null && lucid.getConfigManager() != null) {
            lucid.getConfigManager().save();
        }
    }
}
