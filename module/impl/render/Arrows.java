package dev.lucid.module.impl.render;

import dev.lucid.Lucid;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.ColorSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.movement.MoveUtil;
import dev.lucid.util.players.friend.FriendManager;
import dev.lucid.util.render.ColorUtil;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.WorldProjection;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Стрелки вокруг прицела в сторону игроков: угол — на игрока, дистанция до
 * стрелки — от настроек, цвет — свой для друзей ({@link FriendManager}) и для
 * остальных.
 *
 * <p>Рисуется как HUD-элемент (как {@link NameTags}), но проецирует игроков не
 * через {@link WorldProjection} — стрелке не нужна точка над головой, только
 * направление. Оно считается по классической схеме «радара»: дельта позиций
 * игрока и камеры поворачивается на yaw камеры, и {@code atan2} даёт угол прямо
 * в экранных координатах. Ноль сверху экрана не «когда игрок на севере», а когда
 * игрок ровно по курсу, поэтому стрелки крутятся вместе с камерой.</p>
 *
 * <p>Всё, что должно «жить», идёт через экспоненциальных последователей: радиус
 * дышит (инвентарь расталкивает, шифт поджимает, движение отталкивает), наклон
 * по X/Y тянется за вводом WASD, а yaw камеры догоняется с коротким лагом —
 * стрелки чуть «отстают» при развороте, как стрелка компаса. Разница yaw берётся
 * через {@code Mth.wrapDegrees}: у ванильного yaw нет границ, и без заворачивания
 * после пары оборотов последователь уезжал бы на 360° от цели. Каждая стрелка
 * гасится своей альфой: игрок вышел из мира — стрелка плавно сжимается к центру
 * и умирает, вернулся — вырастает обратно.</p>
 *
 * <p>Только от первого лица: в третьем стрелки вокруг центра экрана указывают
 * мимо, туда, куда камера не смотрит. Модуль выключен — альфы сбрасываются,
 * чтобы после включения стрелки не вспыхивали все разом.</p>
 */
public class Arrows extends Module implements HudElement {

    public static final String STYLE_CLASSIC = "Classic";
    public static final String STYLE_CHEVRON = "Chevron";
    public static final String STYLE_SHARP = "Sharp";
    public static final String STYLE_ROUNDED = "Rounded";

    /** Радиус подгоняется за целью за ~0.12 с, наклон и камера — живее. */
    private static final float RADIUS_TAU = 0.12F;
    private static final float TILT_TAU = 0.10F;
    private static final float CAMERA_TAU = 0.08F;

    /** За это время стрелка появляется/исчезает целиком, в секундах. */
    private static final float FADE_SECONDS = 0.2F;

    /** Поправки радиуса — как и в других клиентах: инвентарь, шифт, движение. */
    private static final float INVENTORY_PUSH = 80F;
    private static final float SNEAK_PULL = 20F;
    private static final float MOVING_PUSH = 10F;

    private final SelectSetting style = new SelectSetting("Style", STYLE_CLASSIC,
            STYLE_CLASSIC, STYLE_CHEVRON, STYLE_SHARP, STYLE_ROUNDED);
    private final SliderSetting distance = new SliderSetting("Distance", 50, 20, 100, 5);
    private final SliderSetting size = new SliderSetting("Size", 17, 10, 28, 1);
    /** Цвета совпадают с EspOverlay: игроки — красный, друзья — голубой. */
    private final ColorSetting playerColor = new ColorSetting("Player Color", 0xFFFF5C5C);
    private final ColorSetting friendColor = new ColorSetting("Friend Color", 0xFF5CC8FF);

    private final List<Fade> fades = new ArrayList<>();

    // Последователи: значение и наносекунды прошлого кадра у них общие
    private float radius;
    private float tiltX;
    private float tiltY;
    private float cameraYaw;
    private long lastNanos;

    public Arrows() {
        super("Arrows", "Arrows around the crosshair pointing to players", Category.RENDER);
        addSettings(style, distance, size, playerColor, friendColor);
    }

    @Override
    protected void onDisable() {
        fades.clear();
        radius = 0.0F;
        tiltX = 0.0F;
        tiltY = 0.0F;
        lastNanos = 0L;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!isEnabled() || mc.player == null || mc.level == null || !WorldProjection.isReady()) return;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;

        float dt = frameSeconds();
        updateAnimations(dt);
        updateFades(dt);

        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        float centerX = width / 2.0F;
        float centerY = height / 2.0F;
        float partial = WorldProjection.partialTick();
        Vec3 eye = mc.gameRenderer.getMainCamera().position();

