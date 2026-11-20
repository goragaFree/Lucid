package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
import net.minecraft.world.level.biome.Biome;

/**
 * Меняет время суток и погоду только у тебя на клиенте.
 * Пакеты времени от сервера блокируются в ClientPacketListenerMixin,
 * вид осадков подменяется в WeatherEffectRendererMixin.
 */
public class Ambience extends Module {

    // Выключено — время идёт как на сервере, модуль трогает только погоду.
    private final BooleanSetting customTime = new BooleanSetting("Custom Time", true);

    // 0 = рассвет, 6000 = полдень, 13000 = закат, 18000 = полночь
    private final SliderSetting time = new SliderSetting("Time", 18000, 0, 24000, 100);
    {
        time.visibleWhen(customTime::getValue);
    }

    // Off — не трогаем погоду вовсе, Clear — гасим то, что прислал сервер,
    // Rain/Snow — навязываем своё. Меняется только локальный уровень дождя:
    // сервер о настройке не знает и погоду у себя не крутит.
    private final SelectSetting weather = new SelectSetting("Weather", "Off", "Off", "Clear", "Rain", "Snow");

    // Уровень дождя живёт в поле уровня и переживает выключение модуля: без этой
    // отметки после Off остался бы висеть принудительный дождь.
    private boolean forced;

    public Ambience() {
        super("Ambience", "Changes client-side time and weather", Category.RENDER);
        addSettings(customTime, time, weather);
    }

    @Override
    public void onTick() {
        if (mc.level == null) return;
        if (customTime.getValue()) mc.level.getLevelData().setDayTime(time.getLong());
        applyWeather();
    }

    @Override
    protected void onDisable() {
        if (mc.level == null) return;
        if (forced) restoreWeather();
        forced = false;
    }

    /**
     * Ставим каждый тик, а не только по смене настройки: сервер присылает
     * RAIN_LEVEL_CHANGE на переходах и иначе перебил бы значение.
     */
    private void applyWeather() {
        if (weather.is("Off")) {
            if (forced) {
                restoreWeather();
                forced = false;
            }
            return;
        }
        // Ад и энд: неба нет, осадкам там взяться неоткуда
        if (!mc.level.canHaveWeather()) return;
        mc.level.setRainLevel(weather.is("Clear") ? 0.0F : 1.0F);
        forced = true;
    }

    /**
     * Возвращает то, что считает сервер, — иначе дождь провисит до следующего пакета погоды.
     * Гром не трогаем: {@code getThunderLevel} и так умножается на уровень дождя.
     */
    private void restoreWeather() {
        mc.level.setRainLevel(mc.level.getLevelData().isRaining() ? 1.0F : 0.0F);
    }

    /**
     * Вид осадков, который навязывает настройка, либо {@code null}, если навязывать нечего.
     * Нужен миксину отрисовки погоды: там решается, рисовать дождь или снег.
     */
    public Biome.Precipitation getForcedPrecipitation() {
        if (weather.is("Snow")) return Biome.Precipitation.SNOW;
        if (weather.is("Rain")) return Biome.Precipitation.RAIN;
        return null;
    }

    /** true, когда уровень дождя задаёт настройка, — тогда серверные RAIN_LEVEL_CHANGE лишние. */
    public boolean overridesRainLevel() {
        return !weather.is("Off");
    }

    /** true, когда время задаёт слайдер, — тогда серверные пакеты времени лишние. */
    public boolean overridesTime() {
        return customTime.getValue();
    }

    public SliderSetting getTime() {
        return time;
    }
}
