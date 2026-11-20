package dev.lucid.util.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Общие операции с инвентарём игрока для модулей.
 *
 * <p>Все индексы — это индексы слотов {@link InventoryMenu} (меню инвентаря игрока,
 * containerId = 0), а не индексы {@code Inventory}: 9..35 — основной инвентарь,
 * 36..44 — хотбар, 45 — вторая рука. Меню инвентаря доступно без открытого экрана,
 * поэтому клики работают и в игре без GUI.</p>
 */
public final class InventoryUtil {

    private static final Minecraft mc = Minecraft.getInstance();

    public static final int MAIN_START = 9;
    public static final int MAIN_END = 35;
    public static final int HOTBAR_START = 36;
    public static final int HOTBAR_END = 44;
    public static final int OFFHAND_SLOT = 45;

    /** Кнопка ClickType.SWAP, означающая «поменять со второй рукой». */
    private static final int OFFHAND_BUTTON = 40;

    private InventoryUtil() {}

    public static boolean isReady() {
        return mc.player != null && mc.level != null && mc.gameMode != null;
    }

    public static ItemStack getOffhand() {
        return mc.player != null ? mc.player.getOffhandItem() : ItemStack.EMPTY;
    }

    public static boolean offhandIs(Item item) {
        return getOffhand().is(item);
    }

    /** Ищет слот с предметом: сначала хотбар, потом основной инвентарь. Нет — {@code -1}. */
    public static int findSlot(Item item) {
        return findSlot(stack -> stack.is(item));
    }

    public static int findSlot(Predicate<ItemStack> filter) {
        if (!isReady()) return -1;
        int hotbar = findInRange(HOTBAR_START, HOTBAR_END, filter);
        if (hotbar != -1) return hotbar;
        return findInRange(MAIN_START, MAIN_END, filter);
    }

    private static int findInRange(int from, int to, Predicate<ItemStack> filter) {
        InventoryMenu menu = mc.player.inventoryMenu;
        for (int i = from; i <= to; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && filter.test(stack)) return slot.index;
        }
        return -1;
    }

    /** Меняет местами указанный слот меню инвентаря и вторую руку. */
    public static void swapWithOffhand(int menuSlot) {
        if (!isReady() || menuSlot < 0) return;
        click(menuSlot, OFFHAND_BUTTON, ClickType.SWAP);
    }

    /** Отправляет пакет закрытия меню инвентаря — как при закрытии экрана инвентаря. */
    public static void closeContainer() {
        if (!isReady() || mc.getConnection() == null) return;
        mc.getConnection().send(
                new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
    }

    public static void click(int menuSlot, int button, ClickType type) {
        if (!isReady() || menuSlot < 0) return;
        mc.gameMode.handleInventoryMouseClick(
                mc.player.inventoryMenu.containerId, menuSlot, button, type, mc.player);
    }
}