package dev.lucid.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/**
 * Выбор ровно одного варианта из списка.
 *
 * <p>Значение — строка из {@link #getOptions()}, поэтому и в конфиг, и в GUI попадает
 * ровно то, что объявил модуль. Чужое значение (опечатка в коде, старое название
 * варианта в конфиге) молча отбрасывается: настройка всегда остаётся валидной,
 * а не падает в {@code null} посреди рендера.</p>
 */
public class SelectSetting extends Setting<String> {

    private final List<String> options;

    public SelectSetting(String name, String defaultValue, String... options) {
        super(name, defaultValue);
        if (options.length == 0) {
            throw new IllegalArgumentException("SelectSetting \"" + name + "\": пустой список вариантов");
        }
        this.options = List.of(options);
        // Дефолт вне списка — ошибка в модуле, но ронять клиент из-за настройки глупо:
        // откатываемся на первый вариант
        if (!this.options.contains(defaultValue)) this.value = this.options.get(0);
    }

    /**
     * Незнакомый вариант игнорируется — так конфиг с устаревшим названием ничего не ломает.
     * null отсекается отдельно: {@code List.of(..).contains(null)} бросает NPE, а не возвращает false.
     */
    @Override
    public void setValue(String newValue) {
        if (newValue == null) return;
        if (options.contains(newValue)) this.value = newValue;
    }

    public boolean is(String option) { return value.equals(option); }

    /** Следующий вариант по кругу — ЛКМ по строке в ClickGUI. */
    public void next() { value = options.get((index() + 1) % options.size()); }

    /** Предыдущий по кругу — ПКМ по строке. */
    public void previous() { value = options.get((index() - 1 + options.size()) % options.size()); }

    /** Индекс текущего варианта; -1 невозможен, пока значение ставится через {@link #setValue}. */
    public int index() { return options.indexOf(value); }

    public List<String> getOptions() { return options; }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            setValue(json.getAsString());
        }
    }
}
