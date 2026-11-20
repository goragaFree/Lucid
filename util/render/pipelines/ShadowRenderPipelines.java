package dev.lucid.util.render.pipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Тень под фигурой: та же формула формы, что у прямоугольника, но с широкой полосой
 * затухания вместо резкого края.
 *
 * Главное отличие от {@link RectRenderPipelines}: прямоугольник, который рисуется, больше
 * самой фигуры. Размытый край уходит наружу на целый радиус размытия, и если рисовать его
 * по геометрии фигуры, тень обрежется ровно по её краю — видно как чёткая рамка вокруг панели.
 *
 * Поэтому в вершины едет два разных прямоугольника: геометрия — расширенная, а половинный
 * размер и координата от центра — от самой тени. Шейдер считает расстояние до второго,
 * а заливает первый.
 *
 * Целые атрибуты, как и у фигур, пишутся в шестнадцатых долях пикселя.
 */
public record ShadowRenderPipelines(
        RenderPipeline pipeline,
        Matrix3x2f pose,
        float x0, float y0, float x1, float y1,
        float centerX, float centerY,
        float halfWidth, float halfHeight,
        float radius, float blur,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    /** Сколько долей пикселя влезает в целочисленный атрибут. */
    private static final float SUBPIXEL = 16.0F;

    /** Запас границ под сглаживание края. */
    private static final int ANTIALIAS_PADDING = 1;

    /**
     * Обычный конструктор: границы считаются сами по расширенному прямоугольнику.
     *
     * Границы нужны игре, чтобы понять, какие элементы перекрываются. У тени брать границы
     * фигуры нельзя: она больше её со всех сторон.
     */
    public ShadowRenderPipelines(
            RenderPipeline pipeline,
            Matrix3x2f pose,
            float x0, float y0, float x1, float y1,
            float centerX, float centerY,
            float halfWidth, float halfHeight,
            float radius, float blur,
            int color,
            @Nullable ScreenRectangle scissorArea) {
        this(pipeline, pose, x0, y0, x1, y1, centerX, centerY, halfWidth, halfHeight,
                radius, blur, color, scissorArea,
                computeBounds(x0, y0, x1, y1, pose, scissorArea));
    }

    /** Четыре вершины по часовой стрелке, как и у остальных элементов интерфейса. */
    @Override
    public void buildVertices(VertexConsumer consumer) {
        emit(consumer, x0, y0);
        emit(consumer, x0, y1);
        emit(consumer, x1, y1);
        emit(consumer, x1, y0);
    }

    private void emit(VertexConsumer consumer, float x, float y) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(color)
                .setUv(x - centerX, y - centerY)
                .setUv1(pack(halfWidth), pack(halfHeight))
                .setUv2(pack(radius), pack(blur));
    }

    private static int pack(float value) {
        return Math.round(value * SUBPIXEL);
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    private static @Nullable ScreenRectangle computeBounds(float x0, float y0, float x1, float y1,
                                                          Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        int left = (int) Math.floor(Math.min(x0, x1)) - ANTIALIAS_PADDING;
        int top = (int) Math.floor(Math.min(y0, y1)) - ANTIALIAS_PADDING;
        int width = (int) Math.ceil(Math.abs(x1 - x0)) + ANTIALIAS_PADDING * 2;
        int height = (int) Math.ceil(Math.abs(y1 - y0)) + ANTIALIAS_PADDING * 2;

        ScreenRectangle rectangle = new ScreenRectangle(left, top, width, height).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(rectangle) : rectangle;
    }
}
