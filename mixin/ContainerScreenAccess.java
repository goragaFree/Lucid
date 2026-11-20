package dev.lucid.mixin;

import dev.lucid.util.inventory.ContainerScreenBridge;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenAccess implements ContainerScreenBridge {
    @Override
    @Invoker("getHoveredSlot")
    public abstract Slot lucid$slotAt(double mouseX, double mouseY);

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int button, ClickType type);

    @Override
    public void lucid$clickSlot(Slot slot, int slotId, int button, ClickType type) {
        // Deliberately a virtual call, NOT a direct gameMode click or super call.
        slotClicked(slot, slotId, button, type);
    }
}
