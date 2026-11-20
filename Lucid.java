package dev.lucid;

import dev.lucid.command.CommandManager;
import dev.lucid.sreen.clickgui.ClickGuiScreen;
import dev.lucid.module.ModuleManager;
import dev.lucid.event.EventBus;
import dev.lucid.event.Priority;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.KeyInputEvent;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.module.impl.combat.AimAssist;
import dev.lucid.module.impl.render.BlockOverlay;
import dev.lucid.module.impl.render.EspOverlay;
import dev.lucid.module.impl.render.Arrows;
import dev.lucid.module.impl.render.NameTags;
import dev.lucid.module.impl.render.TimerTnt;
import dev.lucid.module.impl.render.Hud;
import dev.lucid.util.config.ConfigManager;
import dev.lucid.util.Wrapper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главный класс клиента. Единственная точка входа (client entrypoint).
 */
public final class Lucid implements ClientModInitializer, Wrapper {

    public static final String MOD_ID = "lucid";
    public static final String NAME = "Lucid";
    public static final String VERSION = "1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static Lucid instance;

    private final EventBus eventBus = new EventBus((event, failure) ->
            LOGGER.error("Event listener failed: " + event.getClass().getSimpleName(), failure));
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private ClickGuiScreen clickGui;

    @Override
    public void onInitializeClient() {
        instance = this;
        eventBus.subscribe(this);

        moduleManager = new ModuleManager();
        moduleManager.init();
        eventBus.subscribe(moduleManager);

        // Команды живут отдельно от модулей: у них свой реестр и свой префикс ".",
        // инициализируем до загрузки конфига, чтобы ответы команд были доступны сразу
        CommandManager.init();

        // Конфиг грузится после регистрации модулей — ему нужно, к чему применять значения
        configManager = new ConfigManager(moduleManager);
        configManager.load();

        registerHud();

        ClientTickEvents.START_CLIENT_TICK.register(client ->
                eventBus.post(new ClientTickEvent(ClientTickEvent.Phase.START)));
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                eventBus.post(new ClientTickEvent(ClientTickEvent.Phase.END)));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> configManager.save());

        LOGGER.info("{} {} loaded, modules: {}", NAME, VERSION, moduleManager.getModules().size());

    }

    /** Оба оверлея рисуются через Fabric, без события в EventBus. */
    private void registerHud() {
        // Порядок важен: addLast кладёт элементы в конец списка, поэтому ESP
        // регистрируется раньше HUD — панели тогда перекрывают каркасы, а не наоборот.
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "esp"),
                moduleManager.get(EspOverlay.class)
        );
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "block_overlay"),
                moduleManager.get(BlockOverlay.class)
        );
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "nametags"),
                moduleManager.get(NameTags.class)
        );
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "tnt_timer"),
                moduleManager.get(TimerTnt.class)
        );
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "arrows"),
                moduleManager.get(Arrows.class)
        );
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "aim_assist"),
                moduleManager.get(AimAssist.class)
        );
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                moduleManager.get(Hud.class)
        );
    }

    /** Right Shift belongs to the GUI; higher-priority listeners may cancel it. */
    @Subscribe(priority = Priority.LOW)
    private void onGuiKey(KeyInputEvent event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_RIGHT_SHIFT) return;
        if (mc.screen instanceof ClickGuiScreen gui) {
            // A key being assigned belongs to the bind editor, even Right Shift.
            if (gui.isBinding()) return;
            mc.setScreen(null);
            event.cancel();
        } else if (mc.screen == null) {
            mc.setScreen(getClickGui());
            event.cancel();
        }
    }

    public static Lucid getInstance() {
        return instance;
    }

    /** Один экземпляр на всю игру — чтобы GUI запоминал позиции колонок. */
    public ClickGuiScreen getClickGui() {
        if (clickGui == null) clickGui = new ClickGuiScreen();
        return clickGui;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
