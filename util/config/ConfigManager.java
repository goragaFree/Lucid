package dev.lucid.util.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lucid.Lucid;
import dev.lucid.module.Module;
import dev.lucid.module.ModuleManager;
import dev.lucid.module.impl.render.CameraSetting;
import dev.lucid.setting.Setting;
import dev.lucid.sreen.alt.AltManager;
import dev.lucid.sreen.hud.DragHud;
import dev.lucid.util.players.friend.FriendManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Сохраняет и загружает состояние модулей: включён ли, бинд и все настройки.
 *
 * Файл: .minecraft/config/lucid/config.json
 * {
 *   "modules": {
 *     "Ambience": { "enabled": true, "key": -1, "mouse": -1, "settings": { "Time": 18000.0 } }
 *   }
 * }
 *
 * Рядом лежат независимые секции {@code "hud"} (смещения панелей), {@code "alts"}
 * (пул ников и применённый ник) и {@code "friends"} (сохранённые друзья) —
 * их читают/пишут сами владельцы.
 *
 * Модули и настройки ищутся по имени. Чего нет в файле — остаётся по умолчанию,
 * лишнее в файле (удалённый модуль) просто игнорируется.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ModuleManager moduleManager;
    private final Path file;

    public ConfigManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.file = FabricLoader.getInstance().getConfigDir().resolve(Lucid.MOD_ID).resolve("config.json");
    }

    public void load() {
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;

            JsonObject root = parsed.getAsJsonObject();
            // Позиции панелей HUD, ники и друзья: отдельные секции, к модулям отношения не имеют
            DragHud.read(root);
            AltManager.read(root);
            FriendManager.read(root);

            JsonObject modules = root.getAsJsonObject("modules");
            if (modules == null) return;

            for (Module module : moduleManager.getModules()) {
                JsonElement element = modules.get(module.getName());
                // A renamed module keeps the user's previous enabled state and binds.
                // A new-format entry always takes precedence over the legacy entry.
                if (element == null && module instanceof CameraSetting) element = modules.get("FreeLook");
                if (element != null && element.isJsonObject()) {
                    loadModule(module, element.getAsJsonObject());
                }
            }
            Lucid.LOGGER.info("Config loaded: {}", file);
        } catch (Exception exception) {
            // Битый файл не должен ронять игру: остаёмся на значениях по умолчанию
            Lucid.LOGGER.error("Failed to load config {}", file, exception);
        }
    }

    public void save() {
        JsonObject modules = new JsonObject();
        for (Module module : moduleManager.getModules()) {
            modules.add(module.getName(), saveModule(module));
        }
        JsonObject root = new JsonObject();
        root.add("modules", modules);
        DragHud.write(root);
        AltManager.write(root);
        FriendManager.write(root);

        try {
            Files.createDirectories(file.getParent());

            // Пишем во временный файл и подменяем: если игра упадёт посреди записи,
            // старый конфиг останется целым
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Lucid.LOGGER.error("Failed to save config {}", file, exception);
        }
    }

    private static JsonObject saveModule(Module module) {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", module.isEnabled());
        json.addProperty("key", module.getKey());
        json.addProperty("mouse", module.getMouseButton());

        JsonObject settings = new JsonObject();
        for (Setting<?> setting : module.getSettings()) {
            settings.add(setting.getName(), setting.toJson());
        }
        json.add("settings", settings);
        return json;
    }

    private static void loadModule(Module module, JsonObject json) {
        JsonObject settings = json.getAsJsonObject("settings");
        if (settings != null) {
            for (Setting<?> setting : module.getSettings()) {
                JsonElement value = settings.get(setting.getName());
                if (value == null && module instanceof CameraSetting camera
                        && setting == camera.getLookBind()) {
                    value = settings.get("Клавиша");
                }
                if (value != null) setting.fromJson(value);
            }
        }

        // Бинд ставится одной командой: раздельные setKey/setMouseButton затирали бы
        // друг друга, потому что клавиша и кнопка мыши взаимоисключающие.
        // Старый конфиг без "mouse" просто оставляет вторую половину как была.
        int key = json.has("key") ? json.get("key").getAsInt() : module.getKey();
        int mouse = json.has("mouse") ? json.get("mouse").getAsInt() : module.getMouseButton();
        module.setBind(key, mouse);

        // Включаем последним: onEnable уже увидит загруженные настройки
        if (json.has("enabled")) module.setEnabled(json.get("enabled").getAsBoolean());
    }
}
