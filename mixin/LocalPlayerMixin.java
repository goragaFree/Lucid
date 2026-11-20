package dev.lucid.mixin;

import dev.lucid.module.impl.player.LockSlot;
import dev.lucid.module.impl.player.NoEntityTrace;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Два перехвата на LocalPlayer: выкидывание по Q (LockSlot) и расчёт прицела
 * (NoEntityTrace).
 *
 * <p>LockSlot: {@code Minecraft#handleKeybinds} зовёт {@code player.drop(...)}.
 * Возвращаем {@code false}, чтобы предмет остался в слоте и рука не махнула —
 * ванильный код в {@code handleKeybinds} делает {@code swing}, только если
 * {@code drop} вернул {@code true}.</p>
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void lucid$lockSlotDrop(boolean wholeStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (LockSlot.blocks(player, player.getInventory().getSelectedSlot())) {
            cir.setReturnValue(false);
        }
    }

    /**
     * NoEntityTrace: HEAD обязателен, потому что сравнение «блок против сущности» и
     * трассировка сущностей происходят в самом теле {@code raycastHitResult} — любой
     * инжект позже уже видел бы результат с сущностью. Возвращённый блок-онли результат
     * {@code GameRenderer#pick} кладёт в {@code minecraft.hitResult} как ванильный.
     */
    @Inject(method = "raycastHitResult", at = @At("HEAD"), cancellable = true)
    private void lucid$noEntityTrace(float partialTick, Entity cameraEntity,
                                     CallbackInfoReturnable<HitResult> cir) {
        HitResult blockOnly = NoEntityTrace.blockOnlyPick((LocalPlayer) (Object) this,
                cameraEntity, partialTick);
        if (blockOnly != null) cir.setReturnValue(blockOnly);
    }
}