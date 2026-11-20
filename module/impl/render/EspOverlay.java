package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.util.players.friend.FriendManager;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.WorldProjection;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.List;

/**
 * ESP: каркасы хитбоксов сущностей поверх мира.
 *
 * <p>Рисуем не в мировом рендере, а в слое HUD: восемь углов AABB проецируются
 * матрицами кадра ({@link WorldProjection}) в экранные координаты, и 12 рёбер идут
 * обычными прямоугольниками через {@link Render2D}. Пайплайн rect у нас собран с
 * {@code NO_DEPTH_TEST}, поэтому стены и сущности каркас не перекрывают — «сквозь стены»
 * получается самой природой слоя, без отдельного GL-состояния и без борьбы с буфером
 * глубины. Перспектива при этом настоящая: углы считаются матрицами ванили, а не
 * натягиванием плоского прямоугольника на сущность.</p>
 *
 * <p>Цвета пока зашиты константами: настройку цвета автор не просил. Галочки Modes
 * и Targets живут в общем конфиге по именам, новый режим добавляется строкой в список
 * вариантов и веткой в {@link #render}.</p>
 *
 * <p>Как и {@link Hud}, объект зарегистрирован как HudElement навсегда, поэтому
 * включённость проверяет сам метод рендера.</p>
 */
public class EspOverlay extends Module implements HudElement {

    private static final String BOX = "Box";

    private static final String ANIMALS = "Animals";
    private static final String PLAYERS = "Players";
    private static final String FRIENDS = "Friends";
    private static final String MOBS = "Mobs";
    private static final String WARDEN = "Warden";

    // ARGB, непрозрачные: по одному цвету на категорию, без настройки.
    private static final int COLOR_ANIMALS = 0xFF5CE65C;
    private static final int COLOR_PLAYERS = 0xFFFF5C5C;
    private static final int COLOR_FRIENDS = 0xFF5CC8FF;
    private static final int COLOR_MOBS = 0xFFFFC85C;
    private static final int COLOR_WARDEN = 0xFFB45CFF;

    /** Толщина ребра в GUI-пикселях. */
    private static final float LINE_WIDTH = 1.25F;

    // Угол: xi + 2*zi + 4*yi. Низ (y=0): 0..3, верх (y=1): 4..7 — ровно в этом
    // порядке их заполняет projectBox, поэтому пары ниже идут по граням куба.
    private static final int[] EDGE_A = {0, 1, 3, 2, 4, 5, 7, 6, 0, 1, 2, 3};
    private static final int[] EDGE_B = {1, 3, 2, 0, 5, 7, 6, 4, 4, 5, 6, 7};

    // Пока один режим, но мультиселект: новые виды каркаса добавляются без смены типа настройки.
    private final MultiSelectSetting modes = new MultiSelectSetting("Modes", List.of(BOX), BOX);
    // Все цели включены сразу: модуль должен что-то показывать в первый же кадр.
    private final MultiSelectSetting targets = new MultiSelectSetting("Targets",
            List.of(ANIMALS, PLAYERS, FRIENDS, MOBS, WARDEN),
            ANIMALS, PLAYERS, FRIENDS, MOBS, WARDEN);

    // Переиспользуем точки кадра: по 8 вершин на сущность, без аллокаций в цикле.
    private final Vector2f[] corners = new Vector2f[8];

    public EspOverlay() {
        super("ESP Overlay", "Draws entity boxes through walls", Category.RENDER);
        for (int i = 0; i < corners.length; i++) corners[i] = new Vector2f();
        addSettings(modes, targets);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!isEnabled() || !modes.isSelected(BOX) || targets.count() == 0) return;
        if (mc.player == null || mc.level == null || !WorldProjection.isReady()) return;

