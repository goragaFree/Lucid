package dev.lucid.module.impl.combat;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.SliderSetting;
import net.minecraft.util.Util;

/**
 * Сам нажимает ЛКМ и ПКМ с заданным интервалом, пока модуль включён.
 *
 * <p>Клики идут через приватные {@code Minecraft#startAttack}/{@code startUseItem},
 * открытые accesswidener'ом: это ровно тот код, что выполняет ванильный бинд кнопки,
 * со всеми его ветками — удар по сущности, начало ломания блока, взмах рукой, промах
 * с {@code missTime}. Своей имитации пакетов нет, поэтому сервер видит обычные клики,
 * а поведение не расходится с ванилью при обновлении версии.</p>
 *
 * <p>Кнопки независимы: у каждой свой переключатель, свой интервал и свой момент
 * следующего клика. Момент сдвигается после любой попытки клика; единственное
 * исключение — незаряженная атака при включённом Wait Cooldown: здесь попытка
 * повторяется уже на следующем тике, а не через полный интервал молчания.</p>
 *
 * <p>Темп отмеряется миллисекундами по монотонным часам {@link Util#getMillis()},
 * а не тиками: минимальный интервал 0.1 с меньше тика (50 мс), и тиковый счётчик
 * его просто не различил бы.</p>
 */
public class TapeMouse extends Module {

    private final BooleanSetting leftClick = new BooleanSetting("Left Click", true);
    private final SliderSetting leftDelay = new SliderSetting("Left Delay", 5.0, 0.1, 50.0, 0.1);
    private final BooleanSetting rightClick = new BooleanSetting("Right Click", false);
    private final SliderSetting rightDelay = new SliderSetting("Right Delay", 5.0, 0.1, 50.0, 0.1);
    // Еда, лук и щит — тоже «использование предмета»: лишний клик сбросил бы натяжение
    // лука или прервал еду, поэтому по умолчанию на это время клики встают на паузу.
    private final BooleanSetting pauseWhileUsing = new BooleanSetting("Pause While Using", true);
    // Удар по незаряженной шкале наносит копейки урона (0.2 + f²·0.8), поэтому ЛКМ
    // по умолчанию ждёт почти полной перезарядки, как «Cooldown» у TriggerBot.
    private final BooleanSetting waitCooldown = new BooleanSetting("Wait Cooldown", true);

    // 0 = клик разрешён сразу; иначе монотонное время (мс), раньше которого не кликаем.
    private long nextLeftAt;
    private long nextRightAt;

    public TapeMouse() {
        super("TapeMouse", "Automatically presses the mouse buttons on a timer", Category.COMBAT);
        // Слайдер интервала без своей кнопки — мёртвая настройка: прячем её, но в
        // конфиге значение остаётся и вернётся при включении кнопки обратно.
        leftDelay.visibleWhen(() -> leftClick.getValue());
        rightDelay.visibleWhen(() -> rightClick.getValue());
        addSettings(leftClick, leftDelay, rightClick, rightDelay, pauseWhileUsing, waitCooldown);
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.gameMode == null) return;
        // Любой открытый экран: клики нужны только в мире, в GUI они ничего не жмут,
        // а startAttack всё равно не сработает — только зря сдвинет таймеры.
        if (mc.screen != null) return;
        if (pauseWhileUsing.getValue() && mc.player.isUsingItem()) return;

        long now = Util.getMillis();
        if (leftClick.getValue() && now >= nextLeftAt && clickLeft()) {
            nextLeftAt = now + delayMillis(leftDelay);
        }
        if (rightClick.getValue() && now >= nextRightAt && clickRight()) {
            nextRightAt = now + delayMillis(rightDelay);
        }
    }

    /**
     * Один ванильный клик ЛКМ.
     *
     * @return false только если атака не перезарядилась — тогда повторим на следующем
     *         тике. Возврат самого {@code startAttack} нарочно игнорируется: на промахе
     *         ваниль отвечает false и ставит {@code missTime}, а в креативе не делает
     *         даже этого — считать такую попытку «несостоявшейся» зацикливало бы
     *         повторы каждый тик. Попытка клика уже клик, пауза идёт от неё.
     */
    private boolean clickLeft() {
        // Тот же порог 0.9, что зашит у TriggerBot: ниже него удар уже почти полный,
        // а выше 1.0 шкала не бывает, поэтому это не отрезает «полный» удар.
        if (waitCooldown.getValue() && mc.player.getAttackStrengthScale(0.5F) < 0.9F) {
            return false;
        }
        mc.startAttack();
        return true;
    }

    /** ПКМ без гейтов: startUseItem сам разберёт руки, блоки и предметы. */
    private boolean clickRight() {
        mc.startUseItem();
        return true;
    }

    private static long delayMillis(SliderSetting delay) {
        return (long) (delay.getValue() * 1000.0);
    }

    @Override
    protected void onDisable() {
        // При следующем включении первый клик должен пройти сразу, без остатка паузы.
        nextLeftAt = 0;
        nextRightAt = 0;
    }
}
