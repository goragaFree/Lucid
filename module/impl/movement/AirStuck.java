package dev.lucid.module.impl.movement;

import dev.lucid.Lucid;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.SliderSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Держит игрока на одной позиции, пока включён: исходящие пакеты движения режутся
 * в {@code ClientCommonPacketListenerImpl#send}, а локальная физика каждый тик
 * возвращается к {@link #savedPos}.
 *
 * <p>Скорость, которую сервер видел последней, запоминается при включении и
 * возвращается при выключении — иначе после заморозки игрок продолжил бы с нулевой
 * скоростью. Если выход инициирован ударом, откатом или сменой мира, скорость не
 * трогаем: её уже перезаписала ваниль, и наше вмешательство было бы лишним.</p>
 */
public final class AirStuck extends Module {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final BooleanSetting onlyOnGround = new BooleanSetting("Только на земле", false);
    private final BooleanSetting restoreMotion = new BooleanSetting("Вернуть скорость при выкл.", true);
    private final BooleanSetting disableOnDamage = new BooleanSetting("Выкл. при уроне", true);
    private final BooleanSetting disableOnSetback = new BooleanSetting("Выкл. при откате сервера", true);
    private final BooleanSetting disableOnVehicle = new BooleanSetting("Выкл. на транспорте", true);
    private final BooleanSetting autoDisable = new BooleanSetting("Авто-выключение", true);
    private final SliderSetting time = new SliderSetting("Время (сек)", 3, 1, 10, 1);

    private Vec3 savedPos;
    private Vec3 savedMotion;
    private LocalPlayer frozenPlayer;
    private long enabledAt;
    private boolean hurtSeen;
    private boolean skipRestore;
    private boolean disableRequested;

    public AirStuck() {
        super("AirStuck", "Freezes you in the air", Category.MOVEMENT);
        time.visibleWhen(() -> autoDisable.getValue());
        addSettings(onlyOnGround, restoreMotion, disableOnDamage, disableOnSetback,
                disableOnVehicle, autoDisable, time);
    }

    private static AirStuck instance() {
        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return null;
        return lucid.getModuleManager().get(AirStuck.class);
    }

    /** Читается миксином только для пакетов движения. true = отменить пакет. */
    public static boolean shouldBlockPackets() {
        AirStuck m = instance();
        return m != null && m.isEnabled();
    }

    /** Вызывается миксином в HEAD обработки телепорта от сервера. */
    public static void onServerSetback() {
        AirStuck m = instance();
        if (m == null || !m.isEnabled() || !m.disableOnSetback.getValue()) return;
        m.skipRestore = true; // скорость сейчас перезапишет пакет сервера
        m.setEnabled(false);
    }

    @Override
    protected void onEnable() {
        skipRestore = false;
        disableRequested = false;
        enabledAt = System.nanoTime();
        frozenPlayer = mc.player;
        hurtSeen = frozenPlayer != null && frozenPlayer.hurtTime > 0;
        if (frozenPlayer == null) {
            savedPos = null;
            savedMotion = null;
            return;
        }
        savedPos = frozenPlayer.position();
        savedMotion = frozenPlayer.getDeltaMovement();
        // onGround() подтверждён байткодом 1.21.11 (Entity#onGround, не поле onGround).
        // Выключать прямо здесь нельзя: Module.setEnabled подписывает модуль на EventBus
        // уже ПОСЛЕ onEnable(), и подписка осталась бы висеть у выключенного модуля.
        // Поэтому только помечаем, а setEnabled(false) делаем в первом же тике.
        if (onlyOnGround.getValue() && !frozenPlayer.onGround()) {
            disableRequested = true;
            skipRestore = true; // скорость ещё не меняли, возвращать нечего
        }
    }

    @Override
    protected void onDisable() {
        // Возвращаем скорость, которую сервер видел последней, чтобы физика продолжилась без скачка
        if (restoreMotion.getValue() && !skipRestore
                && mc.player != null && mc.player == frozenPlayer && savedMotion != null) {
            mc.player.setDeltaMovement(savedMotion);
        }
        savedPos = null;
        savedMotion = null;
        frozenPlayer = null;
        enabledAt = 0;
        hurtSeen = false;
        skipRestore = false;
        disableRequested = false;
    }

    @Subscribe
    private void onClientTick(ClientTickEvent event) {
        if (event.getPhase() != ClientTickEvent.Phase.END) return;
        // Слушатель может остаться в шине после выключения (см. onEnable): без этой
        // проверки выключенный модуль продолжал бы обнулять скорость каждый тик.
        if (!isEnabled()) return;
        if (disableRequested) {
            setEnabled(false);
            return;
        }

        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            skipRestore = true;
            setEnabled(false);
            return;
        }

        // Новый LocalPlayer (респавн, смена мира): продолжать нельзя
        if (mc.player != frozenPlayer) {
            skipRestore = true;
            setEnabled(false);
            return;
        }

        if (disableOnVehicle.getValue() && mc.player.isPassenger()) {
            setEnabled(false);
            return;
        }

        // Реагируем на новый удар, а не на старую красную вспышку
        boolean hurt = mc.player.hurtTime > 0;
        if (disableOnDamage.getValue() && hurt && !hurtSeen) {
            skipRestore = true; // откидывание уже изменило скорость
            setEnabled(false);
            return;
        }
        hurtSeen = hurt;

        if (autoDisable.getValue()
                && System.nanoTime() - enabledAt >= time.getLong() * NANOS_PER_SECOND) {
            setEnabled(false);
            return;
        }

        mc.player.setDeltaMovement(Vec3.ZERO);
        if (savedPos != null) {
            mc.player.setPos(savedPos);
            // ПРОВЕРИТЬ: resetFallDistance() в этой редакции убран — если ваниль
            // успевает сдвинуть игрока вниз внутри тика, fallDistance может копить
            // высоту падения и однажды нанести урон. Глянуть на живой проверке.
        }
    }
}
