package dev.lucid.util.inventory;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Тиковый исполнитель отложенного действия с инвентарём по {@link SwapProfile}.
 * Универсальный: действие передаётся как {@link Runnable}, поэтому подходит любому модулю.
 *
 * <p>Модуль обязан вызывать {@link #tickStart()} в START_CLIENT_TICK и {@link #tickEnd()}
 * в END_CLIENT_TICK. Удержание клавиш идёт в START — до {@code LocalPlayer#tick}, тогда
 * ванильный ввод уже видит отпущенные клавиши, а AutoSprint не успевает их вернуть.
 * Клавиши мы не запоминаем: по окончании {@link KeyMapping#setAll()} выставляет их по
 * реальному состоянию клавиатуры, поэтому отпущенная за это время клавиша не залипнет.</p>
 */
public final class SwapSequence {

    private enum Phase { IDLE, PREPARE, DELAY, AFTER }

    private static final Minecraft mc = Minecraft.getInstance();

    /** Горизонтальная скорость (блоков/тик), ниже которой считаем, что игрок стоит. */
    private static final double STILL_SPEED = 0.03;
    private static final double STILL_SPEED_SQ = STILL_SPEED * STILL_SPEED;

    private Phase phase = Phase.IDLE;
    private SwapProfile profile = SwapProfile.INSTANT;
    private Runnable action;
    private LocalPlayer owner;
    private int waited;
    private int remaining;
    private int cooldown;
    private boolean keysHeld;

    /** Идёт последовательность или ещё не прошла пауза между запусками. */
    public boolean isBusy() {
        return phase != Phase.IDLE || cooldown > 0;
    }

    /**
     * Запускает действие по профилю. Возвращает false, если исполнитель занят или мир не готов.
     * Мгновенный профиль выполняет действие прямо в этом вызове.
     */
    public boolean start(Runnable action, SwapProfile profile) {
        if (isBusy() || action == null || !InventoryUtil.isReady()) return false;
        this.profile = profile != null ? profile : SwapProfile.INSTANT;

        if (this.profile.isInstant()) {
            action.run();
            if (this.profile.sendClose()) InventoryUtil.closeContainer();
            cooldown = Math.max(0, this.profile.cooldownTicks());
            return true;
        }

        this.action = action;
        this.owner = mc.player;
        this.waited = 0;
        this.phase = Phase.PREPARE;
        holdKeys();
        return true;
    }

    /** START_CLIENT_TICK: держим клавиши отпущенными, пока последовательность идёт. */
    public void tickStart() {
        if (phase == Phase.IDLE) return;
        if (!stillValid()) {
            cancel();
            return;
        }
        holdKeys();
    }

    /** END_CLIENT_TICK: двигаем последовательность на один тик. */
    public void tickEnd() {
        if (phase == Phase.IDLE) {
            if (cooldown > 0) cooldown--;
            return;
        }
        if (!stillValid()) {
            cancel();
            return;
        }

        switch (phase) {
            case PREPARE -> {
                waited++;
                boolean calm = !profile.waitStandstill() || isStandingStill();
                if (!calm && waited < profile.maxWaitTicks()) return;
                remaining = roll(profile.minDelayTicks(), profile.maxDelayTicks());
                if (remaining <= 0) fire();
                else phase = Phase.DELAY;
            }
            case DELAY -> {
                if (--remaining <= 0) fire();
            }
            case AFTER -> {
                if (--remaining <= 0) finish();
            }
            default -> { }
        }
    }

    /** Прерывает последовательность без выполнения действия и возвращает управление. */
    public void cancel() {
        releaseKeys();
        phase = Phase.IDLE;
        action = null;
        owner = null;
        cooldown = 0;
    }

    private void fire() {
        Runnable run = action;
        if (run != null) run.run();
        remaining = roll(profile.minAfterTicks(), profile.maxAfterTicks());
        if (remaining <= 0) finish();
        else phase = Phase.AFTER;
    }

    private void finish() {
        // Закрытие — в самом конце, как у игрока, который закрывает инвентарь после клика,
        // а не в том же тике, что и клик. Клавиши возвращаем сразу после него.
        if (profile.sendClose()) InventoryUtil.closeContainer();
        releaseKeys();
        phase = Phase.IDLE;
        action = null;
        owner = null;
        cooldown = Math.max(0, profile.cooldownTicks());
    }

    /** Мир сменился или игрока пересоздали — продолжать нельзя, клик ушёл бы в чужое меню. */
    private boolean stillValid() {
        return InventoryUtil.isReady() && mc.player == owner;
    }

    private boolean isStandingStill() {
        Vec3 v = mc.player.getDeltaMovement();
        return v.x * v.x + v.z * v.z < STILL_SPEED_SQ;
    }

    private void holdKeys() {
        if (mc.player == null) return;
        if (profile.stopMovement()) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            keysHeld = true;
        }
        if (profile.stopSprint()) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
            keysHeld = true;
        }
    }

    private void releaseKeys() {
        if (!keysHeld) return;
        keysHeld = false;
        KeyMapping.setAll();
    }

    /** Случайное целое из [a, b]; границы можно задать в любом порядке. */
    private static int roll(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        if (hi <= 0) return 0;
        lo = Math.max(lo, 0);
        return lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }
}