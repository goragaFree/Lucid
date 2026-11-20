package dev.lucid.util.inventory;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/** Access to the screen's own hit test and virtual click handler (including creative). */
public interface ContainerScreenBridge {
    Slot lucid$slotAt(double mouseX, double mouseY);
    void lucid$clickSlot(Slot slot, int slotId, int button, ClickType type);
}
