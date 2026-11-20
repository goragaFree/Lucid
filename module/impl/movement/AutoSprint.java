package dev.lucid.module.impl.movement;

import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;

/** Держит клавишу спринта зажатой — игра сама решает, можно ли бежать (еда, присед и т.д.). */
public class AutoSprint extends Module {

    public AutoSprint() {
        super("AutoSprint", "Sprints automatically", Category.MOVEMENT);
    }

    @Subscribe
    private void onClientTick(ClientTickEvent event) {
        if (isEnabled() && event.getPhase() == ClientTickEvent.Phase.END && mc.player != null) {
            mc.options.keySprint.setDown(true);
        }
    }

    @Override
    protected void onDisable() {
        mc.options.keySprint.setDown(false);
    }
}