        // Размер именно из GuiGraphics: в этих же координатах рисует остальной HUD.
        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        float partial = WorldProjection.partialTick();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entity.isAlive()) continue;
            int color = colorFor(entity);
            if (color == 0) continue;

            // getBoundingBox живёт на позиции тика, а ваниль рисует сущности с
            // интерполяцией между xo/yo/zo и текущей позицией. Сдвигаем хитбокс на
            // ту же разницу, иначе каркас отстаёт от модели на дробь тика.
            AABB box = entity.getBoundingBox();
            Vec3 pos = entity.getPosition(partial);
            double dx = pos.x - entity.getX();
            double dy = pos.y - entity.getY();
            double dz = pos.z - entity.getZ();

            if (!projectBox(box.minX + dx, box.minY + dy, box.minZ + dz,
                    box.maxX + dx, box.maxY + dy, box.maxZ + dz, width, height)) continue;
            if (offscreen(width, height)) continue;
            drawBox(graphics, color);
        }
    }

    /**
     * Цвет категории или 0, если сущность рисовать не надо.
     *
     * <p>Уточняющие категории меняют только цвет, а не видимость: снятый Warden
     * остаётся мобом, снятый Friend — игроком. Иначе выключение «Warden» вообще
     * ничего бы не меняло на экране при включённых «Mobs», и галочка перестала бы
     * быть фильтром целей. Порядок Animal → Mob обязателен: {@code Animal} наследует
     * {@code Mob}, как и в {@code TriggerBot#isTargetAllowed}.</p>
     */
    private int colorFor(Entity entity) {
        if (entity instanceof Warden && targets.isSelected(WARDEN)) return COLOR_WARDEN;
        if (entity instanceof Player player) {
            if (targets.isSelected(FRIENDS) && FriendManager.contains(player.getPlainTextName())) {
                return COLOR_FRIENDS;
            }
            return targets.isSelected(PLAYERS) ? COLOR_PLAYERS : 0;
        }
        if (entity instanceof Animal) {
            if (targets.isSelected(ANIMALS)) return COLOR_ANIMALS;
            return targets.isSelected(MOBS) ? COLOR_MOBS : 0;
        }
        if (entity instanceof Mob) return targets.isSelected(MOBS) ? COLOR_MOBS : 0;
        // ArmorStand — LivingEntity, но не Mob: в список целей не попадает.
        return 0;
    }

    /** Проецирует 8 углов; любой угол за камерой отбрасывает весь каркас. */
    private boolean projectBox(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ,
                               float width, float height) {
        int i = 0;
        for (int yi = 0; yi < 2; yi++) {
            double y = yi == 0 ? minY : maxY;
            for (int zi = 0; zi < 2; zi++) {
                double z = zi == 0 ? minZ : maxZ;
                for (int xi = 0; xi < 2; xi++) {
                    double x = xi == 0 ? minX : maxX;
                    if (!WorldProjection.project(x, y, z, width, height, corners[i])) return false;
                    i++;
                }
            }
        }
        return true;
    }

    /** Каркас целиком за экраном — не плодим на него 12 GUI-элементов. */
    private boolean offscreen(float width, float height) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Vector2f corner : corners) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
        }
        return maxX < 0 || maxY < 0 || minX > width || minY > height;
    }

    private void drawBox(GuiGraphics graphics, int color) {
        for (int i = 0; i < EDGE_A.length; i++) {
            Vector2f from = corners[EDGE_A[i]];
            Vector2f to = corners[EDGE_B[i]];
            line(graphics, from.x, from.y, to.x, to.y, color);
        }
    }

    /**
     * Ребро под любым углом — тот же прямоугольник, но повёрнутый pose.
     * Форму шейдер считает в локальных координатах, поэтому поворот не ломает
     * сглаживание края; границы GuiRenderState тоже считаются с учётом pose.
     */
    private static void line(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.01F) return;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x0, y0);
        graphics.pose().rotate((float) Math.atan2(dy, dx));
        Render2D.rect(graphics, 0, -LINE_WIDTH * 0.5F, length, LINE_WIDTH, color);
        graphics.pose().popMatrix();
    }
}
