package dev.lucid.module.impl.combat;

import dev.lucid.event.Priority;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.players.friend.FriendManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

/**
 * Бьёт сущность под прицелом, когда удар перезарядился и прошла пауза между ударами.
 *
 * <p>Прицел и дальность не свои, а ванильные: {@code hitResult} уже отфильтрован по
 * entityInteractionRange, поэтому увеличенного reach здесь нет и серверу нечего отклонять.
 * Темп — из {@code ru.isle.recovered.TriggerBot}: после любого удара, крита в том числе,
 * 10 тиков паузы (0.5 с), плюс готовность ванильного кулдауна не ниже порога «Cooldown».
 * CPS-лимита и случайного разброса нет: пауза фиксированная, попытка каждый тик.</p>
 *
 * <p>Крит в ванили возможен только на нисходящей части прыжка и только без спринта:
 * на взлёте {@code fallDistance} ещё нулевой. Первый тик падения пропускаем — сервер может
 * на один тик отставать и считать игрока всё ещё на земле. Сброс спринта запрашивается
 * заранее: {@code setSprinting(false)} сам пакет не шлёт, его отправляет LocalPlayer в
 * своём тике, а атака идёт в END_CLIENT_TICK, то есть строго после STOP_SPRINTING.</p>
 *
 * <p>Удержание спринта вынесено в START_CLIENT_TICK: он до {@code LocalPlayer#tick},
 * поэтому AutoSprint не успевает вернуть {@code keySprint} и сорвать стоп. Своих миксинов
 * и событий спринта для этого не нужно — хватает двух фаз уже существующего тика.</p>
 *
 * <p>Smart Crits в серии прыжков обычным не бьёт: пауза в 10 тиков дрейфует относительно
 * прыжка (~12 тиков), и удар по таймеру попадает то в падение, то во взлёт или приземление.
 * Пока игрок в воздухе или только что коснулся земли, ждём подтверждённого падения и бьём
 * критом; обычные удары остаются на земле, когда прыжки прекратились.</p>
 *
 * <p>{@code Naked} и {@code Friends} в {@code Targets} — галочки-разрешения для игроков:
 * снятая галочка удар запрещает. Друг проверяется до {@code Players} как отдельная
 * категория (так же устроено в ESP и в {@code TargetFinder} референса): без {@code Friends}
 * триггер на него не реагирует, даже когда {@code Players} включён. Без {@code Naked}
 * не цель игрок, у которого все четыре слота брони пусты. Обе проверки только для
 * {@code Player}: у зверей и мобов брони обычно нет, и общий фильтр молча выключил бы
 * целые категории {@code Animals} и {@code Mobs}.</p>
 */
public class TriggerBot extends Module {

    private static final String PLAYERS = "Players";
    private static final String ANIMALS = "Animals";
    private static final String MOBS = "Mobs";
    // Уточнения именно для игроков, а не новые типы сущностей: снятая галочка
    // только отсекает цель, поэтому звери и мобы без брони остаются целями.
    private static final String NAKED = "Naked";
    private static final String FRIENDS = "Friends";

    // «Голый» = все четыре слота брони пусты. hasItemInSlot в ванили — ровно
    // !getItemBySlot(slot).isEmpty(), поэтому элитра или тыква тоже считаются одетым.
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final String VANILLA = "Vanilla";
    private static final String LEGIT = "Legit";
    private static final String PACKET = "Packet";

    // «Не атаковать если». В референсе это BooleanGroupSetting, у нас такого типа нет —
    // MultiSelectSetting даёт те же независимые галочки и тот же конфиг по именам.
    private static final String USING_ITEM = "Using Item";
    private static final String USING_SHIELD = "Using Shield";
    private static final String BREAKING_BLOCK = "Breaking Block";
    private static final String NO_ITEM = "No Item";
    private static final String FLYING_ELYTRA = "Flying Elytra";
    private static final String CONTAINER_OPEN = "Container Open";

    // Legit не дёргает спринт сразу после удара: 2 тика, как sprintResetTicks = 2
    // в референсе. Со стороны это читается как «отпустил бег в прыжке», а не стоп-старт.
    private static final int LEGIT_HOLD_TICKS = 2;

