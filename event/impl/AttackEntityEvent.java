package dev.lucid.event.impl;

import dev.lucid.event.CancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Атака по сущности до того, как ушёл пакет на сервер.
 *
 * <p>Постится в HEAD {@code MultiPlayerGameMode#attack}: это единственная точка,
 * через которую проходят и ванильный ЛКМ ({@code Minecraft#startAttack}), и модули,
 * вызывающие {@code mc.gameMode.attack(...)} сами. Отмена события отменяет весь метод,
 * то есть не уходит {@code ServerboundInteractPacket}, не вызывается локальный
 * {@code Player#attack} и не сбрасывается кулдаун — удар не состоялся ни для клиента,
 * ни для сервера.</p>
 *
 * <p>Правый клик ({@code interact}/{@code interactAt}) сюда не попадает: это не урон,
 * и запрещать торговлю или поводок с другом было бы уже другим поведением.</p>
 */
public final class AttackEntityEvent extends CancellableEvent {
    private final Player attacker;
    private final Entity target;

    public AttackEntityEvent(Player attacker, Entity target) {
        this.attacker = attacker;
        this.target = target;
    }

    public Player getAttacker() { return attacker; }
    public Entity getTarget() { return target; }
}
