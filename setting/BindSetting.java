package dev.lucid.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lucid.util.KeyNames;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** A function bind, independent of the module's enable/disable bind. */
public final class BindSetting extends Setting<BindSetting.Binding> {

    public record Binding(int key, int mouseButton) {
        public Binding {
            if (key != -1 && (key < GLFW.GLFW_KEY_SPACE || key > GLFW.GLFW_KEY_LAST)) {
                throw new IllegalArgumentException("Invalid keyboard key: " + key);
            }
            if (mouseButton < -1 || mouseButton > GLFW.GLFW_MOUSE_BUTTON_LAST) {
                throw new IllegalArgumentException("Invalid mouse button: " + mouseButton);
            }
            if (key != -1 && mouseButton != -1) {
                throw new IllegalArgumentException("A binding must have only one input");
            }
        }
    }

    public BindSetting(String name) {
        super(name, new Binding(-1, -1));
    }

    public BindSetting(String name, int defaultKey) {
        super(name, new Binding(defaultKey, -1));
    }

    @Override
    public void setValue(Binding binding) {
        super.setValue(Objects.requireNonNull(binding, "binding"));
    }

    public void bindKey(int key) { setValue(new Binding(key, -1)); }
    public void bindMouse(int button) { setValue(new Binding(-1, button)); }
    public void clearBind() { setValue(new Binding(-1, -1)); }
    public int getKey() { return value.key(); }
    public int getMouseButton() { return value.mouseButton(); }
    public boolean isBound() { return getKey() != -1 || getMouseButton() != -1; }

    public String getDisplayName() {
        String name = KeyNames.of(getKey(), getMouseButton());
        return name == null ? "None" : name;
    }

    /** Call on the client thread, with the game's window handle. */
    public boolean isDown(long window) {
        if (window == 0L) return false;
        if (getMouseButton() != -1) {
            return GLFW.glfwGetMouseButton(window, getMouseButton()) == GLFW.GLFW_PRESS;
        }
        return getKey() != -1 && GLFW.glfwGetKey(window, getKey()) == GLFW.GLFW_PRESS;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("key", getKey());
        json.addProperty("mouse", getMouseButton());
        return json;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonObject()) return;
        try {
            JsonObject object = json.getAsJsonObject();
            // Parse both before updating; malformed input preserves the previous bind.
            int key = readCode(object.get("key"));
            int mouse = readCode(object.get("mouse"));
            setValue(new Binding(key, mouse));
        } catch (IllegalArgumentException | ArithmeticException ignored) {
            // A broken setting must not prevent other settings from loading.
        }
    }

    private static int readCode(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected an integer input code");
        }
        return element.getAsBigDecimal().intValueExact();
    }
}
