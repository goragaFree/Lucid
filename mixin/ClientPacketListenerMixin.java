package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.command.CommandManager;
import dev.lucid.module.impl.render.Ambience;
import dev.lucid.module.impl.movement.AirStuck;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    /**
     * Страховка для клиентских команд: {@code ChatScreenMixin} перехватывает ввод
     * игрока, но строку с {@code "."} может отправить макрос или другой мод напрямую
     * в {@code sendChat}. Пакет в таком случае не создаётся вообще — условие
     * «точка не покидает ПК» должно выполняться для любого вызывающего, а не только
     * для поля ввода. Команду при этом исполняем локально, чтобы макрос работал.
     */
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void lucid$blockClientCommands(String message, CallbackInfo ci) {
        String normalized = message == null ? "" : message.trim();
        if (!CommandManager.isClientCommand(normalized)) return;
        ci.cancel();
        CommandManager.execute(normalized);
    }

    /** Пока Ambience задаёт время (Custom Time) — не даём серверу перезаписывать время. */
    @Inject(method = "handleSetTime", at = @At("HEAD"), cancellable = true)
    private void lucid$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        Ambience ambience = Lucid.getInstance().getModuleManager().get(Ambience.class);
        if (ambience != null && ambience.isEnabled() && ambience.overridesTime()) {
            ci.cancel();
        }
    }

    /**
     * Сервер шлёт RAIN_LEVEL_CHANGE каждый тик, пока плавно вводит или убирает дождь.
     * Если погоду задаёт Ambience, эти пакеты только дёргали бы яркость осадков туда-сюда.
     */
    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void lucid$onGameEvent(ClientboundGameEventPacket packet, CallbackInfo ci) {
        if (packet.getEvent() != ClientboundGameEventPacket.RAIN_LEVEL_CHANGE) return;
        Ambience ambience = Lucid.getInstance().getModuleManager().get(Ambience.class);
        if (ambience != null && ambience.isEnabled() && ambience.overridesRainLevel()) {
            ci.cancel();
        }
    }

    /**
     * Имя {@code handleMovePlayer(ClientboundPlayerPositionPacket)} подтверждено
     * байткодом 1.21.11. HEAD, а не TAIL: модуль должен выключиться до того, как
     * обработчик отправит подтверждение, иначе заморозка съест ответ серверу.
     *
     * <p>Обработчик шлёт {@code ServerboundAcceptTeleportationPacket} и
     * {@code ServerboundMovePlayerPacket$PosRot} напрямую через
     * {@code Connection.send}, а не через {@code ClientCommonPacketListenerImpl#send},
     * поэтому наш блокирующий миксин эти пакеты в принципе не видит.</p>
     *
     * <p>ПРОВЕРИТЬ: пакет приходит при любом телепорте (вход в мир, /tp, респавн),
     * а не только при откате anticheat — возможны ложные выключения модуля.</p>
     */
    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",
            at = @At("HEAD"))
    private void lucid$onSetback(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        AirStuck.onServerSetback();
    }
}
