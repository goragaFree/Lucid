package dev.lucid.util.camera;

/** Render-time FOV animation and a remembered, wheel-adjustable held zoom. */
public final class ZoomController {
    private static final double MIN_FOV = 10.0;
    private static final double WHEEL_DEGREES = 10.0;
    private static final double RESPONSE = 12.0;

    private double rememberedFov = 30.0;
    private double selectedFov = 30.0;
    private double normalFov = 70.0;
    private double animatedFov;
    private double lastVanillaFov;
    private long previousNanos;
    private boolean initialized;
    private boolean held;
    private boolean transitioning;

    public void updateInput(boolean down, float configuredFov) {
        normalFov = Math.max(MIN_FOV, configuredFov);
        if (down && !held) {
            // Reuse the last wheel choice, but always start visibly below normal FOV.
            selectedFov = clamp(Math.min(rememberedFov, normalFov - 20.0));
            transitioning = true;
        } else if (!down && held) {
            rememberedFov = selectedFov;
            transitioning = true;
        }
        held = down;
        if (held) selectedFov = clamp(selectedFov);
    }

    /** Called only for wheel input belonging to held zoom. Fractional wheels work too. */
    public boolean scroll(double vertical) {
        if (!held || !Double.isFinite(vertical) || vertical == 0.0) return false;
        // Bound the delta before multiplication, including absurd external input values.
        double safeDelta = Math.max(-1000.0, Math.min(1000.0, vertical));
        selectedFov = clamp(selectedFov - safeDelta * WHEEL_DEGREES);
        return true;
    }

    public float apply(float vanillaFov, long nowNanos) {
        lastVanillaFov = vanillaFov;
        if (!initialized) {
            // Initialize from the actual view, never from a hardcoded zoomed value.
            animatedFov = vanillaFov;
            previousNanos = nowNanos;
            initialized = true;
        }
        double seconds = Math.max(0.0, Math.min(0.25, (nowNanos - previousNanos) / 1_000_000_000.0));
        previousNanos = nowNanos;
        if (!held && !transitioning) {
            // Once the release animation ends, leave vanilla FOV effects untouched.
            animatedFov = vanillaFov;
            return vanillaFov;
        }
        double target = held ? selectedFov : vanillaFov;
        double blend = -Math.expm1(-RESPONSE * seconds);
        animatedFov += (target - animatedFov) * blend;
        if (Math.abs(target - animatedFov) < 0.001) {
            animatedFov = target;
            if (!held) transitioning = false;
        }
        return (float) animatedFov;
    }

    /** Mouse speed follows the visible optical magnification, including release easing. */
    public double getMouseScale() {
        if (!initialized || (!held && !transitioning)) return 1.0;
        // Projected movement depends on tan(FOV/2), not only the degree ratio.
        // At FOV 10 versus 70 this yields about 0.125: roughly eight times slower.
        double visible = Math.max(1.0, Math.min(179.0, animatedFov));
        double normal = Math.max(1.0, Math.min(179.0, lastVanillaFov));
        double scale = Math.tan(Math.toRadians(visible) * 0.5)
                / Math.tan(Math.toRadians(normal) * 0.5);
        return Math.max(0.01, Math.min(1.0, scale));
    }

    public void reset() {
        if (held) rememberedFov = selectedFov;
        held = false;
        transitioning = false;
        initialized = false;
        previousNanos = 0L;
        // The selected level is remembered for this game session, not saved in config.
    }

    private double clamp(double fov) {
        return Math.max(MIN_FOV, Math.min(normalFov, fov));
    }

    public float getSelectedFov() { return (float) selectedFov; }
    public float getRememberedFov() { return (float) rememberedFov; }
}