        float arrowSize = size.getInt();
        float half = arrowSize / 2.0F;

        for (Fade fade : fades) {
            if (fade.alpha <= 0.01F) continue;

            Vec3 pos = fade.player.getPosition(partial);
            double dx = pos.x - eye.x;
            double dz = pos.z - eye.z;

            double yawRad = Math.toRadians(cameraYaw);
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);
            double rotY = -(dz * cos - dx * sin);
            double rotX = -(dx * cos + dz * sin);
            float angle = (float) (Math.atan2(rotY, rotX) * 180.0 / Math.PI);

            int base = FriendManager.contains(fade.player.getPlainTextName())
                    ? friendColor.getValue() : playerColor.getValue();
            int color = ColorUtil.multiplyAlpha(base, fade.alpha);

            float spawn = radius * fade.alpha;
            float x = (float) (spawn * Math.cos(Math.toRadians(angle))) + centerX + tiltX;
            float y = (float) (spawn * Math.sin(Math.toRadians(angle))) + centerY + tiltY;

            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(x, y);
                graphics.pose().rotate((float) Math.toRadians(angle + 90.0));
                Render2D.texture(graphics, texture(style.getValue()), -half, -half, arrowSize, arrowSize, color);
            } finally {
                graphics.pose().popMatrix();
            }
            graphics.nextStratum();
        }
    }

    /** Радиус, наклон и yaw камеры: экспоненциальные последователи, см. javadoc класса. */
    private void updateAnimations(float dt) {
        float target = distance.getInt();
        if (mc.screen instanceof InventoryScreen) target += INVENTORY_PUSH;
        if (mc.player.isShiftKeyDown()) target -= SNEAK_PULL;
        if (MoveUtil.isMoving()) target += MOVING_PUSH;
        radius += (target - radius) * gain(dt, RADIUS_TAU);

        tiltX += (MoveUtil.getStrafeInput() * 5.0F - tiltX) * gain(dt, TILT_TAU);
        tiltY += (MoveUtil.getForwardInput() * 5.0F - tiltY) * gain(dt, TILT_TAU);

        float yaw = mc.gameRenderer.getMainCamera().yRot();
        cameraYaw += Mth.wrapDegrees(yaw - cameraYaw) * gain(dt, CAMERA_TAU);
    }

    /** Появление/затухание стрелок: кто в мире — растёт, кто пропал — гаснет и умирает. */
    private void updateFades(float dt) {
        List<Fade> updated = new ArrayList<>(fades.size());
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player) continue;
            Fade fade = find(player);
            if (fade == null) {
                fade = new Fade(player);
                fade.alpha = 0.0F;
                fade.appearing = true;
            } else {
                fade.appearing = true;
            }
            updated.add(fade);
        }
        for (Fade fade : fades) {
            if (!updated.contains(fade)) {
                fade.appearing = false;
                if (fade.alpha > 0.0F) updated.add(fade);
            }
        }

        float step = dt / FADE_SECONDS;
        fades.clear();
        for (Fade fade : updated) {
            fade.alpha = Mth.clamp(fade.alpha + (fade.appearing ? step : -step), 0.0F, 1.0F);
            if (fade.alpha > 0.0F) fades.add(fade);
        }
    }

    private Fade find(AbstractClientPlayer player) {
        for (Fade fade : fades) {
            if (fade.player == player) return fade;
        }
        return null;
    }

    private float frameSeconds() {
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
        lastNanos = now;
        return seconds;
    }

    /** Шаг последователя за кадр: одинаковое сглаживание на любом FPS. */
    private static float gain(float dt, float tau) {
        return dt <= 0.0F ? 0.0F : 1.0F - (float) Math.exp(-dt / tau);
    }

    /** Текстура выбранного варианта: assets/lucid/textures/world/arrow_<style>.png. */
    private static Identifier texture(String styleName) {
        return Identifier.fromNamespaceAndPath(Lucid.MOD_ID,
                "textures/world/arrow_" + styleName.toLowerCase(Locale.ROOT) + ".png");
    }

    /** Стрелка конкретного игрока: ссылка на сущность + её альфа. */
    private static final class Fade {
        final AbstractClientPlayer player;
        float alpha;
        boolean appearing;

        Fade(AbstractClientPlayer player) {
            this.player = player;
        }
    }
}
