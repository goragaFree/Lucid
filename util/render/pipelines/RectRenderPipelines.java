package dev.lucid.util.render.pipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Элемент отрисовки: прямоугольник со скруглением (или без), обводкой и цветом в каждом углу.
 * Какой будет угол — круглый или squircle — решает pipeline (LucidPipelines.ROUND / SQUIRCLE).
 *
 * В вершины кроме цвета едут: координата от центра фигуры, половинный размер
 * и пара «радиус — толщина обводки» в шестнадцатых долях пикселя.
 */
public record RectRenderPipelines(
        RenderPipeline pipeline,
        Matrix3x2f pose,
        float x0, float y0, float x1, float y1,
        float radius, float thickness,
        int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    /** Сколько долей пикселя влезает в целочисленный атрибут. */
    private static final float SUBPIXEL = 16.0F;

    /** Запас вокруг фигуры под сглаживание края. */
    private static final float ANTIALIAS_PADDING = 1.0F;

    /** Обычный конструктор: границы считаются сами. */
    public RectRenderPipelines(
            RenderPipeline pipeline,
            Matrix3x2f pose,
            float x0, float y0, float x1, float y1,
            float radius, float thickness,
            int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft,
            @Nullable ScreenRectangle scissorArea) {
        this(pipeline, pose, x0, y0, x1, y1, radius, thickness,
                colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft,
                scissorArea, computeBounds(x0, y0, x1, y1, pose, scissorArea));
    }

    /** Порядок вершин как у ванильной заливки. */
    @Override
    public void buildVertices(VertexConsumer consumer) {
        float padding = padding(pose);
        float dx = Math.copySign(padding, x1 - x0);
        float dy = Math.copySign(padding, y1 - y0);
        // Квад расширяется вместе с UV; halfSize и радиус остаются размерами фигуры
        emit(consumer, x0 - dx, y0 - dy, colorTopLeft);
        emit(consumer, x0 - dx, y1 + dy, colorBottomLeft);
        emit(consumer, x1 + dx, y1 + dy, colorBottomRight);
        emit(consumer, x1 + dx, y0 - dy, colorTopRight);
    }

    private void emit(VertexConsumer consumer, float x, float y, int color) {
        float centerX = (x0 + x1) * 0.5F;
        float centerY = (y0 + y1) * 0.5F;
        float halfWidth = Math.abs(x1 - x0) * 0.5F;
        float halfHeight = Math.abs(y1 - y0) * 0.5F;

        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(color)
                .setUv(x - centerX, y - centerY)
                .setUv1(pack(halfWidth), pack(halfHeight))
                .setUv2(pack(radius), thickness > 0.0F ? Math.max(1, pack(thickness)) : 0);
    }

    private static int pack(float value) {
        return Math.round(value * SUBPIXEL);
    }

    /** Запас в локальных координатах с учётом уменьшения и поворота pose. */
    private static float padding(Matrix3x2f pose) {
        float determinant = Math.abs(pose.m00() * pose.m11() - pose.m01() * pose.m10());
        if (determinant == 0.0F) {
            return ANTIALIAS_PADDING;
        }
        float inverseNorm = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01()
                + pose.m10() * pose.m10() + pose.m11() * pose.m11()) / determinant;
        return Math.max(ANTIALIAS_PADDING, inverseNorm);
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    /**
     * Границы нужны игре, чтобы понять, какие элементы перекрываются и в какой слой
     * положить этот. Если они неверные — элемент пропадёт или нарисуется не в том порядке.
     */
    private static @Nullable ScreenRectangle computeBounds(float x0, float y0, float x1, float y1,
                                                          Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        float padding = padding(pose);
        int left = (int) Math.floor(Math.min(x0, x1) - padding);
        int top = (int) Math.floor(Math.min(y0, y1) - padding);
        int width = (int) Math.ceil(Math.max(x0, x1) + padding) - left;
        int height = (int) Math.ceil(Math.max(y0, y1) + padding) - top;

        ScreenRectangle rectangle = new ScreenRectangle(left, top, width, height).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(rectangle) : rectangle;
    }
}
