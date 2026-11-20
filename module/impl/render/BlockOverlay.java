package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.render.ColorUtil;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.WorldProjection;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector2f;

/**
 * Block Overlay: каркас блока под прицелом.
 *
 * <p>Устроен как {@link EspOverlay}: 8 углов формы блока проецируются матрицами кадра
 * ({@link WorldProjection}) и 12 рёбер рисуются в слое HUD через {@link Render2D}.
 * Берётся настоящая форма блока (плита, забор, ступенька), а не полный куб.</p>
 */
public class BlockOverlay extends Module implements HudElement {

    private static final int[] EDGE_A = {0, 1, 3, 2, 4, 5, 7, 6, 0, 1, 2, 3};
    private static final int[] EDGE_B = {1, 3, 2, 0, 5, 7, 6, 4, 4, 5, 6, 7};

    private final SliderSetting outlineWidth = new SliderSetting("Толщина контура", 1.5, 0, 4, 0.25);
    private final SliderSetting outlineAlpha = new SliderSetting("Прозрачность контура", 255, 0, 255, 5);
    private final SliderSetting fill = new SliderSetting("Заливка", 40, 0, 255, 5);

    private final Vector2f[] corners = new Vector2f[8];

    public BlockOverlay() {
        super("Block Overlay", "Highlights the block you are looking at", Category.RENDER);
        for (int i = 0; i < corners.length; i++) corners[i] = new Vector2f();
        addSettings(outlineWidth, outlineAlpha, fill);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!isEnabled() || mc.player == null || mc.level == null || !WorldProjection.isReady()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getShape(mc.level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);

        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        if (!project(box, width, height)) return;

        if (fill.getInt() > 0) fillHull(graphics, ColorUtil.withAlpha(0xFFFFFFFF, fill.getInt()));

        float lineWidth = outlineWidth.getValue().floatValue();
        if (lineWidth <= 0 || outlineAlpha.getInt() <= 0) return;
        int argb = ColorUtil.withAlpha(0xFFFFFFFF, outlineAlpha.getInt());
        for (int i = 0; i < EDGE_A.length; i++) {
            Vector2f a = corners[EDGE_A[i]];
            Vector2f b = corners[EDGE_B[i]];
            line(graphics, a.x, a.y, b.x, b.y, lineWidth, argb);
        }
    }

    /**
     * Заливка силуэта блока: выпуклая оболочка 8 спроецированных углов, закрашенная
     * горизонтальными полосками по 1 GUI-пикселю. Полоски не перекрываются, поэтому
     * прозрачность ровная, без тёмных швов.
     */
    private void fillHull(GuiGraphics graphics, int color) {
        Vector2f[] pts = corners.clone();
        java.util.Arrays.sort(pts, (p, q) -> p.x != q.x ? Float.compare(p.x, q.x) : Float.compare(p.y, q.y));
        Vector2f[] hull = new Vector2f[16];
        int k = 0;
        for (Vector2f p : pts) {
            while (k >= 2 && cross(hull[k - 2], hull[k - 1], p) <= 0) k--;
            hull[k++] = p;
        }
        for (int i = pts.length - 2, lower = k + 1; i >= 0; i--) {
            while (k >= lower && cross(hull[k - 2], hull[k - 1], pts[i]) <= 0) k--;
            hull[k++] = pts[i];
        }
        int n = k - 1;
        if (n < 3) return;

        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) { minY = Math.min(minY, hull[i].y); maxY = Math.max(maxY, hull[i].y); }
        int y0 = Math.max(0, (int) Math.floor(minY));
        int y1 = Math.min(graphics.guiHeight(), (int) Math.ceil(maxY));
        for (int y = y0; y < y1; y++) {
            float sy = y + 0.5F;
            float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                Vector2f a = hull[i], b = hull[(i + 1) % n];
                if ((a.y <= sy && b.y > sy) || (b.y <= sy && a.y > sy)) {
                    float x = a.x + (sy - a.y) / (b.y - a.y) * (b.x - a.x);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (right > left) graphics.fill(Math.round(left), y, Math.round(right), y + 1, color);
        }
    }

    private static float cross(Vector2f o, Vector2f a, Vector2f b) {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
    }

    private boolean project(AABB box, float width, float height) {
        int i = 0;
        for (int yi = 0; yi < 2; yi++) {
            double y = yi == 0 ? box.minY : box.maxY;
            for (int zi = 0; zi < 2; zi++) {
                double z = zi == 0 ? box.minZ : box.maxZ;
                for (int xi = 0; xi < 2; xi++) {
                    double x = xi == 0 ? box.minX : box.maxX;
                    if (!WorldProjection.project(x, y, z, width, height, corners[i])) return false;
                    i++;
                }
            }
        }
        return true;
    }

    private static void line(GuiGraphics graphics, float x0, float y0, float x1, float y1,
                             float lineWidth, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.01F) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0, y0);
        graphics.pose().rotate((float) Math.atan2(dy, dx));
        Render2D.rect(graphics, 0, -lineWidth * 0.5F, length, lineWidth, color);
        graphics.pose().popMatrix();
    }
}
