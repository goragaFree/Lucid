package dev.lucid.module.impl.combat;

import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BindSetting;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.inventory.InventoryUtil;
import dev.lucid.util.inventory.SwapProfile;
import dev.lucid.util.inventory.SwapSequence;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * По нажатию кнопки меняет предмет во второй руке между двумя выбранными.
 *
 * <p>Логика двойного режима: если во второй руке первый предмет — берём второй;
 * если второй — берём первый; если там что-то третье или пусто — сначала ищем первый.
 * Если выбраны два предмета одного типа, берётся первый найденный в инвентаре (хотбар слева
 * направо, потом основной инвентарь) и меняется с рукой; сама рука при поиске не учитывается.
 * Если нужного предмета в инвентаре нет — ничего не делаем, только пишем в экшн-бар
 * (и клавиши при этом не трогаем: проверка наличия идёт до запуска последовательности).</p>
 *
 * <p>«Игнорировать обычные тотемы»: на серверах с кастомными тотемами обычный
 * {@code minecraft:totem_of_undying} без изменений не считается тотемом для свапа —
 * ни в руке, ни в инвентаре. Особым считается тотем, у которого имя, лор, модель или
 * custom_data отличаются от дефолтных. Работает только для пунктов «Тотем».</p>
 *
 * <p>Режимы задержки: «Легит» — {@link SwapProfile#legit()}, профиль со случайными
 * параметрами на каждый свап; «Кастом» — профиль из настроек. Порядок в Кастоме:
 * остановка → пауза до свапа → свап → пауза после → пакет закрытия инвентаря (всегда). Все паузы в
 * тиках (1 тик = 50 мс); нули и выключенная остановка дают мгновенный клик. Слот предмета
 * ищется заново в момент клика: за паузу инвентарь мог измениться.</p>
 */
public final class AutoSwap extends Module {

    private static final String DOUBLE = "Двойной";
    private static final String TRIPLE = "Тройной";

    private static final String LEGIT = "Легит";
    private static final String CUSTOM = "Кастом";

    // Пункты «Остановить» в Кастоме.
    private static final String STOP_MOVE = "Движение";
    private static final String STOP_SPRINT = "Спринт";
    private static final String WAIT_STILL = "Ждать полной остановки";

    /** Предметы, доступные в селектах именно этого модуля. Метка = текст в настройке. */
    private enum SwapItem {
        SPHERE("Сфера", Items.PLAYER_HEAD),
        TOTEM("Тотем", Items.TOTEM_OF_UNDYING),
        GOLDEN_APPLE("Золотое яблоко", Items.GOLDEN_APPLE),
        SHIELD("Щит", Items.SHIELD);

        final String label;
        final Item item;

        SwapItem(String label, Item item) {
            this.label = label;
            this.item = item;
        }

        static String[] labels() {
            SwapItem[] all = values();
            String[] result = new String[all.length];
            for (int i = 0; i < all.length; i++) result[i] = all[i].label;
            return result;
        }
    }

    private final BindSetting swapBind = new BindSetting("Кнопка перемещения");
    private final SelectSetting mode = new SelectSetting("Режим перемещения", DOUBLE, DOUBLE, TRIPLE);
    private final SelectSetting firstItem = new SelectSetting("Первый предмет",
            SwapItem.TOTEM.label, SwapItem.labels());
    private final SelectSetting secondItem = new SelectSetting("Второй предмет",
            SwapItem.SPHERE.label, SwapItem.labels());

    // Для серверов с особыми тотемами: обычные (без изменений) в свапе не участвуют.
    private final BooleanSetting ignorePlainTotems =
            new BooleanSetting("Игнорировать обычные тотемы", false);

    private final SelectSetting delayMode = new SelectSetting("Режим задержки",
            LEGIT, LEGIT, CUSTOM);

    // ---- Кастом. Паузы в тиках: 1 тик = 50 мс. ----

    // 1) Что остановить на время свапа. «Ждать полной остановки» имеет смысл только
    //    вместе с «Движение»: без остановки скорость сама не упадёт.
    private final MultiSelectSetting stopWhat = new MultiSelectSetting("Остановить",
            List.of(STOP_MOVE, STOP_SPRINT, WAIT_STILL), STOP_MOVE, STOP_SPRINT, WAIT_STILL);
    private final SliderSetting maxWait = new SliderSetting("Ждать остановку не дольше", 3, 1, 10, 1);

    // 2) Пауза между «готов» и самим свапом (случайно от..до).
    private final SliderSetting beforeMin = new SliderSetting("Пауза до свапа: от", 0, 0, 10, 1);
    private final SliderSetting beforeMax = new SliderSetting("Пауза до свапа: до", 2, 0, 10, 1);

    // 3) Сколько держать остановку после свапа (случайно от..до).
    private final SliderSetting afterMin = new SliderSetting("Пауза после свапа: от", 1, 0, 10, 1);
    private final SliderSetting afterMax = new SliderSetting("Пауза после свапа: до", 2, 0, 10, 1);

    // 4) Темп. Пакет закрытия инвентаря шлётся всегда: в ванили он уходит при любом закрытии.
    private final SliderSetting betweenSwaps = new SliderSetting("Пауза между свапами", 4, 0, 20, 1);

    private final SwapSequence sequence = new SwapSequence();
    private boolean wasDown;

    public AutoSwap() {
        super("Auto Swap", "Swaps the offhand item between two chosen items", Category.COMBAT);

        // Опция имеет смысл только если хоть один из предметов — тотем.
        ignorePlainTotems.visibleWhen(() -> firstItem.is(SwapItem.TOTEM.label)
                || secondItem.is(SwapItem.TOTEM.label));

        stopWhat.visibleWhen(this::isCustom);
        maxWait.visibleWhen(() -> isCustom() && stopWhat.isSelected(STOP_MOVE)
                && stopWhat.isSelected(WAIT_STILL));
        beforeMin.visibleWhen(this::isCustom);
        beforeMax.visibleWhen(this::isCustom);
        afterMin.visibleWhen(this::isCustom);
        afterMax.visibleWhen(this::isCustom);
        betweenSwaps.visibleWhen(this::isCustom);

        addSettings(swapBind, mode, firstItem, secondItem, ignorePlainTotems, delayMode,
                stopWhat, maxWait, beforeMin, beforeMax, afterMin, afterMax,
                betweenSwaps);
    }

    private boolean isCustom() {
        return delayMode.is(CUSTOM);
    }

    private boolean ignorePlain() {
        return ignorePlainTotems.getValue();
    }

    @Subscribe
    private void onClientTick(ClientTickEvent event) {
        if (!isEnabled()) return;

        // START — до LocalPlayer#tick: здесь удерживаются клавиши последовательности.
        if (event.getPhase() == ClientTickEvent.Phase.START) {
            sequence.tickStart();
            return;
        }

        if (!InventoryUtil.isReady()) {
            sequence.cancel();
            wasDown = false;
            return;
        }

        // Последовательность идёт и при открытом экране: иначе клавиши зависли бы.
        sequence.tickEnd();

        // Экран открыт — кнопку не читаем, а состояние сбрасываем: иначе клавиша,
        // зажатая в чате, сработала бы сразу после его закрытия.
        if (mc.screen != null) {
            wasDown = false;
            return;
        }

        boolean down = swapBind.isDown(mc.getWindow().handle());
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (!pressed) return;

        if (mode.is(TRIPLE)) {
            // Заглушка: тройной свап пока не реализован.
            notifyPlayer("Тройной свап ещё не реализован");
            return;
        }
        requestSwap();
    }

    /** Проверяет, что менять есть на что, и только потом запускает последовательность. */
    private void requestSwap() {
        if (sequence.isBusy()) return;

        Item first = resolve(firstItem);
        Item second = resolve(secondItem);
        if (first == null || second == null) return;

        Item wanted = pickWanted(first, second);
        if (InventoryUtil.findSlot(filterFor(wanted)) == -1) {
            notifyPlayer(notFoundMessage(wanted));
            return;
        }
        sequence.start(this::performSwap, buildProfile());
    }

    /** Сам обмен: всё определяется заново, состояние инвентаря могло поменяться за паузу. */
    private void performSwap() {
        Item first = resolve(firstItem);
        Item second = resolve(secondItem);
        if (first == null || second == null) return;

        int slot = InventoryUtil.findSlot(filterFor(pickWanted(first, second)));
        if (slot == -1) return;
        InventoryUtil.swapWithOffhand(slot);
    }

    /** Какой предмет нужно положить в руку (по текущему содержимому руки). */
    private Item pickWanted(Item first, Item second) {
        ItemStack off = InventoryUtil.getOffhand();
        if (matches(off, first)) return second;
        if (matches(off, second)) return first;
        return first;
    }

    private Predicate<ItemStack> filterFor(Item item) {
        return stack -> matches(stack, item);
    }

    /** Подходит ли стак под предмет с учётом «игнорировать обычные тотемы». */
    private boolean matches(ItemStack stack, Item item) {
        if (stack.isEmpty() || !stack.is(item)) return false;
        if (item == Items.TOTEM_OF_UNDYING && ignorePlain()) {
            return isSpecial(stack);
        }
        return true;
    }

    /**
     * Особый предмет — у которого компонент отличается от дефолтного для этого предмета.
     * stack.has() не подходит: он видит и дефолтные компоненты (ITEM_NAME, ITEM_MODEL
     * есть у любого ванильного предмета), из-за чего обычный тотем считался бы особым.
     */
    private static boolean isSpecial(ItemStack stack) {
        return changed(stack, DataComponents.CUSTOM_NAME)
                || changed(stack, DataComponents.ITEM_NAME)
                || changed(stack, DataComponents.LORE)
                || changed(stack, DataComponents.CUSTOM_MODEL_DATA)
                || changed(stack, DataComponents.ITEM_MODEL)
                || changed(stack, DataComponents.CUSTOM_DATA);
    }

    private static boolean changed(ItemStack stack, DataComponentType<?> type) {
        return !Objects.equals(stack.get(type), stack.getItem().components().get(type));
    }

    private String notFoundMessage(Item wanted) {
        if (wanted == Items.TOTEM_OF_UNDYING && ignorePlain()) {
            return "Особый тотем не найден в инвентаре";
        }
        return "Предмет не найден в инвентаре";
    }

    private SwapProfile buildProfile() {
        // Легит: новый профиль на каждый свап, тайминги каждый раз немного другие.
        if (!isCustom()) return SwapProfile.legit();
        boolean stopMove = stopWhat.isSelected(STOP_MOVE);
        return new SwapProfile(
                stopMove,
                stopWhat.isSelected(STOP_SPRINT),
                stopMove && stopWhat.isSelected(WAIT_STILL),
                (int) maxWait.getLong(),
                (int) beforeMin.getLong(),
                (int) beforeMax.getLong(),
                (int) afterMin.getLong(),
                (int) afterMax.getLong(),
                true,
                (int) betweenSwaps.getLong());
    }

    private static Item resolve(SelectSetting setting) {
        for (SwapItem item : SwapItem.values()) {
            if (setting.is(item.label)) return item.item;
        }
        return null;
    }

    private void notifyPlayer(String text) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }

    @Override
    protected void onDisable() {
        // Тиков больше не будет — клавиши возвращаем сразу, а не планируем.
        sequence.cancel();
        wasDown = false;
    }
}