    // Пауза между ударами из референса: field1633 = 10 после каждого удара, 0.5 с при
    // 20 TPS. Одна на все удары — крит её не обходит, иначе сразу после крита шёл бы
    // второй удар на готовом ванильном кулдауне (это уже ловили 13.11).
    private static final int STRIKE_COOLDOWN_TICKS = 10;

    // Окно «серии прыжков»: после приземления ещё 4 тика (0.2 с) считаем, что игрок
    // продолжает прыгать. При зажатом пробеле новый прыжок начинается через 1 тик,
    // поэтому обычный удар не проскакивает в промежутке между двумя критами.
    private static final int JUMP_GRACE_TICKS = 4;

    // Новые галочки включены по умолчанию, чтобы свежий конфиг не менял поведение:
    // фильтры включаются снятием галочки. В уже сохранённом конфиге их в списке нет —
    // после загрузки они останутся выключенными, то есть фильтры сразу работают.
    private final MultiSelectSetting targets = new MultiSelectSetting("Targets",
            List.of(PLAYERS, ANIMALS, MOBS, NAKED, FRIENDS), PLAYERS, ANIMALS, MOBS, NAKED, FRIENDS);
    // «Мин кулдаун атаки» в референсе: сколько готовности ванильного кулдауна нужно
    // перед ударом. 0.9 по умолчанию — ровно тот порог, что был у нас зашит в коде.
    private final SliderSetting cooldown = new SliderSetting("Cooldown", 0.9, 0.5, 1.0, 0.01);
    private final BooleanSetting onlyCrits = new BooleanSetting("Only Crits", false);
    private final BooleanSetting smartCrits = new BooleanSetting("Smart Crits", false);
    private final SelectSetting sprintReset = new SelectSetting("Sprint Reset",
            VANILLA, VANILLA, LEGIT, PACKET);
    // Раньше любой открытый экран блокировал удар жёстко, без настройки. Теперь это
    // галочка, включённая по умолчанию: поведение не меняется, но её можно снять.
    private final MultiSelectSetting dontAttackIf = new MultiSelectSetting("Don't Attack If",
            List.of(CONTAINER_OPEN),
            USING_ITEM, USING_SHIELD, BREAKING_BLOCK, NO_ITEM, FLYING_ELYTRA, CONTAINER_OPEN);

    // Сколько тиков подряд сошлись условия крита; удар ждёт второго тика.
    // Сбрасывается, только когда условия перестали сходиться или цель пропала, —
    // после самого удара падение продолжается и подтверждать его заново нечего.
    private int critTicks;
    // >0 = игрок всё ещё в серии прыжков; обычные удары в этом окне запрещены.
    private int jumpGraceTicks;
    // Сброс запрошен, но STOP_SPRINTING ещё не ушёл.
    private boolean sprintResetPending;
    // Каким был keySprint до нашего вмешательства — возвращаем ровно его, а не «всегда true».
    private boolean sprintKeyDownBefore;
    // -1 = возврат не запланирован, иначе тики до возврата keySprint.
    private int restoreDelay = -1;
    // >0 = удар ещё не разрешён; тикает один раз за клиентский тик в END.
    private int strikeCooldownTicks;

    public TriggerBot() {
        super("TriggerBot", "Attacks the entity under the crosshair", Category.COMBAT);
        // Скрытая настройка остаётся в конфиге: выключили Only Crits — включили обратно,
        // Smart Crits вернулся каким был.
        smartCrits.visibleWhen(() -> onlyCrits.getValue());
        addSettings(targets, cooldown, onlyCrits, smartCrits, sprintReset, dontAttackIf);
    }

