package dev.lucid.util.render;

import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Матрицы текущего кадра мира — чтобы переводить мировые координаты в экранные.
 *
 * <p>Не считаем проекцию сами через {@code GameRenderer#getProjectionMatrix(fov)}:
 * FOV в ванили динамический (спринт, зелья, эффект портала), а {@code getFov} приватный.
 * {@code LevelRenderer#renderLevel} получает уже готовые view- и projection-матрицы,
 * причём projection к этому моменту несёт и «закрутку» камеры от тошноты/портала.
 * Копируем ровно их — тогда каркас совпадает с картинкой мира в том же кадре,
 * без поправок на глаз и без гадания, какой FOV был у игры.</p>
 *
 * <p>Вершины мира в ванили подаются относительно камеры: view-матрица содержит только
 * поворот, а сдвиг на {@code -camera.position()} делает {@code Frustum.prepare}. Повторяем
 * это здесь: сначала вычитаем позицию камеры, потом умножаем на P·V. Матрица projection
 * здесь именно та, что ушла в {@code RenderSystem.setProjectionMatrix}, а не
 * {@code cullProjectionMatrix} — у последней своя, более широкая область отсечения.</p>
 */
public final class WorldProjection {

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f CLIP = new Vector4f();

    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static float partialTick;
    private static boolean ready;

    private WorldProjection() {}

    /** Вызывается в HEAD {@code LevelRenderer#renderLevel}, до отрисовки GUI того же кадра. */
    public static void capture(Camera camera, Matrix4f view, Matrix4f projection, float partialTick) {
        VIEW_PROJECTION.set(projection).mul(view);
        cameraX = camera.position().x;
        cameraY = camera.position().y;
        cameraZ = camera.position().z;
        WorldProjection.partialTick = partialTick;
        ready = true;
    }

    public static boolean isReady() { return ready; }

    /** Тот же partialTick, с которым ваниль интерполировала сущности в этом кадре. */
    public static float partialTick() { return partialTick; }

    /**
     * Точка мира → координаты GUI (в масштабе {@code GuiGraphics#guiWidth/guiHeight}).
     * Y растёт вниз, как у всего нашего 2D-рендера.
     *
     * @return {@code false}, если точка за камерой или на её плоскости: {@code w <= 0},
     *         и деление на w дало бы зеркальную точку с другой стороны экрана
     */
    public static boolean project(double x, double y, double z, float width, float height, Vector2f out) {
        VIEW_PROJECTION.transform(
                (float) (x - cameraX), (float) (y - cameraY), (float) (z - cameraZ), 1.0F, CLIP);
        if (CLIP.w <= 0.0F) return false;
        out.set((CLIP.x / CLIP.w * 0.5F + 0.5F) * width,
                (0.5F - CLIP.y / CLIP.w * 0.5F) * height);
        return true;
    }
}
