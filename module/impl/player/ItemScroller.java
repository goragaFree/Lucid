package dev.lucid.module.impl.player;

import dev.lucid.Lucid;
import dev.lucid.mixin.CreativeInventoryAccess;
import dev.lucid.mixin.CreativeSlotAccessor;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.inventory.ContainerScreenBridge;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Shift + LMB only. Tick polling keeps a hold alive outside the slot grid. */
public final class ItemScroller extends Module {
    private final SliderSetting delay = new SliderSetting("Задержка", 50.0, 0.0, 200.0, 5.0);
    private AbstractContainerScreen<?> activeScreen;
    private Slot lastSlot;
    private long lastTransfer;

    public ItemScroller() {
        super("ItemScroller", "Shift + left drag to quick-move items", Category.PLAYER);
        addSettings(delay);
    }

    public static ItemScroller getActive() {
        Lucid client = Lucid.getInstance();
        if (client == null || client.getModuleManager() == null) return null;
        ItemScroller module = client.getModuleManager().get(ItemScroller.class);
        return module != null && module.isEnabled() ? module : null;
    }

    /** Intercept the first press, so vanilla cannot pick a creative stack up. */
    public boolean onClick(AbstractContainerScreen<?> screen, double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !shiftDown() || !canUse(screen)) return false;
        useScreen(screen);
        Slot slot = ((ContainerScreenBridge) screen).lucid$slotAt(x, y);
        if (!managedSlot(screen, slot)) return false;
        transfer(screen, slot);
        return true;
    }

    /** Also run on movement; onTick retries a hovered slot once its delay expires. */
    public boolean onDrag(AbstractContainerScreen<?> screen, double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !held() || !canUse(screen)) return false;
        useScreen(screen);
        Slot slot = ((ContainerScreenBridge) screen).lucid$slotAt(x, y);
        transfer(screen, slot);
        // Consume even outside the grid. Leaving it is NOT a release.
        return true;
    }

    public boolean onRelease(AbstractContainerScreen<?> screen, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        boolean handled = activeScreen == screen;
        reset();
        return handled;
    }

    @Override
    public void onTick() {
        if (!held() || !(mc.screen instanceof AbstractContainerScreen<?> screen)
                || !canUse(screen)) {
            reset();
            return;
        }
        useScreen(screen);
        // No requirement for the original press to have happened over a slot.
        double x = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double y = mc.mouseHandler.getScaledYPos(mc.getWindow());
        transfer(screen, ((ContainerScreenBridge) screen).lucid$slotAt(x, y));
    }

    private void useScreen(AbstractContainerScreen<?> screen) {
        if (activeScreen != screen) {
            reset();
            activeScreen = screen;
        }
    }

    private boolean canUse(AbstractContainerScreen<?> screen) {
        return mc.player != null && mc.gameMode != null && mc.screen == screen
                && mc.isWindowActive() && !mc.player.isSpectator()
                && screen.getMenu().getCarried().isEmpty();
    }

    private boolean managedSlot(AbstractContainerScreen<?> screen, Slot slot) {
        if (slot == null || !slot.isActive() || menuIndex(screen, slot) < 0) return false;
        if (!(screen instanceof CreativeModeInventoryScreen)) return true;
        if (!mc.player.getAbilities().instabuild
                || slot == ((CreativeInventoryAccess) screen).lucid$destroyItemSlot()) return false;
        // The plain hotbar on catalog tabs is intentionally included: its
        // vanilla QUICK_MOVE action deletes the hovered stack, as requested.
        // The separate trash slot above remains excluded (it can clear ALL slots).
        return true;
    }

    private void transfer(AbstractContainerScreen<?> screen, Slot slot) {
        if (!managedSlot(screen, slot) || slot == lastSlot) return;
        if (!slot.hasItem() || !slot.mayPickup(mc.player)) {
            lastSlot = slot;
            return;
        }
        long now = System.nanoTime();
        if (lastTransfer != 0L && now - lastTransfer < delay.getInt() * 1_000_000L) return;
        if (screen instanceof CreativeModeInventoryScreen
                && !(slot instanceof CreativeSlotAccessor)
                && slot.container != mc.player.getInventory()) {
            // Catalog stacks go straight into the player's inventory, not the cursor.
            giveToInventory(slot.getItem().copyWithCount(slot.getItem().getMaxStackSize()));
        } else {
            // Survival/inventory-tab wrappers: normal quick-move.
            // Plain creative catalog hotbar: vanilla deletes ONLY this stack.
            // Pass the list position, not the unreliable creative Slot.index.
            ((ContainerScreenBridge) screen).lucid$clickSlot(slot, menuIndex(screen, slot),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT, ClickType.QUICK_MOVE);
        }
        lastSlot = slot;
        lastTransfer = now;
    }

    private int menuIndex(AbstractContainerScreen<?> screen, Slot slot) {
        for (int i = 0; i < screen.getMenu().slots.size(); i++) {
            if (screen.getMenu().slots.get(i) == slot) return i;
        }
        return -1;
    }

    /** Creative only: merge into existing stacks, then fill empty inventory slots. */
    private void giveToInventory(ItemStack remaining) {
        if (!mc.player.getAbilities().instabuild || remaining.isEmpty()) return;
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < 36 && !remaining.isEmpty(); i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), remaining.getCount());
            if (moved <= 0) continue;
            store(inventory, i, existing.copyWithCount(existing.getCount() + moved));
            remaining.shrink(moved);
        }
        for (int i = 0; i < 36 && !remaining.isEmpty(); i++) {
            if (!inventory.getItem(i).isEmpty()) continue;
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            store(inventory, i, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        inventory.setChanged();
        // No room: do not drop items or overwrite occupied slots.
    }

    private void store(Inventory inventory, int index, ItemStack stack) {
        inventory.setItem(index, stack);
        mc.gameMode.handleCreativeModeItemAdd(stack.copy(), index < 9 ? index + 36 : index);
    }

    private boolean held() {
        long window = GLFW.glfwGetCurrentContext();
        return window != 0L && shiftDown()
                && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private boolean shiftDown() {
        long window = GLFW.glfwGetCurrentContext();
        return window != 0L && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
    }

    @Override
    protected void onEnable() { reset(); }

    @Override
    protected void onDisable() { reset(); }

    private void reset() {
        activeScreen = null;
        lastSlot = null;
        lastTransfer = 0L;
    }
}
