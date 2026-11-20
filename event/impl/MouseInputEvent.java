package dev.lucid.event.impl;

import dev.lucid.event.CancellableEvent;

/**
 * Сырое GLFW-событие кнопки мыши (нажатие/отпускание), симметричное
 * {@link KeyInputEvent}. Нужно для биндов-действий: {@code BindSetting} умеет
 * хранить и кнопку мыши, а {@code ModuleManager.onMousePressed} разбирает только
 * бинды включения модулей. Опрос {@code glfwGetMouseButton} на тике здесь не
 * годится — короткий клик может целиком поместиться между двумя тиками.
 */
public final class MouseInputEvent extends CancellableEvent {
    private final long window;
    private final int button;
    private final int action;

    public MouseInputEvent(long window, int button, int action) {
        this.window = window;
        this.button = button;
        this.action = action;
    }

    public long getWindow() { return window; }
    public int getButton() { return button; }
    public int getAction() { return action; }
}