    /**
     * LOWEST: AutoSprint ставит keySprint в END обычным приоритетом, и гасить его нужно
     * уже после него — иначе он вернёт зажатую клавишу в том же тике и пакет не уйдёт.
     */
    @Subscribe(priority = Priority.LOWEST)
    private void onClientTick(ClientTickEvent event) {
        if (event.getPhase() == ClientTickEvent.Phase.START) {
            holdSprintBeforePlayerTick();
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            // Мир пропал — тиков больше не будет, паузу Legit доводить некому.
            if (mc.player == null && restoreDelay >= 0) restoreSprintNow();
            // Паузу из старого мира не переносим: тиков там больше не будет.
            strikeCooldownTicks = 0;
            jumpGraceTicks = 0;
            resetState();
            return;
        }

        // Считаем до всех гейтов: серия прыжков не должна сбиваться, когда цель
        // на тик пропала из прицела или открылся экран.
        if (!mc.player.onGround()) {
            jumpGraceTicks = JUMP_GRACE_TICKS;
        } else if (jumpGraceTicks > 0) {
            jumpGraceTicks--;
        }

        tickSprintRestore();
        // Декремент до всех гейтов: пауза идёт и без цели под прицелом, как
        // field1633 в PlayerTickEvent референса.
        if (strikeCooldownTicks > 0) strikeCooldownTicks--;

        if (mc.player.isSpectator() || mc.player.isHandsBusy() || shouldSkipAttack()) {
            resetState();
            return;
        }
        if (!(mc.hitResult instanceof EntityHitResult hit) || !isTargetAllowed(hit.getEntity())) {
            resetState();
            return;
        }

        Entity target = hit.getEntity();
        boolean strikeCrit = false;
        if (canCritSoon(target)) {
            critTicks++;
            strikeCrit = critTicks >= 2;
        } else {
            critTicks = 0;
        }

        boolean only = onlyCrits.getValue();
        boolean smart = smartCrits.getValue();
        boolean crit = only && strikeCrit;
        // Без Smart Crits молчим везде, кроме подтверждённого падения. Со Smart Crits
        // обычные удары остаются на земле, но в серии прыжков их не шлём: удар по
        // таймеру дрейфует относительно апекса и иначе срывает крит на взлёте.
        boolean waitingForCrit = only && smart && !crit && shouldWaitForCrit();
        if ((only && !smart && !crit) || waitingForCrit) {
            cancelSprintReset();
            return;
        }
        // Пауза из референса. Стоит после подсчёта critTicks, чтобы подтверждение
        // падения копилось и во время паузы: 10 тиков хватает с запасом на 2 тика.
        if (strikeCooldownTicks > 0) {
            cancelSprintReset();
            return;
        }
        // Второй гейт — готовность ванильного кулдауна (20 / ATTACK_SPEED 4.0 ≈ 5 тиков).
        // Сравнение строгое: при пороге 1.0 полная шкала должна удар пропустить, а
        // «<= 1.0» запретила бы его совсем. С паузой в 10 тиков шкала почти всегда
        // уже полная, но гейт задаёт минимум силы и работает на первом ударе.
        if (mc.player.getAttackStrengthScale(0.5F) < cooldown.getValue()) {
            cancelSprintReset();
            return;
        }
        // Сервер этот удар всё равно отклонит: цель ещё в иммунитете после предыдущего.
        // Запрошенный сброс спринта не отменяем — через тик он понадобится тому же криту.
        if (isTargetDamageCoolingDown(target)) return;
        // Сюда без крита доходят только без Only Crits или уже на земле: сброс не нужен.
        if (!crit) cancelSprintReset();

        if (crit && mc.player.isSprinting() && !sprintResetPending) {
            requestSprintReset();
            sprintResetPending = true;
            // Packet отправляет STOP_SPRINTING сам и сразу, ждать нечего. Остальные
            // режимы ждут LocalPlayer#tick следующего тика, иначе сервер увидит спринт.
            if (!sprintReset.is(PACKET)) return;
        }

        attack(target, crit);
    }

    /**
     * Гасим спринт до того, как {@code LocalPlayer#tick} успеет его вернуть.
     *
     * <p>Проверка в END опаздывает: AutoSprint ставит {@code keySprint} в прошлом END,
     * и в следующем {@code aiStep()} видит зажатую клавишу раньше, чем мы снова её снимем.
     * START_CLIENT_TICK — ровно перед тиком игрока, поэтому здесь стоп достоверен и
     * {@code sendIsSprintingIfNeeded()} честно отправляет STOP_SPRINTING. Дополнительного
     * миксина в LocalPlayer и отдельного SprintEvent для этого не заводим.</p>
     */
    private void holdSprintBeforePlayerTick() {
        if (mc.player == null) return;
        if (!sprintResetPending && restoreDelay < 0) return;
        mc.options.keySprint.setDown(false);
        mc.player.setSprinting(false);
    }

