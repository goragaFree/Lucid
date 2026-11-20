package dev.lucid.util.camera;

/** A time-normalized first-order follower for accumulated mouse displacement. */
public final class DampedMouseMotion {
    public record Motion(double x, double y) {}

    private double goalX;
    private double goalY;
    private double positionX;
    private double positionY;
    private long previousNanos;
    private boolean initialized;

    public Motion sample(double inputX, double inputY, double strength, long nowNanos) {
        if (!Double.isFinite(inputX) || !Double.isFinite(inputY)) {
            throw new IllegalArgumentException("Mouse motion must be finite");
        }
        if (!Double.isFinite(strength) || strength <= 1.0) {
            reset();
            return new Motion(inputX, inputY);
        }
        double seconds = 1.0 / 60.0;
        if (initialized) {
            long elapsed = nowNanos - previousNanos;
            if (elapsed < 0L || elapsed > 250_000_000L) {
                // A suspended render loop must not replay old accumulated movement.
                reset();
            } else {
                seconds = elapsed / 1_000_000_000.0;
            }
        }
        initialized = true;
        previousNanos = nowNanos;
        double amount = Math.min(50.0, strength);
        // At 60 Hz the follower moves 1/strength of the remaining distance.
        // The exponent gives the same response over elapsed time at other frame rates.
        double gain = -Math.expm1(Math.log1p(-1.0 / amount) * seconds * 60.0);
        goalX += inputX;
        goalY += inputY;
        double nextX = positionX + (goalX - positionX) * gain;
        double nextY = positionY + (goalY - positionY) * gain;
        if (Math.abs(goalX - nextX) < 1.0E-8) nextX = goalX;
        if (Math.abs(goalY - nextY) < 1.0E-8) nextY = goalY;
        Motion output = new Motion(nextX - positionX, nextY - positionY);
        positionX = nextX;
        positionY = nextY;
        // Keep the virtual coordinates small during long play sessions.
        if (Math.abs(positionX) > 1_000_000.0 || Math.abs(positionY) > 1_000_000.0) {
            goalX -= positionX;
            goalY -= positionY;
            positionX = 0.0;
            positionY = 0.0;
        }
        return output;
    }

    public void reset() {
        goalX = goalY = positionX = positionY = 0.0;
        previousNanos = 0L;
        initialized = false;
    }
}
