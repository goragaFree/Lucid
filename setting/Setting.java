package dev.lucid.setting;

import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Базовый класс настройки модуля. */
public abstract class Setting<T> {

    private final String name;
    protected T value;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.value = defaultValue;
    }

    public String getName() { return name; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }

    /**
     * Скрывает настройку в ClickGUI, пока условие ложно. Условие вычисляется на каждый кадр,
     * поэтому компоненты собираются один раз в конструкторе кнопки и не пересоздаются.
     *
     * <p>Скрытие не трогает значение: выключили родителя, включили обратно — настройка
     * вернулась какой была. Иначе конфиг молча сбрасывал бы чужой выбор.</p>
     */
    public Setting<T> visibleWhen(BooleanSupplier condition) {
        this.visibility = Objects.requireNonNull(condition);
        return this;
    }

    public boolean isVisible() { return visibility.getAsBoolean(); }

    /** Значение для конфига. */
    public abstract JsonElement toJson();

    /** Загрузка из конфига. Кривое значение не должно ронять игру — просто игнорируем его. */
    public abstract void fromJson(JsonElement json);
}
