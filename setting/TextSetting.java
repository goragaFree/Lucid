package dev.lucid.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Строковая настройка — поле ввода в ClickGUI ({@code TextComponent}).
 *
 * <p>Значение проходит через {@link #setValue(String)} и там же чистится: обрезается
 * по краям, теряет цветовые коды и прижимается к лимиту длины. Поэтому ни компонент,
 * ни конфиг не могут положить в настройку мусор длиннее лимита — нормализация одна,
 * а не по копии в каждом месте.</p>
 *
 * <p>Пустая строка — легальное значение: модуль сам решает, что делать, когда текст
 * не задан (обычно откатывается на свой дефолт). Так поле «очистил и передумал»
 * не превращается в магическое значение в конфиге.</p>
 */
public class TextSetting extends Setting<String> {

    private final int maxLength;

    public TextSetting(String name, String defaultValue, int maxLength) {
        super(name, defaultValue);
        this.maxLength = Math.max(1, maxLength);
    }

    @Override
    public void setValue(String newValue) {
        if (newValue == null) return;
        String cleaned = newValue.replace("§", "").trim();
        this.value = cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    public int getMaxLength() { return maxLength; }

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
