package dev.lucid.command.impl;

import dev.lucid.Lucid;
import dev.lucid.command.Command;
import dev.lucid.command.CommandManager;
import dev.lucid.module.Module;
import dev.lucid.module.ModuleManager;
import dev.lucid.util.KeyNames;

import java.util.ArrayList;
import java.util.List;

/**
 * Команда для управления биндами модулей: {@code .bind add|change|remove|list|clear}.
 *
 * <p>Позволяет добавлять, изменять и удалять бинды модулей через команды,
 * а также просматривать список всех забинденных модулей.</p>
 */
public class BindCommand extends Command {

    private static final List<String> SUBCOMMANDS = List.of("add", "change", "remove", "list", "clear");
    private static final String ADD_USAGE = ".bind add <module> <key>";
    private static final String CHANGE_USAGE = ".bind change bind <module> <key>";
    private static final String REMOVE_USAGE = ".bind remove <module>";

    public BindCommand() {
        super("bind", "Manages module keybinds");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "add" -> add(args);
            case "change" -> change(args);
            case "remove" -> remove(args);
            case "list" -> list();
            case "clear" -> clear();
            default -> printUsage();
        }
    }

    private static void add(String[] args) {
        if (args.length != 3) {
            CommandManager.send("Usage: " + ADD_USAGE);
            return;
        }

        String moduleName = args[1];
        String keyName = args[2];

        ModuleManager moduleManager = Lucid.getInstance().getModuleManager();
        Module module = moduleManager.get(moduleName);

        if (module == null) {
            CommandManager.send("Module not found: " + moduleName);
            return;
        }

        if (module.isBound()) {
            String oldBind = KeyNames.of(module.getKey(), module.getMouseButton());
            CommandManager.send("Module " + module.getName() + " already has bind: " + oldBind);
            return;
        }

        if (!setBind(module, keyName)) {
            CommandManager.send("Invalid key: " + keyName);
            return;
        }

        String newBind = KeyNames.of(module.getKey(), module.getMouseButton());
        CommandManager.send("Added bind " + newBind + " to " + module.getName());
    }

    private static void change(String[] args) {
        // .bind change bind <module> <key>
        if (args.length != 4 || !args[1].equalsIgnoreCase("bind")) {
            CommandManager.send("Usage: " + CHANGE_USAGE);
            return;
        }

        String moduleName = args[2];
        String keyName = args[3];

        ModuleManager moduleManager = Lucid.getInstance().getModuleManager();
        Module module = moduleManager.get(moduleName);

        if (module == null) {
            CommandManager.send("Module not found: " + moduleName);
            return;
        }

        String oldBind = KeyNames.of(module.getKey(), module.getMouseButton());
        
        if (!setBind(module, keyName)) {
            CommandManager.send("Invalid key: " + keyName);
            return;
        }

        String newBind = KeyNames.of(module.getKey(), module.getMouseButton());
        
        if (oldBind != null) {
            CommandManager.send("Changed " + module.getName() + " bind from " + oldBind + " to " + newBind);
        } else {
            CommandManager.send("Set " + module.getName() + " bind to " + newBind);
        }
    }

    private static void remove(String[] args) {
        if (args.length != 2) {
            CommandManager.send("Usage: " + REMOVE_USAGE);
            return;
        }

        String moduleName = args[1];
        ModuleManager moduleManager = Lucid.getInstance().getModuleManager();
        Module module = moduleManager.get(moduleName);

        if (module == null) {
            CommandManager.send("Module not found: " + moduleName);
            return;
        }

        if (!module.isBound()) {
            CommandManager.send("Module " + module.getName() + " has no bind");
            return;
        }

        String oldBind = KeyNames.of(module.getKey(), module.getMouseButton());
        module.clearBind();
        CommandManager.send("Removed bind " + oldBind + " from " + module.getName());
    }

    private static void list() {
        ModuleManager moduleManager = Lucid.getInstance().getModuleManager();
        List<String> bound = new ArrayList<>();

        for (Module module : moduleManager.getModules()) {
            if (module.isBound()) {
                String bind = KeyNames.of(module.getKey(), module.getMouseButton());
                bound.add(module.getName() + ": " + bind);
            }
        }

        if (bound.isEmpty()) {
            CommandManager.send("No modules have binds");
            return;
        }

        CommandManager.send("Module binds (" + bound.size() + "):");
        for (String entry : bound) {
            CommandManager.send("  " + entry);
        }
    }

    private static void clear() {
        ModuleManager moduleManager = Lucid.getInstance().getModuleManager();
        int count = 0;

        for (Module module : moduleManager.getModules()) {
            if (module.isBound()) {
                module.clearBind();
                count++;
            }
        }

        CommandManager.send("Cleared " + count + " module binds");
    }

    /**
     * Устанавливает бинд для модуля, пытаясь распарсить как клавишу или кнопку мыши.
     * @return true если бинд успешно установлен, false если ключ невалидный
     */
    private static boolean setBind(Module module, String keyName) {
        // Сначала пробуем как клавишу
        int key = KeyNames.parseKey(keyName);
        if (key != -1) {
            module.bindKey(key);
            return true;
        }

        // Потом как кнопку мыши
        int mouseButton = KeyNames.parseMouse(keyName);
        if (mouseButton != -1) {
            module.bindMouse(mouseButton);
            return true;
        }

        return false;
    }

    private static void printUsage() {
        CommandManager.send(ADD_USAGE + " - add a keybind to module");
        CommandManager.send(CHANGE_USAGE + " - change module keybind");
        CommandManager.send(REMOVE_USAGE + " - remove module keybind");
        CommandManager.send(".bind list - show all bound modules");
        CommandManager.send(".bind clear - remove all module keybinds");
    }

    @Override
    public List<String> suggest(String rest) {
        int space = rest.indexOf(' ');
        
        // Первый аргумент - сабкоманда
        if (space < 0) return SUBCOMMANDS;

        String sub = rest.substring(0, space).toLowerCase(java.util.Locale.ROOT);
        String remaining = rest.substring(space + 1).stripLeading();
        int space2 = remaining.indexOf(' ');

        ModuleManager moduleManager = Lucid.getInstance().getModuleManager();

        // .bind change bind <module> <key>
        if (sub.equals("change")) {
            if (space2 < 0) {
                return List.of("bind");
            }
            String changeArg = remaining.substring(0, space2);
            if (changeArg.equalsIgnoreCase("bind")) {
                String remaining2 = remaining.substring(space2 + 1).stripLeading();
                int space3 = remaining2.indexOf(' ');
                
                // Предлагаем имена модулей
                if (space3 < 0) {
                    List<String> moduleNames = new ArrayList<>();
                    for (Module module : moduleManager.getModules()) {
                        moduleNames.add(module.getName());
                    }
                    return moduleNames;
                }
            }
            return List.of();
        }

        // .bind add <module> <key>
        if (sub.equals("add")) {
            // Предлагаем имена модулей
            if (space2 < 0) {
                List<String> moduleNames = new ArrayList<>();
                for (Module module : moduleManager.getModules()) {
                    moduleNames.add(module.getName());
                }
                return moduleNames;
            }
            return List.of();
        }

        // .bind remove <module>
        if (sub.equals("remove")) {
            List<String> boundModules = new ArrayList<>();
            for (Module module : moduleManager.getModules()) {
                if (module.isBound()) {
                    boundModules.add(module.getName());
                }
            }
            return boundModules;
        }

        return List.of();
    }

    @Override
    public String getUsageHint(String rest) {
        int space = rest.indexOf(' ');
        String sub = space < 0 ? rest : rest.substring(0, space);
        
        if (sub.equalsIgnoreCase("add")) return ADD_USAGE;
        if (sub.equalsIgnoreCase("change")) return CHANGE_USAGE;
        if (sub.equalsIgnoreCase("remove")) return REMOVE_USAGE;
        
        return null;
    }
}
