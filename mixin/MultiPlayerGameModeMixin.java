package dev.lucid.mixin;

import dev.lucid.module.impl.movement.InventoryMove;
import dev.lucid.module.impl.player.LockSlot;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перехватывает выкидывание по клавише Q в экране контейнера: там ваниль шлёт клик
 * {@code ClickType.THROW} по наведённому слоту через {@code handleInventoryMouseClick}.
 * Отменяем его, если наведённый слот — защищённый слот хотбара игрока.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void lucid$lockSlotThrow(int containerId, int slotId, int button, ClickType clickType,
                                    Player player, CallbackInfo ci) {
        if (clickType != ClickType.THROW) return;
        if (LockSlot.blocksSlotClick(player, slotId)) ci.cancel();
    }

    /**
     * InventoryMove: ПКМ-клики и PICKUP_ALL во время движения отменяются — сервер увидел бы
     * щелчки, которые не совпали с видимым движением персонажа.
     */
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void lucid$cancelRiskyClick(int containerId, int slotId, int button,
                                        ClickType clickType, Player player, CallbackInfo ci) {
        if (InventoryMove.INSTANCE != null && InventoryMove.INSTANCE.cancelClick(clickType, button)) ci.cancel();
    }
}