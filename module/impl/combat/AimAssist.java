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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Smooth, frame-rate-independent aim assistance inside the configured FOV. */
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

    /** Prevents tiny corrections from alternating around the aim point. */
    private static final float DEAD_ZONE_DEGREES = 0.06F;
    /** A locked target gets a little extra room before the lock is released. */
    private static final double LOCK_FOV_MULTIPLIER = 1.15;
    private static final long MAX_FRAME_GAP_NANOS = 250_000_000L;

    private final SelectSetting mode = new SelectSetting("Mode", MODE_FOV, MODE_FOV);
    private final SliderSetting fov = new SliderSetting("FOV", 40.0, 10.0, 90.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Speed", 30.0, 1.0, 100.0, 1.0);
    private final SliderSetting range = new SliderSetting("Range", 4.5, 1.0, 6.0, 0.1);
    private final MultiSelectSetting targets = new MultiSelectSetting("Targets",
            List.of(PLAYERS, ANIMALS, MOBS, NAKED, FRIENDS),
            PLAYERS, ANIMALS, MOBS, NAKED, FRIENDS);

    private Entity lockedTarget;
    private long previousAimNanos;

    public AimAssist() {
        super("AimAssist", "Helps aim at targets inside the FOV circle", Category.COMBAT);
        addSettings(mode, fov, speed, range, targets);
    }

    @Override
    protected void onEnable() {
        resetAimState();
    }

    @Override
    protected void onDisable() {
        resetAimState();
    }

    /**
     * Runs with HUD rendering rather than the 20 Hz client tick. The target position
     * is interpolated with the same partial tick used by world rendering, so moving
     * targets no longer advance in visible tick-sized steps.
     */
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

        long now = System.nanoTime();
        double seconds = frameSeconds(now);
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
        if (isLockedTargetValid(lockedTarget, partialTick)) {
            return lockedTarget;
        }

        lockedTarget = findTarget(partialTick);
        return lockedTarget;
    }

    private boolean isLockedTargetValid(Entity entity, float partialTick) {
        if (!isTargetAllowed(entity)) return false;
        return isInsideAimArea(entity, partialTick, LOCK_FOV_MULTIPLIER);
    }

    /** Finds the entity closest to the crosshair, not merely the closest entity. */
    private Entity findTarget(float partialTick) {
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;

        Vec3 eyes = mc.player.getEyePosition(partialTick);
        Vec3 look = mc.player.getViewVector(partialTick);
        double maxRangeSq = square(range.getValue());
        double maxAngle = fov.getValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isTargetAllowed(entity)) continue;

            Vec3 targetEyes = entity.getEyePosition(partialTick);
            double distSq = eyes.distanceToSqr(targetEyes);
            if (distSq > maxRangeSq || distSq < 1.0E-4) continue;

            double angle = angleBetween(look, targetEyes.subtract(eyes));
            if (angle > maxAngle) continue;

            if (angle < bestAngle) {
                bestAngle = angle;
                best = entity;
            }
        }
        return best;
    }

    private boolean isInsideAimArea(Entity entity, float partialTick, double fovMultiplier) {
        Vec3 eyes = mc.player.getEyePosition(partialTick);
        Vec3 targetEyes = entity.getEyePosition(partialTick);
        Vec3 delta = targetEyes.subtract(eyes);

        double distanceSq = delta.lengthSqr();
        if (distanceSq < 1.0E-4 || distanceSq > square(range.getValue())) return false;

        double angle = angleBetween(mc.player.getViewVector(partialTick), delta);
        return angle <= fov.getValue() * fovMultiplier;
    }

    private void aimAt(Entity target, float partialTick, double seconds) {
        Vec3 eyes = mc.player.getEyePosition(partialTick);
        Vec3 targetEyes = target.getEyePosition(partialTick);
        Vec3 delta = targetEyes.subtract(eyes);

        double distXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, distXZ));

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        float yawDiff = Mth.wrapDegrees(targetYaw - yaw);
        float pitchDiff = targetPitch - pitch;

        if (Math.abs(yawDiff) < DEAD_ZONE_DEGREES) yawDiff = 0.0F;
        if (Math.abs(pitchDiff) < DEAD_ZONE_DEGREES) pitchDiff = 0.0F;
        if (yawDiff == 0.0F && pitchDiff == 0.0F) return;

        float frameFactor = smoothingFactor(seconds);
        mc.player.setYRot(yaw + yawDiff * frameFactor);
        mc.player.setXRot(Mth.clamp(pitch + pitchDiff * frameFactor, -90.0F, 90.0F));
    }

    /**
     * Preserves the old Speed meaning at 20 ticks per second, while making the
     * correction independent of render FPS.
     */
    private float smoothingFactor(double seconds) {
        double tickFactor = Mth.clamp(speed.getValue() / 100.0, 0.0, 1.0);
        if (tickFactor >= 1.0) return 1.0F;

        double frameFactor = 1.0 - Math.pow(1.0 - tickFactor, seconds * 20.0);
        return (float) Mth.clamp(frameFactor, 0.0, 1.0);
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
        Vec3 direction = delta.normalize();
        double dot = Mth.clamp(look.dot(direction), -1.0, 1.0);
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
        Render2D.roundedOutline(
                graphics,
                cx - radius,
                cy - radius,
                diameter,
                diameter,
                radius,
                CIRCLE_THICKNESS,
                CIRCLE_COLOR
        );
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
