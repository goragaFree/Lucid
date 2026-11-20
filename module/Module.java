package dev.lucid.module;

import dev.lucid.Lucid;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.setting.Setting;
import dev.lucid.util.Wrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Базовый класс для всех модулей. */
public abstract class Module implements Wrapper {

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private boolean enabled;
    // Бинд один и он либо клавиша, либо кнопка мыши: держать оба незачем, модуль
    // всё равно переключается одним нажатием. -1 = не назначено.
    private int key;
    private int mouseButton;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = -1;
        this.mouseButton = -1;
    }

    protected void addSettings(Setting<?>... settings) {
        this.settings.addAll(Arrays.asList(settings));
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            try {
                onEnable();
                Lucid.getInstance().getEventBus().subscribe(this);
            } catch (RuntimeException | Error failure) {
                this.enabled = false;
                Lucid.getInstance().getEventBus().unsubscribe(this);
                try { onDisable(); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        } else {
            Lucid.getInstance().getEventBus().unsubscribe(this);
            onDisable();
        }
    }

    /** Compatibility bridge: old modules continue receiving END ticks exactly once. */
    @Subscribe
    private void lucid$legacyTick(ClientTickEvent event) {
        if (enabled && event.getPhase() == ClientTickEvent.Phase.END && mc.player != null) {
            onTick();
        }
    }

    protected void onEnable() {}

    protected void onDisable() {}

    /** Вызывается каждый клиентский тик, пока модуль включён. */
    public void onTick() {}

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public int getKey() { return key; }
    public int getMouseButton() { return mouseButton; }

    /** Ставит бинд целиком — так его и читает конфиг, не рискуя затереть половину. */
    public void setBind(int key, int mouseButton) {
        this.key = key;
        this.mouseButton = mouseButton;
    }

    public void bindKey(int key) { setBind(key, -1); }

    public void bindMouse(int button) { setBind(-1, button); }

    public void clearBind() { setBind(-1, -1); }

    public boolean isBound() { return key != -1 || mouseButton != -1; }
}
