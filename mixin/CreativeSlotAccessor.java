package dev.lucid.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Identifies inventory-tab wrappers without depending on their broken index field. */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface CreativeSlotAccessor {
    @Accessor("target")
    Slot lucid$target();
}
