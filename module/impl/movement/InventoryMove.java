package dev.lucid.module.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.sreen.clickgui.ClickGuiScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Ходьба с открытым инвентарём.
 *
 * <p>Работает только в двух экранах: инвентарь игрока ({@link InventoryScreen}) и
 * клик-гуи ({@link ClickGuiScreen}). В режиме бинда клик-гуи движение стоит — иначе
 * клавиши бинда двигали бы персонажа. Сундуки, верстаки и все прочие экраны
 * ведут себя ванильно.</p>
 *
 * <p>Само движение: в START-тике флагам семи {@link KeyMapping} возвращается физическое
 * состояние клавиш (через {@code setDown}, а не {@code set}, чтобы не копить
 * {@code clickCount}). Остальное делает ваниль.</p>
 *
 * <p>Legit-часть, три независимые настройки:</p>
 * <ul>
 *   <li><b>Stop Before Close</b> — закрытие экрана откладывается, пока персонаж не остановится
 *   (но не дольше {@link #STOP_LIMIT_TICKS} тиков).</li>
 *   <li><b>Delay Clicks</b> — клики по слотам во время движения придерживаются и уходят
 *   пачкой перед закрытием.</li>
 *   <li><b>Block Risky Clicks</b> — ПКМ-клики и PICKUP_ALL во время движения отменяются.</li>
 * </ul>
 *
 * <p>Хуки {@link #deferClose}, {@link #holdPacket} и {@link #cancelClick} вызываются из миксинов.</p>
 */
public final class InventoryMove extends Module {

    public static InventoryMove INSTANCE;

    private static final double STOP_SPEED = 0.03;
    private static final int STOP_LIMIT_TICKS = 3;

    private final BooleanSetting stopBeforeClose = new BooleanSetting("Stop Before Close", true);
    private final BooleanSetting delayClicks = new BooleanSetting("Delay Clicks", true);
    private final BooleanSetting blockRiskyClicks = new BooleanSetting("Block Risky Clicks", true);

    /** Ровно семь флагов, из которых KeyboardInput собирает Input. */
    private KeyMapping[] movement;

    /** true, пока флаги под нашим управлением. */
    private boolean walking;

    /** Экран, закрытие которого отложено. */
    private Screen pendingScreen;
    private int closeTicks;
    private boolean releasing;

    private final List<Packet<?>> heldClicks = new ArrayList<>();
    private boolean flushing;

    public InventoryMove() {
        super("InventoryMove", "Walk while an inventory is open", Category.MOVEMENT);
        addSettings(stopBeforeClose, delayClicks, blockRiskyClicks);
        INSTANCE = this;
    }

    // ───────────────────────── тик ─────────────────────────

    @Subscribe
    private void onClientTick(ClientTickEvent event) {
        if (event.getPhase() != ClientTickEvent.Phase.START || !isEnabled()) return;

        if (pendingScreen != null) {
            if (mc.player == null || mc.screen != pendingScreen) {
                // Экран закрыли иначе (сервер, дисконнект): не зависаем.
                pendingScreen = null;
                flushClicks();
            } else {
                tickPendingClose();
                return;
            }
        }

        Screen screen = mc.screen;
        if (screen == null) {
            walking = false;
            if (!heldClicks.isEmpty()) flushClicks();
            return;
        }

        if (mc.player == null || mc.level == null || !mc.isWindowActive()
                || mc.player.isSleeping() || mc.getCameraEntity() != mc.player
                || !allowed(screen)) {
            if (walking) release();
            return;
        }

        walking = true;
        sync();
    }

    /** Ожидание остановки: ввод заблокирован, спринт сброшен. */
    private void tickPendingClose() {
        for (KeyMapping key : movement()) {
            if (!toggled(key)) key.setDown(false);
        }
        mc.player.setSprinting(false);

        if (++closeTicks >= STOP_LIMIT_TICKS || !isMoving()) {
            flushClicks();
            Screen screen = pendingScreen;
            pendingScreen = null;
            releasing = true;
            try {
                screen.onClose();
            } finally {
                releasing = false;
            }
        }
    }

    // ───────────────────────── хуки миксинов ─────────────────────────

    /** Миксин на Minecraft#setScreen(null). true = отменить закрытие. */
    public boolean deferClose(Screen screen) {
        if (releasing || pendingScreen != null) return false;
        if (!isEnabled() || mc.player == null || !allowed(screen)) return false;

        boolean stop = stopBeforeClose.getValue() && isMoving();
        if (!stop && heldClicks.isEmpty()) return false;

        pendingScreen = screen;
        closeTicks = 0;
        return true;
    }

    /** Миксин на ClientCommonPacketListenerImpl#send. true = пакет придержан. */
    public boolean holdPacket(Packet<?> packet) {
        if (flushing || !isEnabled() || !delayClicks.getValue()) return false;
        if (!(packet instanceof ServerboundContainerClickPacket)) return false;
        if (mc.screen == null || mc.player == null) return false;
        if (heldClicks.isEmpty() && !isMoving() && !movementKeysDown()) return false;

        heldClicks.add(packet);
        return true;
    }

    /** Миксин на MultiPlayerGameMode#handleInventoryMouseClick. true = клик отменить. */
    public boolean cancelClick(ClickType type, int button) {
        if (!isEnabled() || !blockRiskyClicks.getValue() || mc.player == null) return false;
        if (heldClicks.isEmpty() && !isMoving() && !movementKeysDown()) return false;

        boolean rightClick = button == 1 && type != ClickType.SWAP && type != ClickType.THROW;
        return rightClick || type == ClickType.PICKUP_ALL;
    }

    // ───────────────────────── внутренности ─────────────────────────

    private boolean isMoving() {
        if (mc.player == null) return false;
        Vec3 v = mc.player.getDeltaMovement();
        return Math.abs(v.x) > STOP_SPEED || Math.abs(v.z) > STOP_SPEED;
    }

    private boolean movementKeysDown() {
        return physicallyDown(mc.options.keyUp) || physicallyDown(mc.options.keyDown)
                || physicallyDown(mc.options.keyLeft) || physicallyDown(mc.options.keyRight);
    }

    private void flushClicks() {
        if (heldClicks.isEmpty()) return;
        if (mc.getConnection() == null) {
            heldClicks.clear();
            return;
        }
        flushing = true;
        try {
            heldClicks.forEach(mc.getConnection()::send);
        } finally {
            flushing = false;
            heldClicks.clear();
        }
    }

    private void sync() {
        for (KeyMapping key : movement()) {
            if (toggled(key)) continue;
            key.setDown(physicallyDown(key));
        }
    }

    private void release() {
        walking = false;
        for (KeyMapping key : movement()) {
            if (toggled(key)) continue;
            key.setDown(false);
        }
    }

    @Override
    protected void onDisable() {
        pendingScreen = null;
        flushClicks();
        if (walking && mc.screen != null) release();
        walking = false;
    }

    /** Только инвентарь игрока и клик-гуи; в режиме бинда клик-гуи движение стоит. */
    private boolean allowed(Screen screen) {
        if (screen instanceof InventoryScreen) return true;
        return screen instanceof ClickGuiScreen gui && !gui.isBinding();
    }

    private boolean physicallyDown(KeyMapping key) {
        if (key.isUnbound()) return false;
        InputConstants.Key bound = InputConstants.getKey(key.saveString());
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), bound.getValue()) == GLFW.GLFW_PRESS;
        }
        return bound.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(mc.getWindow(), bound.getValue());
    }

    private boolean toggled(KeyMapping key) {
        if (key == mc.options.keyShift) return mc.options.toggleCrouch().get();
        if (key == mc.options.keySprint) return mc.options.toggleSprint().get();
        return false;
    }

    private KeyMapping[] movement() {
        if (movement == null) {
            movement = new KeyMapping[] {
                    mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight,
                    mc.options.keyJump, mc.options.keyShift, mc.options.keySprint
            };
        }
        return movement;
    }
}
