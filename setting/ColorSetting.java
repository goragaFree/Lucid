package dev.lucid.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Цветовая настройка — ARGB-число, в ClickGUI рисуется свотчем и HSV-пикером
 * ({@code ColorComponent}).
 *
 * <p>Хранит полный ARGB, но альфу пикер не меняет: установка всегда пишется с
 * альфой из значения, чтобы «прозрачный цвет по случайности» не приполз из конфига.
 * Значение вне 0..255 каналов Gson вернуть не может, а кривой цвет (например,
 * нулевая альфа) для рендера безопасен — фигура просто не нарисуется.</p>
 */
public class ColorSetting extends Setting<Integer> {

    public ColorSetting(String name, int defaultColor) {
        super(name, defaultColor);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            value = json.getAsInt();
        }
    }
}
