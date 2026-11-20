package dev.lucid.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Числовая настройка — слайдер в ClickGUI.
 *
 * <p>Хранит {@code double}, но живёт на сетке шага: любая запись идёт через
 * {@link #setValue(Double)}, где значение сначала прижимается к [min, max], а потом
 * округляется до кратного {@code step}. Поэтому ни слайдер, ни конфиг не могут
 * положить в поле что-то вне диапазона — нормализация не дублируется в GUI.</p>
 */
public class SliderSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;

    public SliderSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void setValue(Double newValue) {
        double clamped = Math.max(min, Math.min(max, newValue));
        this.value = Math.round(clamped / step) * step;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            setValue(json.getAsDouble()); // setValue сам прижмёт к min/max и шагу
        }
    }

    public int getInt() { return (int) Math.round(value); }
    public long getLong() { return Math.round(value); }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
}
