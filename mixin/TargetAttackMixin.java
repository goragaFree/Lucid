package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.event.impl.AttackEntityEvent;
import dev.lucid.sreen.hud.TargetHud;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class TargetAttackMixin {

    /**
     * Общая точка всех ударов по сущности: и ванильного клика, и вызовов из модулей.
     *
     * <p>Сначала даём модулям отменить удар ({@code NoFriendDamage}), и только потом
     * запоминаем цель для {@code TargetHud}: отменённый удар не должен появляться в
     * плашке как «цель, которую мы били». Один инжект на метод вместо двух нужен ещё
     * и затем, чтобы порядок между отменой и запоминанием не зависел от приоритетов
     * миксинов.</p>
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void lucid$rememberTarget(Player player, Entity entity, CallbackInfo ci) {
        Lucid lucid = Lucid.getInstance();
        if (lucid != null) {
            AttackEntityEvent event = lucid.getEventBus().post(new AttackEntityEvent(player, entity));
            if (event.isCancelled()) {
                ci.cancel();
                return;
            }
        }
        TargetHud.remember(entity);
    }
}