    /**
     * Условия «не атаковать если». Проверяются до поиска цели, чтобы впустую не копить
     * critTicks и не запрашивать сброс спринта, который всё равно отменится.
     *
     * <p>В 1.21.11 нет {@code Gui#screen()} из новых версий: любой открытый экран лежит
     * в {@code Minecraft#screen}, поэтому «Container Open» ловит и чат, и инвентарь, и
     * ClickGUI — как раньше ловила жёсткая проверка без настройки.</p>
     */
    private boolean shouldSkipAttack() {
        if (dontAttackIf.isSelected(USING_ITEM) && mc.player.isUsingItem()) return true;
        if (dontAttackIf.isSelected(USING_SHIELD) && mc.player.isBlocking()) return true;
        if (dontAttackIf.isSelected(BREAKING_BLOCK) && mc.gameMode.isDestroying()) return true;
        if (dontAttackIf.isSelected(NO_ITEM) && mc.player.getMainHandItem().isEmpty()) return true;
        if (dontAttackIf.isSelected(FLYING_ELYTRA) && mc.player.isFallFlying()) return true;
        return dontAttackIf.isSelected(CONTAINER_OPEN) && mc.screen != null;
    }

    /** Повторяет ванильный порядок: пакет атаки и локальный урон, затем свинг. */
    private void attack(Entity target, boolean crit) {
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        // Любой удар, не только обычный: крит вне паузы давал двойной удар через 0.25 с.
        strikeCooldownTicks = STRIKE_COOLDOWN_TICKS;
        scheduleRestore();
        // critTicks не сбрасываем: падение после крита продолжается, и лишние 2 тика
        // «переподтверждения» открывали бы окно, где крит считается неподтверждённым.
    }

    /**
     * Условия {@code Player#canCriticalAttack}, кроме спринта: его снимаем сами.
     * fallDistance здесь double и растёт только на нисходящей части прыжка.
     */
    private boolean canCritSoon(Entity target) {
        return target instanceof LivingEntity
                && mc.player.fallDistance > 0
                && !mc.player.onGround()
                && critAllowedAtAll();
    }

    /**
     * Постоянные запреты крита из ванили — те, что не зависят от фазы прыжка.
     * Вынесены отдельно, чтобы одинаково отвечать на «можно ли ударить критом» и
     * «стоит ли вообще ждать крита»: в воде или на лестнице ждать нечего.
     */
    private boolean critAllowedAtAll() {
        // Под водой, в лаве и при плавании критического удара всё равно не будет, а
        // STOP_SPRINTING от нас стал бы лишним пакетом «ни о чём».
        if (mc.player.onClimbable() || mc.player.isInWater()) return false;
        if (mc.player.isUnderWater() || mc.player.isInLava() || mc.player.isSwimming()) return false;
        return !mc.player.hasEffect(MobEffects.BLINDNESS) && !mc.player.isPassenger();
    }

    /**
     * Цель ещё не вышла из ванильного иммунитета после урона — сервер удар отклонит.
     *
     * <p>{@code LivingEntity#hurtServer} после урона ставит {@code invulnerableTime = 20}
     * ({@code INVULNERABLE_DURATION}) и, пока счётчик больше 10, отбрасывает повторный
     * урон не сильнее предыдущего. Наша пауза ровно 10 тиков приходит на эту границу:
     * если серверный тик не успел декрементировать счётчик до обработки нашего пакета,
     * атака уходит впустую — свинг есть, урона нет. На integrated server сдвиг между
     * клиентским и серверным тиком плавает на 1, поэтому промах редкий и нерегулярный.</p>
     *
     * <p>Серверного поля с клиента не видно, но {@code ClientboundDamageEventPacket}
     * ставит цели {@code hurtTime = 10} из того же урона, а {@code baseTick()} уменьшает
     * его каждый тик. Пока {@code hurtTime > 0}, бить рано: ждём тик. Константу темпа
     * не трогаем — удар сдвигается только когда сервер ещё не готов принять урон.</p>
     */
    private boolean isTargetDamageCoolingDown(Entity target) {
        return target instanceof LivingEntity living && living.hurtTime > 0;
    }

    /**
     * Стоит ли Smart Crits промолчать вместо обычного удара.
     *
     * <p>В воздухе крит ещё впереди: на взлёте {@code fallDistance} нулевой, но апекс
     * близко. Окно после приземления нужно для серии прыжков — при зажатом пробеле игрок
     * касается земли всего 1 тик, и удар по таймеру иначе проскакивал бы обычным между
     * двумя критами. Где крит невозможен в принципе, не ждём и бьём обычным.</p>
     */
    private boolean shouldWaitForCrit() {
        return (!mc.player.onGround() || jumpGraceTicks > 0) && critAllowedAtAll();
    }

