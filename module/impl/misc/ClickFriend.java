package dev.lucid.module.impl.misc;

import dev.lucid.command.CommandManager;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.KeyInputEvent;
import dev.lucid.event.impl.MouseInputEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BindSetting;
import dev.lucid.util.players.friend.FriendManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Добавляет или убирает друга по игроку под прицелом, пока нажат назначенный бинд.
 *
 * <p>Друзья — чисто клиентский статус (см. {@link FriendManager}): здесь нет ни
 * одного пакета на сервер, меняется только локальный {@code config.json}, поэтому
 * модуль ничего не знает о командах, целях атаки и списках сущностей сервера.
 * Ответ пишется в локальный чат тем же {@link CommandManager#send(String)}, что и
 * {@code .friend add/remove}, — чтобы имя и формат совпадали с ручной командой.</p>
 *
 * <p>Цель берём из {@code mc.hitResult}, а не своим рейкастом: это готовый ванильный
 * прицел с его дальностью и отсечением блоками. Свой луч здесь был бы второй
 * «правдой» о том, на кого смотрит игрок, и разошёлся бы с прицелом на границе
 * хитбокса. Имя берём через {@code Player#getPlainTextName()} и не приводим к
 * нижнему регистру: {@code haroHw243} и {@code harohw243} для автора разные ники.</p>
 *
 * <p>Реакция идёт по фронту нажатия ({@code GLFW_PRESS}), а не опросом
 * {@code glfwGetKey} в тике: удержание клавиши не должно превращаться в
 * бесконечное добавление/удаление, а короткий клик мыши не должен теряться
 * между двумя тиками. Событие не отменяем — клавиша остаётся и у ванили.</p>
 */
public final class ClickFriend extends Module {

    private final BindSetting friendBind = new BindSetting("Add Friend");

    public ClickFriend() {
        super("ClickFriend", "Add or remove a friend by aiming at a player", Category.MISC);
        addSettings(friendBind);
    }

    @Subscribe
    private void onKey(KeyInputEvent event) {
        int key = friendBind.getKey();
        // -1 означает «бинд не назначен», а такие коды приходят и от GLFW
        if (key < 0 || event.getAction() != GLFW.GLFW_PRESS || event.getKey() != key) return;
        toggleFriendUnderCrosshair();
    }

    @Subscribe
    private void onMouse(MouseInputEvent event) {
        int button = friendBind.getMouseButton();
        if (button < 0 || event.getAction() != GLFW.GLFW_PRESS || event.getButton() != button) return;
        toggleFriendUnderCrosshair();
    }

    /** Одно нажатие = одно переключение: был другом — убираем, не был — добавляем. */
    private void toggleFriendUnderCrosshair() {
        // В открытом экране нажатие принадлежит GUI, а не миру
        if (mc.screen != null || mc.player == null || mc.level == null) return;
        if (!(mc.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof Player player)
                || player == mc.player) return;

        String name = player.getPlainTextName();
        if (!FriendManager.isValid(name)) {
            CommandManager.send("Invalid name");
            return;
        }
        if (FriendManager.remove(name)) {
            CommandManager.send("Removed " + name);
            return;
        }
        FriendManager.add(name);
        CommandManager.send("Added " + name);
    }
}
