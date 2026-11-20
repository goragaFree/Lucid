package dev.lucid.util.movement;

import dev.lucid.util.Wrapper;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Client-thread movement helpers for Mojang mappings 1.21.11.
 * Speeds are blocks per tick, directions are radians unless named Degrees.
 * Uses ClientInput.getMoveVector(): X = left/right, Y = forward/backward.
 * Does not teleport, send packets, change rotation or bypass vanilla physics.
 */
public final class MoveUtil implements Wrapper {
    private static final double INPUT_EPSILON = 1.0E-8;

    private MoveUtil() {}

    /** Last input sampled by Minecraft. Safe in menus; not a raw GLFW poll. */
    public static Vec2 getInput() {
        return mc.player == null || mc.player.input == null
                ? Vec2.ZERO : mc.player.input.getMoveVector();
    }

    /** Movement intention, not actual velocity (knockback/ice can move an idle player). */
    public static boolean isMoving() {
        Vec2 input = getInput();
        return Math.hypot(input.x, input.y) > INPUT_EPSILON;
    }

    /** Positive = forward, negative = backward. Diagonals may be normalized. */
    public static float getForwardInput() { return getInput().y; }

    /** Positive = left, negative = right. */
    public static float getStrafeInput() { return getInput().x; }

    /** Current horizontal velocity magnitude, in blocks/tick; ignores Y. */
    public static double getSpeed() {
        if (mc.player == null) return 0.0;
        Vec3 velocity = mc.player.getDeltaMovement();
        return Math.hypot(velocity.x, velocity.z);
    }

    /** Nominal blocks/second at 20 TPS, not a measured wall-clock speed. */
    public static double getSpeedBps() { return getSpeed() * 20.0; }

    /** Current input direction in radians; without input returns the player's yaw. */
    public static double getDirection() {
        Vec2 input = getInput();
        return getDirection(mc.player == null ? 0.0f : mc.player.getYRot(), input.y, input.x);
    }

    public static double getDirectionDegrees() { return Math.toDegrees(getDirection()); }

