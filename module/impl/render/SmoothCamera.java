package dev.lucid.module.impl.render;

import dev.lucid.Lucid;
import dev.lucid.event.Priority;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.event.impl.MouseRotationEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.camera.DampedMouseMotion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

/** Optional mouse inertia, independent of Camera Setting's optical zoom sensitivity. */
public final class SmoothCamera extends Module {
    private final SliderSetting smoothness = new SliderSetting("Плавность", 1, 1, 50, 1);
    private final DampedMouseMotion motion = new DampedMouseMotion();
    private LocalPlayer inputOwner;
    private Level inputLevel;
    private boolean orbitInput;

    public SmoothCamera() {
        super("SmoothCamera", "Smooths mouse rotation with adjustable inertia", Category.RENDER);
        addSettings(smoothness);
    }

    @Override
    protected void onEnable() {
        clearInput();
    }

    @Override
    protected void onDisable() {
        clearInput();
    }

    @Subscribe
    private void onClientTick(ClientTickEvent event) {
        if (event.getPhase() == ClientTickEvent.Phase.START) syncInputContext();
    }

    @Subscribe(priority = Priority.HIGHEST)
    private void onMouseRotation(MouseRotationEvent event) {
        if (!syncInputContext()) return;
        // HIGHEST precedes CameraSetting's HIGH: smooth first, then scale zoom
        // sensitivity and finally send the result to the player or free-look camera.
        DampedMouseMotion.Motion delta = motion.sample(event.getCursorDeltaX(),
                event.getCursorDeltaY(), smoothness.getValue(), System.nanoTime());
        event.setCursorDeltaX(delta.x());
        event.setCursorDeltaY(delta.y());
    }

    private boolean syncInputContext() {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.screen != null
                || !mc.isWindowActive() || mc.getCameraEntity() != mc.player || mc.player.isSleeping()) {
            clearInput();
            return false;
        }
        CameraSetting camera = Lucid.getInstance().getModuleManager().get(CameraSetting.class);
        // Query the held bind directly: CameraSetting refreshes later in this event.
        boolean orbit = camera != null && camera.isEnabled()
                && camera.getLookBind().isDown(mc.getWindow().handle());
        if (inputOwner != mc.player || inputLevel != mc.level || orbit != orbitInput) {
            motion.reset();
        }
        inputOwner = mc.player;
        inputLevel = mc.level;
        orbitInput = orbit;
        return true;
    }

    private void clearInput() {
        motion.reset();
        inputOwner = null;
        inputLevel = null;
        orbitInput = false;
    }

    public SliderSetting getSmoothness() { return smoothness; }
}
