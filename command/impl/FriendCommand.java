package dev.lucid.command.impl;

import dev.lucid.command.Command;
import dev.lucid.command.CommandManager;
import dev.lucid.util.players.friend.FriendManager;

import java.util.List;

/**
 * Первая клиентская команда: {@code .friend add|list|remove|clear}.
 *
 * <p>Все четыре действия — это просто вызовы {@link FriendManager}; команда не
 * знает ни о модулях, ни о мире, ни о сервере. Список друзей пока нигде больше не
 * используется: TriggerBot и остальные модули намеренно не тронуты.</p>
 *
 * <p>Подсказки устроены как у ванильных команд: пока первое слово после
 * {@code .friend} не закончено, предлагаем сабкоманды; в {@code remove} отдаём
 * сохранённые ники (их можно листать колёсиком и стрелками — это умеет сам
 * {@code CommandSuggestions.SuggestionsList}); в {@code add} кандидатов нет,
 * вместо них рисуется строка {@code .friend add <name>}.</p>
 */
public class FriendCommand extends Command {

    private static final List<String> SUBCOMMANDS = List.of("add", "list", "remove", "clear");
    private static final String ADD_USAGE = ".friend add <name>";
    private static final String REMOVE_USAGE = ".friend remove <name>";
    private static final int NAMES_PER_LINE = 8;

    public FriendCommand() {
        super("friend", "Manages the client-side friend list");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "add" -> add(args);
            case "remove" -> remove(args);
            case "list" -> printList();
            case "clear" -> clear();
            default -> printUsage();
        }
    }

    private static void add(String[] args) {
        if (args.length != 2) {
            CommandManager.send("Usage: " + ADD_USAGE);
            return;
        }
        String name = args[1];
        if (!FriendManager.isValid(name)) {
            CommandManager.send("Invalid name");
            return;
        }
        if (FriendManager.contains(name)) {
            CommandManager.send("Already a friend");
            return;
        }
        FriendManager.add(name);
        CommandManager.send("Added " + name);
    }

    private static void remove(String[] args) {
        if (args.length != 2) {
            CommandManager.send("Usage: " + REMOVE_USAGE);
            return;
        }
        String name = args[1];
        if (!FriendManager.remove(name)) {
            CommandManager.send("Not a friend");
            return;
        }
        CommandManager.send("Removed " + name);
    }

    private static void printList() {
        List<String> names = FriendManager.names();
        if (names.isEmpty()) {
            CommandManager.send("No friends");
            return;
        }
        if (names.size() <= NAMES_PER_LINE) {
            CommandManager.send("Friends (" + names.size() + "): " + String.join(", ", names));
            return;
        }
        // Длинный список не превращается в одну нечитаемую строку чата
        CommandManager.send("Friends (" + names.size() + "):");
        for (int i = 0; i < names.size(); i += NAMES_PER_LINE) {
            int end = Math.min(i + NAMES_PER_LINE, names.size());
            CommandManager.send("  " + String.join(", ", names.subList(i, end)));
        }
    }

    private static void clear() {
        int count = FriendManager.clear();
        CommandManager.send("Cleared " + count + " friends");
    }

    private static void printUsage() {
        CommandManager.send(ADD_USAGE + " - add a friend");
        CommandManager.send(".friend list - show saved friends");
        CommandManager.send(REMOVE_USAGE + " - remove a friend");
        CommandManager.send(".friend clear - remove all friends");
    }

    @Override
    public List<String> suggest(String rest) {
        int space = rest.indexOf(' ');
        // Сабкоманда ещё набирается — ваниль сама оставит подходящие по префиксу
        if (space < 0) return SUBCOMMANDS;

        String sub = rest.substring(0, space);
        if (sub.equalsIgnoreCase("remove")) return FriendManager.names();
        // add: ники онлайн не предлагаем, вместо них работает usage-строка.
        // list и clear аргументов не принимают.
        return List.of();
    }

    @Override
    public String getUsageHint(String rest) {
        int space = rest.indexOf(' ');
        String sub = space < 0 ? rest : rest.substring(0, space);
        return sub.equalsIgnoreCase("add") ? ADD_USAGE : null;
    }
}
