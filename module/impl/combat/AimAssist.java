package dev.lucid.module.impl.combat;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.math.MathUtil;
import dev.lucid.util.players.friend.FriendManager;
import dev.lucid.util.render.Render2D;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Frame-rate-independent assistance towards the nearest part of a target's hitbox. */
public class AimAssist extends Module implements HudElement {
    private static final String MODE_FOV = "FOV";
    private static final String PLAYERS = "Players";
    private static final String ANIMALS = "Animals";
    private static final String MOBS = "Mobs";
    private static final String NAKED = "Naked";
    private static final String FRIENDS = "Friends";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int CIRCLE_COLOR = 0xE6808080;
    private static final float CIRCLE_THICKNESS = 2.0F;
    private static final float DEAD_ZONE_DEGREES = 0.01F;
    private static final double LOCK_FOV_MULTIPLIER = 1.15;
    private static final long MAX_FRAME_GAP_NANOS = 250_000_000L;
    /** Small safety margin inside the selected stopping region. */
    private static final double HITBOX_INSET = 0.025;
    private static final double SOFT_EDGE_DEGREES = 0.8;
    private static final int CLOSEST_POINT_ITERATIONS = 48;

    private final SelectSetting mode = new SelectSetting("Mode", MODE_FOV, MODE_FOV);
    private final SliderSetting fov = new SliderSetting("FOV", 40.0, 10.0, 90.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Speed", 30.0, 1.0, 100.0, 1.0);
    /** 0 keeps the full hitbox; 100 leaves its central 10 percent on each axis. */
    private final SliderSetting attraction = new SliderSetting("Attraction", 25.0, 0.0, 100.0, 1.0);
    private final SliderSetting range = new SliderSetting("Range", 4.5, 1.0, 6.0, 0.1);
    private final MultiSelectSetting targets = new MultiSelectSetting("Targets",
            List.of(PLAYERS, ANIMALS, MOBS, NAKED, FRIENDS),
            PLAYERS, ANIMALS, MOBS, NAKED, FRIENDS);

    private Entity lockedTarget;
    private long previousAimNanos;

    public AimAssist() {
        super("AimAssist", "Helps aim at targets inside the FOV circle", Category.COMBAT);
        addSettings(mode, fov, speed, attraction, range, targets);
    }

    @Override
    protected void onEnable() {
        resetAimState();
    }

    @Override
    protected void onDisable() {
        resetAimState();
    }

    private void updateAim() {
        if (!canAim()) {
            resetAimState();
            return;
        }
        float partialTick = MathUtil.partialTick();
        Entity target = selectTarget(partialTick);
        if (target == null) {
            previousAimNanos = 0L;
            return;
        }
        double seconds = frameSeconds(System.nanoTime());
        aimAt(target, partialTick, seconds);
    }

    private boolean canAim() {
        return isEnabled()
                && mc.player != null
                && mc.level != null
                && mc.screen == null
                && mc.isWindowActive()
                && mc.getCameraEntity() == mc.player
                && !mc.player.isSleeping()
                && mode.is(MODE_FOV);
    }

    private Entity selectTarget(float partialTick) {
        if (isLockedTargetValid(lockedTarget, partialTick)) return lockedTarget;
        lockedTarget = findTarget(partialTick);
        return lockedTarget;
    }

    private boolean isLockedTargetValid(Entity entity, float partialTick) {
        return isTargetAllowed(entity)
                && isInsideAimArea(entity, partialTick, LOCK_FOV_MULTIPLIER);
    }

    /** FOV is measured to the visible hitbox, rather than to the entity's eyes. */
    private Entity findTarget(float partialTick) {
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3 eyes = mc.player.getEyePosition(partialTick);
        Vec3 look = currentLook();
        double maxRange = range.getValue();
        double maxRangeSq = square(maxRange);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isTargetAllowed(entity)) continue;
            // Keep the original range semantics; do not extend reach by half a hitbox.
            double distSq = eyes.distanceToSqr(entity.getEyePosition(partialTick));
            if (distSq > maxRangeSq || distSq < 1.0E-4) continue;
            AABB box = interpolatedBox(entity, partialTick);
            double angle = hitboxAngle(eyes, look, box, maxRange);
            if (angle > fov.getValue()) continue;
            if (angle < bestAngle) {
                bestAngle = angle;
                best = entity;
            }
        }
        return best;
    }

    private boolean isInsideAimArea(Entity entity, float partialTick, double multiplier) {
        Vec3 eyes = mc.player.getEyePosition(partialTick);
        double distSq = eyes.distanceToSqr(entity.getEyePosition(partialTick));
        double maxRange = range.getValue();
        if (distSq < 1.0E-4 || distSq > square(maxRange)) return false;
        return hitboxAngle(eyes, currentLook(), interpolatedBox(entity, partialTick), maxRange)
                <= fov.getValue() * multiplier;
    }

    private void aimAt(Entity target, float partialTick, double seconds) {
        Vec3 eyes = mc.player.getEyePosition(partialTick);
        Vec3 look = currentLook();
        AABB box = interpolatedBox(target, partialTick);
        double maxRange = range.getValue();

        // Attraction controls how far inside the real hitbox assistance stops.
        // Selection and range still use the original, unshrunk hitbox.
        AABB stoppingBox = attractionBox(box);
        if (rayHitsBox(eyes, look, stoppingBox, maxRange)) return;

        // Aim slightly beyond the stopping boundary to avoid asymptotic edge stalls.
        Vec3 point = nearestPointToRay(eyes, look, insetBox(stoppingBox), maxRange);
        Vec3 delta = point.subtract(eyes);
        if (delta.lengthSqr() < 1.0E-10) return;
        double distXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, distXZ));
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        float yawDiff = Mth.wrapDegrees(targetYaw - yaw);
        float pitchDiff = targetPitch - pitch;
        double error = Math.hypot(yawDiff, pitchDiff);
        if (error < DEAD_ZONE_DEGREES) {
            // We are outside the stopping region; do not leave a subpixel miss here.
            mc.player.setYRot(yaw + yawDiff);
            mc.player.setXRot(Mth.clamp(targetPitch, -90.0F, 90.0F));
            return;
        }

        // Reduce the rotation rate near an edge, inside the time-based smoothing.
        double edgeStrength = 0.25 + 0.75 * Mth.clamp(error / SOFT_EDGE_DEGREES, 0.0, 1.0);
        float factor = smoothingFactor(seconds, edgeStrength);
        mc.player.setYRot(yaw + yawDiff * factor);
        mc.player.setXRot(Mth.clamp(pitch + pitchDiff * factor, -90.0F, 90.0F));
    }

    /** Use actual current rotations, not tick-interpolated rotations already corrected last frame. */
    private Vec3 currentLook() {
        double yaw = Math.toRadians(mc.player.getYRot());
        double pitch = Math.toRadians(mc.player.getXRot());
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    private static AABB interpolatedBox(Entity entity, float partialTick) {
        Vec3 offset = entity.getPosition(partialTick).subtract(entity.position());
        return entity.getBoundingBox().move(offset);
    }

    private AABB attractionBox(AABB box) {
        double amount = Mth.clamp(attraction.getValue() / 100.0, 0.0, 1.0);
        // Never collapse to a single point, even at maximum attraction.
        double fraction = 0.45 * amount;
        double x = (box.maxX - box.minX) * fraction;
        double y = (box.maxY - box.minY) * fraction;
        double z = (box.maxZ - box.minZ) * fraction;
        return new AABB(box.minX + x, box.minY + y, box.minZ + z,
                box.maxX - x, box.maxY - y, box.maxZ - z);
    }

    private static AABB insetBox(AABB box) {
        double x = Math.min(HITBOX_INSET, (box.maxX - box.minX) * 0.1);
        double y = Math.min(HITBOX_INSET, (box.maxY - box.minY) * 0.1);
        double z = Math.min(HITBOX_INSET, (box.maxZ - box.minZ) * 0.1);
        return new AABB(box.minX + x, box.minY + y, box.minZ + z,
                box.maxX - x, box.maxY - y, box.maxZ - z);
    }

    private static boolean rayHitsBox(Vec3 eyes, Vec3 look, AABB box, double distance) {
        return box.contains(eyes) || box.clip(eyes, eyes.add(look.scale(distance))).isPresent();
    }

    private static double hitboxAngle(Vec3 eyes, Vec3 look, AABB box, double distance) {
        if (rayHitsBox(eyes, look, box, distance)) return 0.0;
        return angleBetween(look, nearestPointToRay(eyes, look, box, distance).subtract(eyes));
    }

    /**
     * Squared distance from a point on the view ray to an AABB is convex in ray
     * distance. A bounded ternary search finds the closest ray/box pair without
     * choosing random aim points or switching between sampled body positions.
     */
    private static Vec3 nearestPointToRay(Vec3 eyes, Vec3 look, AABB box, double distance) {
        double low = 0.0;
        double high = distance;
        for (int i = 0; i < CLOSEST_POINT_ITERATIONS; i++) {
            double left = low + (high - low) / 3.0;
            double right = high - (high - low) / 3.0;
            if (distanceToBoxSq(eyes, look, left, box) <= distanceToBoxSq(eyes, look, right, box)) {
                high = right;
            } else {
                low = left;
            }
        }
        double t = (low + high) * 0.5;
        return new Vec3(
                Mth.clamp(eyes.x + look.x * t, box.minX, box.maxX),
                Mth.clamp(eyes.y + look.y * t, box.minY, box.maxY),
                Mth.clamp(eyes.z + look.z * t, box.minZ, box.maxZ));
    }

    private static double distanceToBoxSq(Vec3 eyes, Vec3 look, double t, AABB box) {
        double x = eyes.x + look.x * t;
        double y = eyes.y + look.y * t;
        double z = eyes.z + look.z * t;
        double dx = x - Mth.clamp(x, box.minX, box.maxX);
        double dy = y - Mth.clamp(y, box.minY, box.maxY);
        double dz = z - Mth.clamp(z, box.minZ, box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private float smoothingFactor(double seconds, double strength) {
        double tickFactor = Mth.clamp(speed.getValue() / 100.0, 0.0, 1.0);
        if (tickFactor >= 1.0) return 1.0F;
        double factor = 1.0 - Math.pow(1.0 - tickFactor, seconds * 20.0 * strength);
        return (float) Mth.clamp(factor, 0.0, 1.0);
    }

    private double frameSeconds(long nowNanos) {
        double seconds = 1.0 / 60.0;
        if (previousAimNanos != 0L) {
            long elapsed = nowNanos - previousAimNanos;
            if (elapsed >= 0L && elapsed <= MAX_FRAME_GAP_NANOS) {
                seconds = elapsed / 1_000_000_000.0;
            }
        }
        previousAimNanos = nowNanos;
        return seconds;
    }

    private static double angleBetween(Vec3 look, Vec3 delta) {
        if (delta.lengthSqr() < 1.0E-10) return 0.0;
        double dot = Mth.clamp(look.dot(delta.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static double square(double value) {
        return value * value;
    }

    private void resetAimState() {
        lockedTarget = null;
        previousAimNanos = 0L;
    }

    private boolean isTargetAllowed(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (entity instanceof TamableAnimal tame && tame.isOwnedBy(mc.player)) return false;
        if (entity instanceof Player player) {
            String category = FriendManager.contains(player.getPlainTextName()) ? FRIENDS : PLAYERS;
            if (!targets.isSelected(category)) return false;
            return targets.isSelected(NAKED) || hasAnyArmor(player);
        }
        if (entity instanceof Animal) return targets.isSelected(ANIMALS);
        if (entity instanceof Mob) return targets.isSelected(MOBS);
        return false;
    }

    private static boolean hasAnyArmor(LivingEntity living) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (living.hasItemInSlot(slot)) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        updateAim();
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (mc.screen != null || mc.options.hideGui) return;
        if (!mode.is(MODE_FOV)) return;
        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        float cx = width * 0.5F;
        float cy = height * 0.5F;
        float radius = fovToPixels(fov.getValue().floatValue(), height);
        if (radius < 1.0F) return;
        float diameter = radius * 2.0F;
        Render2D.roundedOutline(graphics, cx - radius, cy - radius,
                diameter, diameter, radius, CIRCLE_THICKNESS, CIRCLE_COLOR);
    }

    private float fovToPixels(float aimFovDeg, float screenHeight) {
        double gameFov = mc.options.fov().get();
        if (gameFov <= 1.0) gameFov = 70.0;
        double aimRad = Math.toRadians(aimFovDeg * 0.5);
        double gameRad = Math.toRadians(gameFov * 0.5);
        double tanGame = Math.tan(gameRad);
        if (tanGame < 1.0E-6) return 0.0F;
        return (float) (Math.tan(aimRad) / tanGame * (screenHeight * 0.5));
    }
}
