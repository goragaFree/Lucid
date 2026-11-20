package dev.lucid.mixin;

import dev.lucid.module.impl.movement.AirStuck;
import dev.lucid.module.impl.movement.InventoryMove;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Отменяет только исходящие пакеты движения. Хук стоит в базовом классе:
 * {@code send(Packet<?>)} объявлен в {@code ClientCommonPacketListenerImpl},
 * {@code ClientPacketListener} его наследует и не переопределяет.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {

    /**
     * Сначала отсекаем по типу пакета: миксин вызывается на каждый send, а чат,
     * атака, свинг, команды и keep-alive должны уходить без задержек.
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void lucid$blockMovementPackets(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ServerboundMovePlayerPacket
                || packet instanceof ServerboundMoveVehiclePacket)) return;
        if (AirStuck.shouldBlockPackets()) ci.cancel();
    }

    /**
     * InventoryMove: клики по слотам во время движения придерживаются и уходят пачкой
     * перед закрытием инвентаря. Хук стоит в базовом классе: {@code send} объявлен
     * здесь, {@code ClientPacketListener} его наследует и не переопределяет.
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void lucid$holdClicks(Packet<?> packet, CallbackInfo ci) {
        if (InventoryMove.INSTANCE != null && InventoryMove.INSTANCE.holdPacket(packet)) ci.cancel();
    }
}