    /** Pure calculation. Positive strafe means LEFT. Zero input means yaw itself. */
    public static double getDirection(float yaw, float forward, float strafe) {
        requireFinite(yaw, "yaw");
        requireFinite(forward, "forward");
        requireFinite(strafe, "strafe");
        double angle = Math.toRadians(yaw);
        if (Math.hypot(forward, strafe) > INPUT_EPSILON) {
            angle -= Math.atan2(strafe, forward);
        }
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    /**
     * Pure horizontal vector calculation. Normalizes input so diagonals are not faster.
     * A nonzero input sets the requested speed, regardless of its analog magnitude.
     * Zero input returns Vec3.ZERO. Y is always zero.
     */
    public static Vec3 getVelocity(double speed, float yaw, float forward, float strafe) {
        requireSpeed(speed);
        requireFinite(yaw, "yaw");
        requireFinite(forward, "forward");
        requireFinite(strafe, "strafe");
        double length = Math.hypot(forward, strafe);
        if (length <= INPUT_EPSILON || speed == 0.0) return Vec3.ZERO;
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double normalizedForward = forward / length;
        double normalizedStrafe = strafe / length;
        return new Vec3(
                (normalizedStrafe * cos - normalizedForward * sin) * speed,
                0.0,
                (normalizedForward * cos + normalizedStrafe * sin) * speed
        );
    }

    /** Redirect existing horizontal velocity along input, preserving its magnitude and Y. */
    public static boolean strafe() { return strafe(getSpeed()); }

    /** Set horizontal speed along current input and yaw, preserving vertical velocity. */
    public static boolean strafe(double speed) {
        requireSpeed(speed);
        return mc.player != null && strafe(speed, mc.player.getYRot());
    }

    /**
     * Input-relative strafe using an explicit yaw, without changing the player's rotation.
     * Returns false and leaves velocity untouched without a world/player or movement input.
     * speed=0 stops X/Z when input is present; use stop() to stop regardless of input.
     */
    public static boolean strafe(double speed, float yaw) {
        requireSpeed(speed);
        requireFinite(yaw, "yaw");
        if (mc.player == null || mc.level == null) return false;
        Vec2 input = getInput();
        if (Math.hypot(input.x, input.y) <= INPUT_EPSILON) return false;
        Vec3 velocity = getVelocity(speed, yaw, input.y, input.x);
        mc.player.setDeltaMovement(velocity.x, mc.player.getDeltaMovement().y, velocity.z);
        return true;
    }

    /** Alias for strafe(speed). */
    public static boolean setSpeed(double speed) { return strafe(speed); }

    /** Stops X/Z only, including without movement input. Does not cancel falling/jumping. */
    public static boolean stop() {
        if (mc.player == null || mc.level == null) return false;
        mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
        return true;
    }

    /** Input-relative movement yaw in DEGREES, wrapped to [-180, 180). */
    public static float moveYaw(float yaw) {
        Vec2 input = getInput();
        return (float) wrapDegrees(Math.toDegrees(getDirection(yaw, input.y, input.x)));
    }

    /**
     * Signed horizontal angle from yaw to a RELATIVE position, in degrees [-180, 180).
     * Positive = right, negative = left. Zero horizontal vector returns zero.
     * Does not subtract the player's position: pass target.subtract(player.position()).
     */
    public static double getDegreesRelativeToView(Vec3 relativePosition, float yaw) {
        Objects.requireNonNull(relativePosition, "relativePosition");
        requireFinite(relativePosition.x, "relativePosition.x");
        requireFinite(relativePosition.z, "relativePosition.z");
        requireFinite(yaw, "yaw");
        if (Math.hypot(relativePosition.x, relativePosition.z) <= INPUT_EPSILON) return 0.0;
        double targetYaw = Math.toDegrees(Math.atan2(-relativePosition.x, relativePosition.z));
        return wrapDegrees(targetYaw - yaw);
    }

    /** Ray-based ground distance below the FEET CENTER, searching at most 256 blocks. */
    public static double getDistanceToGround() { return getDistanceToGround(256.0); }

    /**
     * Vertical distance to a block collision shape below the player's center.
     * Accounts for slabs and other collision shapes, ignores fluids and entities.
     * Stops at the world's minimum Y, including negative heights.
     * Returns POSITIVE_INFINITY on a miss, without a player/world or outside the range.
     * This is NOT a whole-bounding-box support test; it can miss a ledge under one edge.
     */
    public static double getDistanceToGround(double maxDistance) {
        requireFinite(maxDistance, "maxDistance");
        if (maxDistance < 0.0) throw new IllegalArgumentException("maxDistance must be non-negative");
        if (mc.player == null || mc.level == null) return Double.POSITIVE_INFINITY;
        double feetY = mc.player.getY();
        double epsilon = 1.0E-4;
        double bottomY = Math.max(mc.level.getMinY(), feetY - maxDistance - epsilon);
        if (feetY + epsilon <= bottomY) return Double.POSITIVE_INFINITY;
        Vec3 from = new Vec3(mc.player.getX(), feetY + epsilon, mc.player.getZ());
        Vec3 to = new Vec3(from.x, bottomY, from.z);
        BlockHitResult hit = mc.level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return Double.POSITIVE_INFINITY;
        double distance = Math.max(0.0, feetY - hit.getLocation().y);
        return distance <= maxDistance ? distance : Double.POSITIVE_INFINITY;
    }

    /** Default axis dead angle: 20 degrees. Does not mutate the supplied record. */
    public static Input getDirectionalInputForDegrees(Input input, double degrees) {
        return getDirectionalInputForDegrees(input, degrees, 20.0f);
    }

    /**
     * Replaces ONLY directional buttons based on a relative angle: 0=forward,
     * +90=right, -90=left, +/-180=backward. Preserves jump, shift and sprint.
     * deadAngle must be in [0, 45); boundaries exclude the near-zero axis so
     * opposite buttons are never both pressed, including when deadAngle is zero.
     * Returns a new input record; applying it to a movement pipeline is the caller's job.
     */
    public static Input getDirectionalInputForDegrees(Input input, double degrees, float deadAngle) {
        Objects.requireNonNull(input, "input");
        requireFinite(degrees, "degrees");
        requireFinite(deadAngle, "deadAngle");
        if (deadAngle < 0.0f || deadAngle >= 45.0f) {
            throw new IllegalArgumentException("deadAngle must be in [0, 45)");
        }
        double angle = wrapDegrees(degrees);
        double absolute = Math.abs(angle);
        boolean forward = absolute < 90.0 - deadAngle;
        boolean backward = absolute > 90.0 + deadAngle;
        boolean right = angle > deadAngle && angle < 180.0 - deadAngle;
        boolean left = angle < -deadAngle && angle > -180.0 + deadAngle;
        return new Input(forward, backward, left, right, input.jump(), input.shift(), input.sprint());
    }

    private static double wrapDegrees(double degrees) {
        double result = degrees % 360.0;
        if (result >= 180.0) result -= 360.0;
        if (result < -180.0) result += 360.0;
        return result;
    }

    private static void requireSpeed(double speed) {
        requireFinite(speed, "speed");
        if (speed < 0.0) throw new IllegalArgumentException("speed must be non-negative");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
