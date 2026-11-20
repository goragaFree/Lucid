package dev.lucid.module.impl.combat;

import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.AttackEntityEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.util.players.friend.FriendManager;
import net.minecraft.world.entity.player.Player;

/**
 * Запрещает наносить урон сохранённым друзьям.
 *
 * <p>Друзья — чисто клиентский список ников (см. {@link FriendManager}), поэтому
 * модуль не знает ни о сервере, ни о командах, ни о пакетах: он отменяет ещё не
 * отправленный {@link AttackEntityEvent} в общей точке всех ударов. Благодаря этому
 * защита работает и для ручного клика, и для любого модуля, который атакует через
 * {@code mc.gameMode.attack(...)} — код самих модулей при этом не меняется.</p>
 *
 * <p>Сверяем только {@code Player}: статус друга привязан к нику, а имя сущности
 * берётся из {@code Player#getPlainTextName()} — ровно так же, как в {@code .friend}
 * и {@code ClickFriend}. Регистр не приводим ни к какому виду: {@code haroHw243} и
 * {@code harohw243} для автора разные ники, и «защитить» не того было бы хуже,
 * чем не защитить вовсе.</p>
 *
 * <p>Правый клик не трогаем: торговля, поводок и посадка в лодку с другом — не урон.
 * Отдельная оговорка по 1.21.11: удар {@code PIERCING_WEAPON} (копьё) уходит пакетом
 * {@code ServerboundPlayerActionPacket(STAB)} без цели, список сущностей по лучу
 * собирает уже сервер — этот путь через {@code attack(...)} не проходит.</p>
 */
public final class NoFriendDamage extends Module {

    public NoFriendDamage() {
        super("NoFriendDamage", "Never attack a saved friend", Category.COMBAT);
    }

    @Subscribe
    private void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || player == event.getAttacker()) return;
        if (FriendManager.contains(player.getPlainTextName())) event.cancel();
    }
}
