package dev.lucid.command;

import java.util.List;

/**
 * База клиентской команды: всё, что набрано с префиксом {@code "."}, живёт только
 * в этом клиенте и не превращается ни в {@code ServerboundChatPacket}, ни в
 * серверную команду.
 *
 * <p>Свой парсер вместо Brigadier — осознанно: Brigadier в клиенте привязан к
 * префиксу {@code "/"} и к {@code ClientSuggestionProvider}, а нам нужен короткий
 * реестр с Human-readable подсказками. Аргументы приходят уже разрезанными по
 * пробелам, поэтому команда отвечает только за свою логику, а за префикс, поиск
 * команды и чат отвечает {@link CommandManager}.</p>
 */
public abstract class Command {

    private final String name;
    private final String description;

    protected Command(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    /** @param args слова после имени команды, без ведущей точки. */
    public abstract void execute(String[] args);

    /**
     * Кандидаты для последнего слова ввода: команда возвращает их целиком, а не
     * отфильтрованными — сужает список до набранного префикса уже ванильный
     * {@code SharedSuggestionProvider.suggest(...)} по {@code matchesSubStr}.
     *
     * @param rest всё, что набрано после имени команды и первого пробела;
     *             завершающий пробел важен: он означает «аргумент ещё не начат».
     */
    public abstract List<String> suggest(String rest);

    /**
     * Строка usage над полем ввода, когда команд подсказать нечего (например,
     * {@code .friend add <name>}). {@code null} — ничего не рисовать.
     */
    public String getUsageHint(String rest) {
        return null;
    }
}
