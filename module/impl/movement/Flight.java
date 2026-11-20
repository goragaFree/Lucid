package dev.lucid.module.impl.movement;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Полёт как в креативе. Пока есть один режим — «Ванилла»: игроку выдаётся право
 * летать ({@code abilities.mayfly}), дальше ваниль сама делает всё как в креативе —
 * двойной пробел включает и выключает полёт, приземление гасит его, спринт удваивает
 * скорость.
 *
 * <p><b>Почему без своей физики.</b> Своё движение по тикам рассинхронизируется с
 * сервером и рассыпается на анти-читах. Здесь физика целиком ванильная, модуль
 * управляет только двумя числами: горизонтальной скоростью ({@code abilities.flyingSpeed}
 * — ваниль применяет её и к вертикали с множителем 3) и вертикальной, которая
 * выставляется отдельно (см. {@link #applyVertical}).</p>
 *
 * <p><b>Скорость XZ.</b> Ваниль каждый тик добавляет {@code flyingSpeed} к движению и
 * уносит дальше 0.91 от пройденного: устоявшийся ход за тик = {@code flyingSpeed / 0.09}
 * блоков. Слайдер в блоках/сек переводится в {@code flyingSpeed} именно через это
 * соотношение, поэтому значение 11.1 ≈ скорость креатива по умолчанию (0.05).
 * Спринт по-прежнему удваивает её, как в ванили.</p>
 *
 * <p><b>Скорость Y.</b> Ваниль на каждый тик полёта добавляет к движению
 * {@code flyingSpeed * 3} по нажатым прыжку/шифту. Чтобы вертикаль не зависела от
 * слайдера XZ, в конце тика вертикальная составляющая движения выставляется с
 * компенсацией этого ванильного слагаемого: в следующий тик игрок входит ровно с той
 * скоростью, которая даёт слайдер «Скорость Y» в блоках/сек. Отпущенные клавиши —
 * чистое зависание на месте.</p>
 *
 * <p><b>Включение из конфига.</b> Модуль может включиться до входа в мир ({@code mc.player == null})
 * — тогда всё применит первый тик в мире, {@link #onEnable} в этом случае только сбрасывает
 * сохранённое состояние.</p>
 */
public class Flight extends Module {

    public static final String VANILLA = "Ванилла";

    /** Горизонтальное трение полёта в travelInAir: ход за тик = flyingSpeed / (1 - FLY_FRICTION). */
    private static final double FLY_FRICTION = 0.91;
    /** Ванильный множитель вертикали: motion.y += flyingSpeed * 3 за тик. */
    private static final double VERTICAL_FACTOR = 3.0;
    /** Тиков в секунде: слайдеры заданы в блоках/сек, движение живёт в блоках/тик. */
    private static final double TICKS_PER_SECOND = 20.0;

    private final SelectSetting mode = new SelectSetting("Режим", VANILLA, VANILLA);
    private final SliderSetting speedXz = new SliderSetting("Скорость XZ", 11.1, 0.5, 50, 0.1);
    private final SliderSetting speedY = new SliderSetting("Скорость Y", 7.5, 0.5, 50, 0.1);

    /** Право на полёт было выдано модулем — при выключении его надо забрать. */
    private boolean changedMayfly;
    /** Полёт игрока на момент включения — выключаем только тот, что включили сами. */
    private boolean flyingAtEnable;
    /** flyingSpeed до включения, {@code -1} — не меняли (вне мира скорость не трогаем). */
    private float savedFlyingSpeed = -1F;

    public Flight() {
        super("Flight", "Creative-like vanilla flight", Category.MOVEMENT);
        // Слайдеры видны в ClickGUI только пока модуль включён. visibleWhen возвращает
        // Setting<T>, а не конкретный тип настройки, поэтому вызываем отдельным
        // оператором, а не цепочкой на инициализаторе поля.
        this.speedXz.visibleWhen(this::isEnabled);
        this.speedY.visibleWhen(this::isEnabled);
        addSettings(mode, speedXz, speedY);
    }

    @Override
    protected void onEnable() {
        this.changedMayfly = false;
        this.flyingAtEnable = false;
        this.savedFlyingSpeed = -1F;

        // Включили до входа в мир (из конфига) — применит первый тик.
        if (mc.player == null) return;

        Abilities abilities = mc.player.getAbilities();
        this.flyingAtEnable = abilities.flying;
        this.applyAbilities(abilities, true);

        // Как ваниль при смене способностей: пакет ServerboundPlayerAbilitiesPacket
        // уходит в LocalPlayer#onUpdateAbilities.
        mc.player.onUpdateAbilities();
    }

    @Override
    protected void onDisable() {
        if (mc.player == null) return;

        Abilities abilities = mc.player.getAbilities();
        if (this.changedMayfly) abilities.mayfly = false;
        if (!this.flyingAtEnable) abilities.flying = false;
        if (this.savedFlyingSpeed >= 0F) abilities.setFlyingSpeed(this.savedFlyingSpeed);
        mc.player.onUpdateAbilities();
    }

    @Override
    public void onTick() {
        Player player = mc.player;
        if (player == null || !mode.is(VANILLA)) return;

        Abilities abilities = player.getAbilities();
        this.applyAbilities(abilities, false);
        if (abilities.flying) this.applyVertical(player, abilities);
    }

    /**
     * Раз в тик даёт право на полёт и ставит скорость: сервер может прислать свои
     * способности и затереть mayfly, а слайдер крутится в живую.
     *
     * @param enterFlight включить {@code flying} сразу — только при включении модуля;
     *                    дальше полётом управляет ваниль (двойной пробел, приземление)
     */
    private void applyAbilities(Abilities abilities, boolean enterFlight) {
        if (mc.player.isSpectator()) return;

        if (!abilities.mayfly && !abilities.instabuild) {
            abilities.mayfly = true;
            this.changedMayfly = true;
        }
        if (this.savedFlyingSpeed < 0F) this.savedFlyingSpeed = abilities.getFlyingSpeed();
        abilities.setFlyingSpeed(this.flyingSpeed());

        if (enterFlight && !abilities.flying) abilities.flying = true;
    }

    /** Слайдер «Скорость XZ» (блоков/сек) → ванильный {@code flyingSpeed}. */
    private float flyingSpeed() {
        double blocksPerTick = this.speedXz.getValue() / TICKS_PER_SECOND;
        return (float) (blocksPerTick * (1.0 - FLY_FRICTION));
    }

    /**
     * Своя вертикаль в конце тика — с компенсацией ванильного слагаемого
     * {@code flyingSpeed * 3}, которое ваниль добавит в начале следующего.
     *
     * <p>Гравитация в компенсации не участвует: ваниль применяет её только к скорости
     * «на следующий тик», а эта точка перезаписывается здесь каждый тик.</p>
     */
    private void applyVertical(Player player, Abilities abilities) {
        // Ваниль двигает по вертикали только когда камера на самом игроке (F5 не трогаем),
        // и в меню буквы — текст, а не «вверх».
        if (mc.getCameraEntity() != player) return;

        double direction = 0.0;
        if (mc.screen == null) {
            direction = (mc.options.keyJump.isDown() ? 1.0 : 0.0)
                    - (mc.options.keyShift.isDown() ? 1.0 : 0.0);
        }

        double vanillaVertical = direction * abilities.getFlyingSpeed() * VERTICAL_FACTOR;
        double targetY = direction * (this.speedY.getValue() / TICKS_PER_SECOND - vanillaVertical);

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, targetY, motion.z);
    }
}