    /**
     * ArmorStand и прочие живые, не Mob и не Player, не попадают ни в одну категорию.
     *
     * <p>Имя друга берём {@code getPlainTextName()} и сравниваем точным {@code equals},
     * как в {@code NoFriendDamage} и {@code ClickFriend}: регистр в {@code FriendManager}
     * значимый. Голым считаем только игрока — см. пояснение в шапке класса.</p>
     */
    private boolean isTargetAllowed(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        // Свой приручённый волк под прицелом — не цель, что бы ни было отмечено.
        if (entity instanceof TamableAnimal tame && tame.isOwnedBy(mc.player)) return false;
        if (entity instanceof Player player) {
            String category = FriendManager.contains(player.getPlainTextName()) ? FRIENDS : PLAYERS;
            if (!targets.isSelected(category)) return false;
            // Нужен хотя бы один непустой слот брони: иначе голый, а галочка снята.
            return targets.isSelected(NAKED) || hasAnyArmor(player);
        }
        if (entity instanceof Animal) return targets.isSelected(ANIMALS);
        if (entity instanceof Mob) return targets.isSelected(MOBS);
        return false;
    }

    /** Достаточно одного предмета в четырёх слотах брони — «как минимум 1 элемент». */
    private static boolean hasAnyArmor(LivingEntity living) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (living.hasItemInSlot(slot)) return true;
        }
        return false;
    }

    private void requestSprintReset() {
        if (sprintReset.is(PACKET)) {
            // Не трогаем локальный isSprinting(): сервер считает, что бег остановлен,
            // клиент продолжает бежать. Клиентских частиц крита в этом режиме не будет —
            // урон критом посчитает сервер.
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) {
                connection.send(new ServerboundPlayerCommandPacket(mc.player,
                        ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            }
            return;
        }
        sprintKeyDownBefore = mc.options.keySprint.isDown();
        mc.options.keySprint.setDown(false);
        // setSprinting сам пакет не шлёт: STOP_SPRINTING отправит sendIsSprintingIfNeeded
        // в LocalPlayer#tick следующего тика, когда aiStep уже не вернёт спринт обратно.
        mc.player.setSprinting(false);
    }

    /**
     * Удар критом не состоялся — гасить спринт было незачем. Без этого флаг залипал бы
     * и следующий крит ушёл без STOP_SPRINTING, то есть обычным уроном.
     */
    private void cancelSprintReset() {
        if (!sprintResetPending) return;
        if (!sprintReset.is(PACKET)) mc.options.keySprint.setDown(sprintKeyDownBefore);
        sprintResetPending = false;
    }

    /** После удара возвращаем клавишу не сразу: Legit держит паузу, Vanilla — один тик. */
    private void scheduleRestore() {
        if (!sprintResetPending) return;
        if (!sprintReset.is(PACKET)) {
            restoreDelay = sprintReset.is(LEGIT) ? LEGIT_HOLD_TICKS : 0;
        }
        sprintResetPending = false;
    }

    private void tickSprintRestore() {
        if (restoreDelay < 0) return;
        if (restoreDelay > 0) {
            restoreDelay--;
            return;
        }
        mc.options.keySprint.setDown(sprintKeyDownBefore);
        restoreDelay = -1;
    }

    /**
     * Удара не было — держать запрошенный сброс больше незачем. Запланированный после
     * удара возврат (Legit) не трогаем: пауза должна отработать до конца.
     */
    private void resetState() {
        cancelSprintReset();
        critTicks = 0;
    }

    private void restoreSprintNow() {
        if (!sprintReset.is(PACKET)) mc.options.keySprint.setDown(sprintKeyDownBefore);
        restoreDelay = -1;
    }

    @Override
    protected void onDisable() {
        // Дальше тиков не будет, поэтому возвращаем клавишу здесь, а не планируем.
        cancelSprintReset();
        if (restoreDelay >= 0) restoreSprintNow();
        critTicks = 0;
        jumpGraceTicks = 0;
        // При следующем включении первый удар должен быть сразу, без остатка паузы.
        strikeCooldownTicks = 0;
    }
}
