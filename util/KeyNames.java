package dev.lucid.util;

import org.lwjgl.glfw.GLFW;

/**
 * Короткие имена биндов для GUI: {@code R}, {@code LShift}, {@code F5}, {@code MMB}, {@code M4}.
 *
 * <p>Своя таблица, а не ванильные {@code InputConstants}: у них имя вида
 * {@code key.keyboard.left.shift} и перевод зависит от языка игры, а строка модуля
 * шириной в 120 px таких слов не выдержит.</p>
 */
public final class KeyNames {

    /** Таблица по GLFW-кодам: null — код без короткого имени. */
    private static final String[] KEYS = new String[GLFW.GLFW_KEY_LAST + 1];

    static {
        for (int index = 0; index < 26; index++) {
            KEYS[GLFW.GLFW_KEY_A + index] = String.valueOf((char) ('A' + index));
        }
        for (int index = 0; index < 10; index++) {
            KEYS[GLFW.GLFW_KEY_0 + index] = String.valueOf(index);
            KEYS[GLFW.GLFW_KEY_KP_0 + index] = "Num" + index;
        }
        for (int index = 1; index <= 25; index++) {
            KEYS[GLFW.GLFW_KEY_F1 + index - 1] = "F" + index;
        }

        KEYS[GLFW.GLFW_KEY_SPACE] = "Space";
        KEYS[GLFW.GLFW_KEY_APOSTROPHE] = "'";
        KEYS[GLFW.GLFW_KEY_COMMA] = ",";
        KEYS[GLFW.GLFW_KEY_MINUS] = "-";
        KEYS[GLFW.GLFW_KEY_PERIOD] = ".";
        KEYS[GLFW.GLFW_KEY_SLASH] = "/";
        KEYS[GLFW.GLFW_KEY_SEMICOLON] = ";";
        KEYS[GLFW.GLFW_KEY_EQUAL] = "=";
        KEYS[GLFW.GLFW_KEY_LEFT_BRACKET] = "[";
        KEYS[GLFW.GLFW_KEY_BACKSLASH] = "\\";
        KEYS[GLFW.GLFW_KEY_RIGHT_BRACKET] = "]";
        KEYS[GLFW.GLFW_KEY_GRAVE_ACCENT] = "`";

        KEYS[GLFW.GLFW_KEY_ESCAPE] = "Esc";
        KEYS[GLFW.GLFW_KEY_ENTER] = "Enter";
        KEYS[GLFW.GLFW_KEY_TAB] = "Tab";
        KEYS[GLFW.GLFW_KEY_BACKSPACE] = "Backspace";
        KEYS[GLFW.GLFW_KEY_INSERT] = "Insert";
        KEYS[GLFW.GLFW_KEY_DELETE] = "Delete";
        KEYS[GLFW.GLFW_KEY_RIGHT] = "Right";
        KEYS[GLFW.GLFW_KEY_LEFT] = "Left";
        KEYS[GLFW.GLFW_KEY_DOWN] = "Down";
        KEYS[GLFW.GLFW_KEY_UP] = "Up";
        KEYS[GLFW.GLFW_KEY_PAGE_UP] = "PageUp";
        KEYS[GLFW.GLFW_KEY_PAGE_DOWN] = "PageDown";
        KEYS[GLFW.GLFW_KEY_HOME] = "Home";
        KEYS[GLFW.GLFW_KEY_END] = "End";
        KEYS[GLFW.GLFW_KEY_CAPS_LOCK] = "Caps";
        KEYS[GLFW.GLFW_KEY_SCROLL_LOCK] = "Scroll";
        KEYS[GLFW.GLFW_KEY_NUM_LOCK] = "NumLock";
        KEYS[GLFW.GLFW_KEY_PRINT_SCREEN] = "PrtSc";
        KEYS[GLFW.GLFW_KEY_PAUSE] = "Pause";

        KEYS[GLFW.GLFW_KEY_KP_DECIMAL] = "Num.";
        KEYS[GLFW.GLFW_KEY_KP_DIVIDE] = "Num/";
        KEYS[GLFW.GLFW_KEY_KP_MULTIPLY] = "Num*";
        KEYS[GLFW.GLFW_KEY_KP_SUBTRACT] = "Num-";
        KEYS[GLFW.GLFW_KEY_KP_ADD] = "Num+";
        KEYS[GLFW.GLFW_KEY_KP_ENTER] = "NumEnter";
        KEYS[GLFW.GLFW_KEY_KP_EQUAL] = "Num=";

        KEYS[GLFW.GLFW_KEY_LEFT_SHIFT] = "LShift";
        KEYS[GLFW.GLFW_KEY_LEFT_CONTROL] = "LCtrl";
        KEYS[GLFW.GLFW_KEY_LEFT_ALT] = "LAlt";
        KEYS[GLFW.GLFW_KEY_LEFT_SUPER] = "LWin";
        KEYS[GLFW.GLFW_KEY_RIGHT_SHIFT] = "RShift";
        KEYS[GLFW.GLFW_KEY_RIGHT_CONTROL] = "RCtrl";
        KEYS[GLFW.GLFW_KEY_RIGHT_ALT] = "RAlt";
        KEYS[GLFW.GLFW_KEY_RIGHT_SUPER] = "RWin";
        KEYS[GLFW.GLFW_KEY_MENU] = "Menu";
    }

    private KeyNames() {}

    /** Имя клавиши по GLFW-коду; "?" — код без имени (мультимедиа-клавиши и т. п.). */
    public static String ofKey(int key) {
        if (key < 0 || key > GLFW.GLFW_KEY_LAST) return "?";
        String name = KEYS[key];
        return name != null ? name : "?";
    }

    /** Имя кнопки мыши: LMB/RMB/MMB, дальше M4, M5, … — как считают сами мыши. */
    public static String ofMouse(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
            default -> "M" + (button + 1);
        };
    }

    /** Имя бинда модуля; {@code null} — бинда нет. */
    public static String of(int key, int mouseButton) {
        if (mouseButton != -1) return ofMouse(mouseButton);
        if (key != -1) return ofKey(key);
        return null;
    }

    /**
     * Парсит строку в GLFW-код клавиши.
     * @return код клавиши или -1 если не найдено
     */
    public static int parseKey(String name) {
        if (name == null || name.isEmpty()) return -1;
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        
        // Поиск в таблице
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i] != null && KEYS[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Парсит строку в код кнопки мыши.
     * @return код кнопки мыши или -1 если не найдено
     */
    public static int parseMouse(String name) {
        if (name == null || name.isEmpty()) return -1;
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        
        return switch (upper) {
            case "LMB" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case "RMB" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case "MMB" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            default -> {
                // M4, M5, etc.
                if (upper.startsWith("M") && upper.length() > 1) {
                    try {
                        int button = Integer.parseInt(upper.substring(1)) - 1;
                        if (button >= 0 && button <= 7) yield button;
                    } catch (NumberFormatException ignored) {}
                }
                yield -1;
            }
        };
    }
}
