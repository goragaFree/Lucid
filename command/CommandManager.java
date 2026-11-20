package dev.lucid.command;

import dev.lucid.command.impl.BindCommand;
import dev.lucid.command.impl.FriendCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Реестр клиентских команд. Это не Brigadier и не серверный диспетчер: обычный
 * список команд плюс разбор строки после {@code "."} на слова. Один реестр нужен,
 * чтобы каждая команда не парсила ввод заново и не тащила за собой префикс,
 * историю чата и сбор подсказок.
 *
 * <p>Префикс жёстко один — {@code "."}: всё, что с него начинается, забирается
 * {@code ChatScreenMixin} до отправки и не покидает клиент. Страховка в
 * {@code ClientPacketListenerMixin} ловит тот же случай, если строку отправит
 * макрос или другой мод, минуя поле ввода.</p>
 *
 * <p>Подсказки отдаём не своим рендером, а ванилью: {@code CommandSuggestionsMixin}
 * подменяет список кандидатов, а {@code SharedSuggestionProvider.matchesSubStr}
 * сам отсечёт лишнее по набранному слову. Поэтому {@link #getSuggestions(String)}
 * возвращает полные строки-кандидаты, без ручной фильтрации по префиксу.</p>
 */
public final class CommandManager {

    public static final String PREFIX = ".";

    private static final List<Command> COMMANDS = new ArrayList<>();

    private CommandManager() {}

    /** Вызывается один раз из {@code Lucid#onInitializeClient}. */
    public static void init() {
        COMMANDS.clear();
        register(new FriendCommand());
        register(new BindCommand());
    }

    /** Точка расширения API: сюда регистрируются будущие команды клиента. */
    public static void register(Command command) {
        COMMANDS.add(command);
    }

    public static List<Command> getCommands() {
        return List.copyOf(COMMANDS);
    }

    /** Ведущие пробелы не должны превратить команду в обычное сообщение чата. */
    public static boolean isClientCommand(String message) {
        return message != null && message.stripLeading().startsWith(PREFIX);
    }

    /**
     * Исполняет набранную строку. Неизвестная команда отвечает в локальный чат —
     * это осознанная альтернатива отправке на сервер: пользователь видит, что
     * опечатался, а сервер не получает вообще ничего.
     */
    public static void execute(String line) {
        if (!isClientCommand(line)) return;

        String body = line.stripLeading().substring(PREFIX.length()).trim();
        if (body.isEmpty()) {
            send("Unknown command");
            return;
        }
        String[] words = body.split("\\s+");
        Command command = find(words[0]);
        if (command == null) {
            send("Unknown command: " + PREFIX + words[0]);
            return;
        }
        command.execute(Arrays.copyOfRange(words, 1, words.length));
    }

    /**
     * Кандидаты для выпадающего списка. Пока набирается первое слово, предлагаем
     * имена команд с точкой ({@code .friend}); после первого пробела управление
     * переходит самой команде — она знает свои сабкоманды и свои аргументы.
     */
    public static List<String> getSuggestions(String line) {
        if (!isClientCommand(line)) return List.of();

        String body = line.stripLeading().substring(PREFIX.length());
        int space = body.indexOf(' ');
        if (space < 0) {
            List<String> names = new ArrayList<>();
            for (Command command : COMMANDS) names.add(PREFIX + command.getName());
            return names;
        }

        Command command = find(body.substring(0, space));
        if (command == null) return List.of();
        // Пробелы между командой и аргументом не меняют смысла, а завершающий
        // пробел — наоборот, важен: он означает «аргумент ещё не начат».
        return command.suggest(body.substring(space + 1).stripLeading());
    }

    /** Строка usage над полем ввода или {@code null}, если рисовать нечего. */
    public static String getUsageHint(String line) {
        if (!isClientCommand(line)) return null;

        String body = line.stripLeading().substring(PREFIX.length());
        int space = body.indexOf(' ');
        if (space < 0) return null;

        Command command = find(body.substring(0, space));
        if (command == null) return null;
        return command.getUsageHint(body.substring(space + 1).stripLeading());
    }

    /** Имена команд регистронезависимы: {@code .FRIEND ADD X} — та же команда. */
    private static Command find(String name) {
        for (Command command : COMMANDS) {
            if (command.getName().equalsIgnoreCase(name)) return command;
        }
        return null;
    }

    /**
     * Ответ только в клиентский чат. {@code ChatComponent#addMessage} не создаёт
     * пакетов и не трогает подписи сообщений — в отличие от {@code sendChat}.
     */
    public static void send(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        mc.gui.getChat().addMessage(Component.literal("[Lucid] ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
    }
}
