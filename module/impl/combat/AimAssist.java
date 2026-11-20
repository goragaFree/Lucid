package dev.lucid.module.impl.combat;

import dev.lucid.event.Priority;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
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

/**
 * Помогает наводиться на цель внутри FOV-круга вокруг прицела.
 *
 * <p>Режим пока один — FOV: если цель попадает в конус с углом {@code FOV} и
 * в пределах {@code Range}, модуль каждый тик доводит yaw/pitch к ней
 * на долю {@code Speed}. Круг на экране — визуальный радиус того же угла.</p>
 *
 * <p>Фильтр целей совпадает с {@link TriggerBot}: Players / Animals / Mobs /
 * Naked / Friends. Приручённые животные и сам игрок никогда не целятся.</p>
 */
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

    /** Серый контур, непрозрачность ~90% (alpha 230). */
    private static final int CIRCLE_COLOR = 0xE6808080;
    private static final float CIRCLE_THICKNESS = 2.0F;

    private final SelectSetting mode = new SelectSetting("Mode", MODE_FOV, MODE_FOV);
    private final SliderSetting fov = new SliderSetting("FOV", 40.0, 10.0, 90.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Speed", 30.0, 1.0, 100.0, 1.0);
    private final SliderSetting range = new SliderSetting("Range", 4.5, 1.0, 6.0, 0.1);
    private final MultiSelectSetting targets = new MultiSelectSetting("Targets",
            List.of(PLAYERS, ANIMALS, MOBS, NAKED, FRIENDS),
            PLAYERS, ANIMALS, MOBS, NAKED, FRIENDS);

    public AimAssist() {
        super("AimAssist", "Helps aim at targets inside the FOV circle", Category.COMBAT);
        addSettings(mode, fov, speed, range, targets);
    }

    @Subscribe(priority = Priority.HIGH)
    private void onClientTick(ClientTickEvent event) {
        if (event.getPhase() != ClientTickEvent.Phase.END) return;
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (mc.screen != null || !mc.isWindowActive()) return;
        if (mc.getCameraEntity() != mc.player || mc.player.isSleeping()) return;

        if (!mode.is(MODE_FOV)) return;

        Entity target = findTarget();
        if (target == null) return;

        aimAt(target);
    }

    /**
     * Ближайшая (по угловому расстоянию до прицела) валидная цель
     * в пределах range и FOV. Угол — между view-вектором и вектором к глазам цели.
     */
    private Entity findTarget() {
        double maxRange = range.getValue();
        double maxRangeSq = maxRange * maxRange;
        double maxAngle = fov.getValue();

        Vec3 eyes = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);

        Entity best = null;
        double bestAngle = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isTargetAllowed(entity)) continue;

            Vec3 targetEyes = entity.getEyePosition(1.0F);
            double distSq = eyes.distanceToSqr(targetEyes);
            if (distSq > maxRangeSq || distSq < 1.0E-4) continue;

            Vec3 toTarget = targetEyes.subtract(eyes).normalize();
            double dot = Mth.clamp(look.dot(toTarget), -1.0, 1.0);
            double angle = Math.toDegrees(Math.acos(dot));
            if (angle > maxAngle) continue;

            if (angle < bestAngle) {
                bestAngle = angle;
                best = entity;
            }
        }
        return best;
    }

    /**
     * Доводит yaw/pitch к глазам цели. Speed 1..100 — процент коррекции за тик:
     * 100 почти мгновенно, 30 — мягкая помощь.
     */
    private void aimAt(Entity target) {
        Vec3 eyes = mc.player.getEyePosition(1.0F);
        Vec3 targetEyes = target.getEyePosition(1.0F);
        Vec3 delta = targetEyes.subtract(eyes);

        double distXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, distXZ));

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        float yawDiff = Mth.wrapDegrees(targetYaw - yaw);
        float pitchDiff = targetPitch - pitch;

        float factor = (float) (speed.getValue() / 100.0);
        factor = Mth.clamp(factor, 0.0F, 1.0F);

        mc.player.setYRot(yaw + yawDiff * factor);
        mc.player.setXRot(Mth.clamp(pitch + pitchDiff * factor, -90.0F, 90.0F));
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

    // ---------------- HUD: FOV-круг ----------------

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (mc.screen != null || mc.options.hideGui) return;
        if (!mode.is(MODE_FOV)) return;

        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        float cx = width * 0.5F;
        float cy = height * 0.5F;

        float radius = fovToPixels(fov.getValue().floatValue(), height);
        if (radius < 1.0F) return;

        // Обводка круга: roundedOutline с radius = половина стороны даёт окружность.
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

    /**
     * Переводит угловой FOV (градусы) в радиус круга на экране.
     * Использует текущий FOV опций, чтобы круг совпадал с конусом наводки
     * при любом зуме/настройке камеры.
     */
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
