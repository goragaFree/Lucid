package dev.lucid.module.impl.player;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.MultiSelectSetting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Не даёт случайно выкинуть предмет из защищённых слотов хотбара.
 *
 * <p>Слоты выбираются мультиселектом: можно закрыть один, можно все девять.
 * Нумерация — как у игрока, 1..9, в порядке слева направо.</p>
 *
 * <p>Закрыты два пути выкидывания:</p>
 * <ul>
 *   <li>клавиша Q в игре — это {@code LocalPlayer#drop(boolean)}, он берёт предмет
 *       из выбранного слота хотбара и шлёт серверу {@code DROP_ITEM};</li>
 *   <li>клавиша Q в экране инвентаря — это клик {@code ClickType.THROW} по наведённому
 *       слоту, он уходит через {@code MultiPlayerGameMode#handleInventoryMouseClick}.</li>
 * </ul>
 *
 * <p>Индексы слотов у клиента разные: в первом случае это индекс выбранного слота
 * инвентаря (0..8), во втором — индекс слота внутри контейнера, а он у слотов
 * инвентаря игрока совпадает с индексом в самом инвентаре (0..8 — хотбар,
 * 9..35 — основной инвентарь). Поэтому выкидывание из экрана инвентаря дополнительно
 * проверяется по контейнеру слота: у слотов сундука индексы тоже начинаются с нуля,
 * но контейнер у них другой.</p>
 */
public final class LockSlot extends Module {

    /** Номера слотов так, как их видит игрок: слева направо, 1..9. */
    private static final String[] SLOT_NAMES = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};

    // Модуль создаётся один раз в ModuleManager.init(); миксину незачем искать его
    // в реестре модулей на каждый выкинутый предмет.
    private static LockSlot instance;

    private final MultiSelectSetting slots = new MultiSelectSetting("Slots", SLOT_NAMES);
    private final BooleanSetting sneakBypass = new BooleanSetting("Sneak Bypass", false);

    public LockSlot() {
        super("LockSlot", "Block accidental drops from locked hotbar slots", Category.PLAYER);
        addSettings(slots, sneakBypass);
        instance = this;
    }

    /**
     * Заблокирован ли слот хотбара.
     *
     * @param player    игрок, который выкидывает предмет
     * @param slotIndex индекс слота хотбара, 0..8
     */
    public static boolean blocks(Player player, int slotIndex) {
        LockSlot module = instance;
        if (module == null || !module.isEnabled()) return false;
        if (player == null || slotIndex < 0 || slotIndex > 8) return false;
        // Шифт снимает защиту: удобно, когда нужно срочно выкинуть стак.
        if (module.sneakBypass.getValue() && player.isShiftKeyDown()) return false;
        return module.slots.isSelected(SLOT_NAMES[slotIndex]);
    }

    /**
     * Клик по слоту в экране контейнера: выкидывание по наведённому слоту.
     *
     * @param slotId индекс слота, как его понимает меню контейнера
     */
    public static boolean blocksSlotClick(Player player, int slotId) {
        if (instance == null || !instance.isEnabled()) return false;
        if (player == null) return false;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return false;
        for (Slot slot : menu.slots) {
            // Слот инвентаря игрока: контейнер — его инвентарь, индекс — индекс в нём.
            // Проверяем и индекс, и контейнер, чтобы не перепутать слот сундука с тем
            // же индексом — у сундука контейнер другой.
            if (slot.index == slotId && slot.container == player.getInventory()) {
                return blocks(player, slot.index);
            }
        }
        return false;
    }
}
