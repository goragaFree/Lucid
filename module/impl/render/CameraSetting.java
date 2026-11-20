package dev.lucid.module.impl.render;

import dev.lucid.event.Priority;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.event.impl.MouseRotationEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BindSetting;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.util.camera.ZoomController;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Independent held camera controls, plus optional third-person wall clipping. */
public final class CameraSetting extends Module {
    private final ZoomController zoom = new ZoomController();
    private final BindSetting lookBind = new BindSetting("Обзор");
    private final BindSetting zoomBind = new BindSetting("Зум");
    private final BooleanSetting cameraClip = new BooleanSetting("CameraClip", false);
    private CameraType previousView;
    private CameraType orbitView;
    private LocalPlayer owner;
    private Level ownerLevel;
    private float cameraYaw;
    private float cameraPitch;

    public CameraSetting() {
        super("Camera Setting", "Free look, held zoom and third-person camera clipping", Category.RENDER);
        addSettings(lookBind, zoomBind, cameraClip);
    }

    @Subscribe
    private void onClientTick(ClientTickEvent event) {
        // Unlike Module.onTick(), this also runs after leaving a world.
        if (event.getPhase() == ClientTickEvent.Phase.START) refresh();
    }

    @Subscribe(priority = Priority.HIGH)
    private void onMouseRotation(MouseRotationEvent event) {
        // Check input every mouse update, not only at the 20 Hz tick rate.
        refresh();
        // Slow rotation at high magnification; do not change the user's sensitivity option.
        // The current rendered FOV also makes mouse speed ease back on zoom release.
        double mouseScale = zoom.getMouseScale();
        event.setCursorDeltaX(event.getCursorDeltaX() * mouseScale);
        event.setCursorDeltaY(event.getCursorDeltaY() * mouseScale);
        if (!isLooking()) return;
        // The existing event already includes sensitivity, smoothing and inversion.
        // 0.15 is the remaining vanilla Entity.turn input-to-degree conversion.
        cameraYaw = (float) Mth.wrapDegrees(cameraYaw + event.getCursorDeltaX() * 0.15);
        cameraPitch = (float) Mth.clamp(cameraPitch + event.getCursorDeltaY() * 0.15, -90.0, 90.0);
        event.cancel();
    }

    private void refresh() {
        if (!canUseBinds()) {
            finish();
            zoom.reset();
            return;
        }
        zoom.updateInput(isZooming(), mc.options.fov().get());
        if (!lookBind.isDown(mc.getWindow().handle())) {
            finish();
            return;
        }
        if (owner != null && (owner != mc.player || ownerLevel != mc.level)) finish();
        if (owner == null) {
            // Snapshot exactly once per hold; repeated presses cannot overwrite it.
            previousView = mc.options.getCameraType();
            orbitView = previousView.isFirstPerson() ? CameraType.THIRD_PERSON_BACK : previousView;
            owner = mc.player;
            ownerLevel = mc.level;
            cameraYaw = owner.getYRot();
            cameraPitch = owner.getXRot();
        }
        mc.options.setCameraType(orbitView);
    }

    private void finish() {
        if (previousView != null) mc.options.setCameraType(previousView);
        previousView = null;
        orbitView = null;
        owner = null;
        ownerLevel = null;
    }

    @Override
    protected void onDisable() {
        finish();
        zoom.reset();
    }

    public boolean isLooking() {
        return isEnabled() && owner != null && owner == mc.player && ownerLevel == mc.level;
    }

    private boolean canUseBinds() {
        return isEnabled() && mc.player != null && mc.level != null
                && mc.screen == null && mc.isWindowActive()
                && mc.getCameraEntity() == mc.player && !mc.player.isSleeping();
    }

    public boolean isZooming() {
        // Read the bind at render time: no latched state and no FOV option to restore.
        return canUseBinds() && zoomBind.isDown(mc.getWindow().handle());
    }

    public float applyZoom(float vanillaFov) {
        if (!canUseBinds()) {
            zoom.reset();
            return vanillaFov;
        }
        zoom.updateInput(isZooming(), mc.options.fov().get());
        // Run on release too: otherwise the outgoing animation would be cut off.
        return zoom.apply(vanillaFov, System.nanoTime());
    }

    public boolean onZoomScroll(double vertical) {
        if (!isZooming()) return false;
        // A wheel event may precede the first rendered frame after pressing the bind.
        zoom.updateInput(true, mc.options.fov().get());
        return zoom.scroll(vertical);
    }

    public boolean isClipEnabled() {
        // Clip remains effective with an inventory/GUI open, unlike held controls.
        return isEnabled() && cameraClip.getValue() && mc.player != null && mc.level != null
                && mc.getCameraEntity() == mc.player && !mc.player.isSleeping();
    }

    public BindSetting getZoomBind() { return zoomBind; }
    public BooleanSetting getCameraClip() { return cameraClip; }

    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }
    public BindSetting getLookBind() { return lookBind; }
}
