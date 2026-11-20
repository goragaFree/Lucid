package dev.lucid.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Выбор любого числа вариантов из списка — от нуля до всех сразу.
 *
 * <p>Внутри — {@link LinkedHashSet}, но порядок всегда порядок объявления вариантов,
 * а не порядок кликов: и конфиг, и рендер тогда не зависят от того, в какой
 * последовательности игрок тыкал галочки.</p>
 *
 * <p>Наружу значение отдаётся только для чтения: менять набор можно через
 * {@link #toggle(String)}, {@link #select(String...)} и {@link #clear()}, иначе
 * в поле можно было бы подложить вариант, которого нет в списке.</p>
 */
public class MultiSelectSetting extends Setting<Set<String>> {

    private final List<String> options;

    public MultiSelectSetting(String name, String... options) {
        this(name, List.of(), options);
    }

    public MultiSelectSetting(String name, Collection<String> defaultSelected, String... options) {
        super(name, new LinkedHashSet<>());
        if (options.length == 0) {
            throw new IllegalArgumentException("MultiSelectSetting \"" + name + "\": пустой список вариантов");
        }
        this.options = List.of(options);
        select(defaultSelected);
    }

    /** Всё, чего нет в объявленном списке, отсекается — и из конфига, и из кода. */
    @Override
    public void setValue(Set<String> newValue) {
        if (newValue == null) return;
        Set<String> filtered = new LinkedHashSet<>();
        for (String option : options) {
            if (newValue.contains(option)) filtered.add(option);
        }
        this.value = filtered;
    }

    @Override
    public Set<String> getValue() {
        return Collections.unmodifiableSet(value);
    }

    public boolean isSelected(String option) { return value.contains(option); }

    public void toggle(String option) {
        if (!options.contains(option)) return;
        Set<String> updated = new LinkedHashSet<>(value);
        if (!updated.remove(option)) updated.add(option);
        setValue(updated); // разложит по порядку объявления
    }

    public void select(String... selected) {
        select(List.of(selected));
    }

    public void select(Collection<String> selected) {
        if (selected != null) setValue(new LinkedHashSet<>(selected));
    }

    public void clear() {
        value = new LinkedHashSet<>();
    }

    public int count() { return value.size(); }

    public List<String> getOptions() { return options; }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (String option : value) array.add(option);
        return array;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonArray()) return;
        Set<String> loaded = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray()) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                loaded.add(element.getAsString());
            }
        }
        setValue(loaded); // устаревшие названия не воскресают
    }
}
