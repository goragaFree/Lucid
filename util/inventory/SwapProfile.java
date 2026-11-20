package dev.lucid.util.inventory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Описание того, как именно выполняется отложенное действие с инвентарём.
 * Все времена в клиентских тиках (1 тик = 50 мс), а не в миллисекундах:
 * действие всегда попадает в одну и ту же точку тика и не зависит от FPS.
 *
 * <p>Порядок: подготовка (держим клавиши, при желании ждём остановки) →
 * пауза до действия → само действие (+ пакет закрытия) → пауза после → возврат управления →
 * пауза между запусками.</p>
 *
 * @param stopMovement   отпускать WASD на время последовательности
 * @param stopSprint     гасить спринт на время последовательности
 * @param waitStandstill перед действием ждать, пока горизонтальная скорость упадёт
 * @param maxWaitTicks   сколько максимум ждать остановки; потом действие идёт всё равно
 * @param minDelayTicks  пауза между «готов» и действием, нижняя граница
 * @param maxDelayTicks  то же, верхняя граница
 * @param minAfterTicks  сколько держать клавиши после действия, нижняя граница
 * @param maxAfterTicks  то же, верхняя граница
 * @param sendClose      отправить пакет закрытия контейнера в конце последовательности,
 *                       после паузы «после действия»
 * @param cooldownTicks  минимальная пауза до следующего запуска
 */
public record SwapProfile(
        boolean stopMovement,
        boolean stopSprint,
        boolean waitStandstill,
        int maxWaitTicks,
        int minDelayTicks,
        int maxDelayTicks,
        int minAfterTicks,
        int maxAfterTicks,
        boolean sendClose,
        int cooldownTicks) {

    /** Без ожиданий: действие выполняется в тот же миг, где его запросили. */
    public static final SwapProfile INSTANT =
            new SwapProfile(false, false, false, 0, 0, 0, 0, 0, false, 1);

    /**
     * Новый «легитный» профиль на каждый свап. Параметры не фиксированные, а выбираются
     * заново из небольших диапазонов, поэтому два свапа подряд не повторяют тайминги:
     * разное ожидание остановки, разная пауза до и после, разная пауза между запусками.
     * Остановка движения, гашение спринта, ожидание остановки и пакет закрытия всегда
     * включены: случайны только тайминги, а не то, что именно делается.
     */
    public static SwapProfile legit() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int delayFrom = r.nextInt(0, 2);                 // 0..1
        int delayTo = delayFrom + r.nextInt(1, 4);       // ещё +1..3
        int afterTo = 1 + r.nextInt(1, 3);               // 2..3
        return new SwapProfile(
                true,
                true,                                    // спринт гасим всегда
                true,                                    // остановку ждём всегда
                r.nextInt(2, 5),                         // ждать не дольше 2..4 тиков
                delayFrom, delayTo,
                1, afterTo,
                true,
                r.nextInt(3, 8));                        // пауза между свапами 3..7 тиков
    }

    /** true — последовательность не нужна, действие запускается немедленно. */
    public boolean isInstant() {
        return !stopMovement && !stopSprint && !waitStandstill
                && maxDelayTicks <= 0 && maxAfterTicks <= 0;
    }
}