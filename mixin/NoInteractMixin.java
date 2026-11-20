package dev.lucid.mixin;


import dev.lucid.module.impl.combat.NoInteract;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Три точки, через которые {@code Minecraft#startUseItem()} пробует ПКМ, — и только они.
 *
 * <p>Все три метода возвращают {@link InteractionResult}, и {@code PASS} для ванили
 * означает «этот вариант ничего не сделал, попробуй следующий»: после {@code useItemOn}
 * и после {@code interact} управление доходит до {@code gameMode.useItem(...)}, то есть
 * до использования предмета в руке. Отмена через {@code ci.cancel()} здесь не годится —
 * у {@code CallbackInfoReturnable} она вернула бы {@code null}, а ваниль сразу зовёт
 * {@code result.consumesAction()} и упадёт.</p>
 *
 * <p>HEAD обязателен: пакеты уходят в самом начале методов, до локального
 * взаимодействия. Инжект в RETURN оставил бы серверу команду открыть сундук или
 * торговлю, даже если на клиенте экран уже не показался.</p>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class NoInteractMixin {

    /**
     * ПКМ по блоку. Один и тот же код для любого блока из настройки: модуль сам
     * достаёт состояние по позиции попадания и сверяет его реестровый ID.
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void lucid$noInteractBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        if (NoInteract.shouldCancelBlock(player, hitResult)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /** ПКМ по сущности: второй шаг ванили, после {@code interactAt}. */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void lucid$noInteractEntity(Player player, Entity entity, InteractionHand hand,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (NoInteract.shouldCancelEntity(player, entity)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /**
     * ПКМ по сущности с точкой попадания — отдельный метод и отдельный пакет.
     * Зовётся первым для любой сущности под прицелом (в том числе для жителя и
     * торговца), поэтому без этого хука сервер открыл бы торговлю ещё до
     * {@code interact}.
     */
    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void lucid$noInteractEntityAt(Player player, Entity entity, EntityHitResult hitResult,
                                          InteractionHand hand,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (NoInteract.shouldCancelEntity(player, entity)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
