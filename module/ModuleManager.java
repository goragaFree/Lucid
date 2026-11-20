package dev.lucid.module;

import dev.lucid.event.Priority;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.KeyInputEvent;
import dev.lucid.module.impl.combat.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import dev.lucid.module.impl.misc.ChatSetting;
import dev.lucid.module.impl.misc.ClickFriend;
import dev.lucid.module.impl.movement.AirStuck;
import dev.lucid.module.impl.movement.AutoSprint;
import dev.lucid.module.impl.movement.Flight;
import dev.lucid.module.impl.movement.InventoryMove;
import dev.lucid.module.impl.player.ItemScroller;
import dev.lucid.module.impl.player.LockSlot;
import dev.lucid.module.impl.player.NameProtect;
import dev.lucid.module.impl.player.NoEntityTrace;

import dev.lucid.module.impl.render.Ambience;
import dev.lucid.module.impl.render.Arrows;
import dev.lucid.module.impl.render.BlockOverlay;
import dev.lucid.module.impl.render.EspOverlay;
import dev.lucid.module.impl.render.Gamma;
import dev.lucid.module.impl.render.CameraSetting;
import dev.lucid.module.impl.render.FreeCam;
import dev.lucid.module.impl.render.SmoothCamera;
import dev.lucid.module.impl.render.Hud;
import dev.lucid.module.impl.render.ItemPhysic;
import dev.lucid.module.impl.render.NameTags;
import dev.lucid.module.impl.render.TimerTnt;
import dev.lucid.module.impl.render.ViewModel;
import dev.lucid.module.impl.render.NoRender;
import dev.lucid.module.impl.render.Predictions;
import dev.lucid.module.impl.render.Saturation;
import dev.lucid.module.impl.render.SeeInvisible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public void init() {
        // Combat
        register(new TriggerBot());
        register(new AimAssist());
        register(new AutoSwap());
        register(new AutoTotem());
        register(new NoFriendDamage());
        register(new TapeMouse());
        register(new NoInteract());

        // Movement
        register(new AirStuck());
        register(new AutoSprint());
        register(new Flight());
        register(new InventoryMove());
        // Player
        register(new ItemScroller());
        register(new LockSlot());
        register(new NameProtect());
        register(new NoEntityTrace());
        // Render
        register(new Ambience());
        register(new Gamma());
        register(new Saturation());
        register(new CameraSetting());
        register(new FreeCam());
        register(new SmoothCamera());
        register(new SeeInvisible());
        register(new EspOverlay());
        register(new BlockOverlay());
        register(new NameTags());
        register(new TimerTnt());
        register(new ViewModel());
        register(new ItemPhysic());
        register(new Arrows());
        register(new NoRender());
        register(new Predictions());
        register(new Hud());
        // Misc
        register(new ChatSetting());
        register(new ClickFriend());
    }

    private void register(Module module) {
        modules.add(module);
    }

    @Subscribe(priority = Priority.LOWEST)
    private void onKeyInput(KeyInputEvent event) {
        if (event.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) {
            onKeyPressed(event.getKey());
        }
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    /**
     * Переключает модуль, которому принадлежит эта клавиша.
     *
     * <p>Отрицательные коды отсекаются сразу: -1 у модуля значит «бинда нет», и без
     * этой проверки нажатие «ничего» переключало бы все непобинденные модули разом.</p>
     *
     * @return true, если клавиша была чьим-то биндом
     */
    public boolean onKeyPressed(int key) {
        if (key < 0) return false;
        for (Module module : modules) {
            if (module.getKey() == key) {
                module.toggle();
                return true;
            }
        }
        return false;
    }

    /** То же для кнопок мыши. */
    public boolean onMousePressed(int button) {
        if (button < 0) return false;
        for (Module module : modules) {
            if (module.getMouseButton() == button) {
                module.toggle();
                return true;
            }
        }
        return false;
    }

    public List<Module> getModules(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) result.add(module);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> clazz) {
        for (Module module : modules) {
            if (module.getClass() == clazz) return (T) module;
        }
        return null;
    }

    public Module get(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) return module;
        }
        return null;
    }
}
