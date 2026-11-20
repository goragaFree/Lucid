package dev.lucid.util.render.pipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lucid.util.render.clientpipeline.LucidPipelines;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.FontTexture;
import dev.lucid.util.render.font.Glyph;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Целая строка одним элементом отрисовки: по 4 вершины на букву, но элемент один.
 * Соседние строки с тем же атласом игра склеивает в одну пачку сама.
 *
 * Раскладка и границы считаются одним проходом в конструкторе,
 * сборка вершин берёт готовые глифы и позиции.
 */
public final class TextRenderPipelines implements GuiElementRenderState {

    /** Сколько долей пикселя влезает в целочисленный атрибут. */
    private static final float SUBPIXEL = 16.0F;

    /** Запас границ под сглаживание края. */
    private static final int ANTIALIAS_PADDING = 1;

    private final FontAtlas atlas;
    private final Glyph[] glyphs;
    private final float[] positions;
    private final int glyphCount;
    private final Matrix3x2f pose;
    private final float baseline;
    private final float size;
    private final int color;
    private final @Nullable ScreenRectangle scissorArea;
    private final @Nullable ScreenRectangle bounds;
    private @Nullable TextureSetup textureSetup;

    /**
     * @param x        левый край строки
     * @param baseline линия, на которой стоят буквы
     * @param size     размер шрифта в пикселях интерфейса
     */
    public TextRenderPipelines(FontAtlas atlas, String text, Matrix3x2f pose,
                               float x, float baseline, float size, int color,
                               @Nullable ScreenRectangle scissorArea) {
        this.atlas = atlas;
        this.glyphs = new Glyph[text.length()];
        this.positions = new float[text.length()];
        this.pose = pose;
        this.baseline = baseline;
        this.size = size;
        this.color = color;
        this.scissorArea = scissorArea;

        float pen = x;
        float minX = x;
        float maxX = x;
        float top = baseline - atlas.ascender(size);
        float bottom = baseline - atlas.descender(size);
        int previous = -1;
        int count = 0;

        for (int index = 0; index < text.length(); ) {
            int code = text.codePointAt(index);
            index += Character.charCount(code);

            Glyph glyph = atlas.glyph(code);
            if (glyph == null) continue;

            pen += atlas.kerning(previous, code) * size;
            previous = code;

            if (glyph.drawable()) {
                glyphs[count] = glyph;
                positions[count++] = pen;
                minX = Math.min(minX, pen + glyph.left() * size);
                maxX = Math.max(maxX, pen + glyph.right() * size);
                top = Math.min(top, baseline + glyph.top() * size);
                bottom = Math.max(bottom, baseline + glyph.bottom() * size);
            }
            pen += glyph.advance() * size;
        }

        this.glyphCount = count;
        this.bounds = computeBounds(minX, top, Math.max(maxX, pen), bottom, pose, scissorArea);
    }

    @Override
    public RenderPipeline pipeline() {
        return LucidPipelines.TEXT;
    }

    @Override
    public TextureSetup textureSetup() {
        // Кэш только на жизнь элемента кадра: после перезагрузки ресурсов будут новые элементы
        if (textureSetup == null) {
            textureSetup = FontTexture.of(atlas.texture());
        }
        return textureSetup;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        int range = Math.round(atlas.distanceRange() * SUBPIXEL);

        for (int index = 0; index < glyphCount; index++) {
            Glyph glyph = glyphs[index];
            float pen = positions[index];
            float left = pen + glyph.left() * size;
            float right = pen + glyph.right() * size;
            float top = baseline + glyph.top() * size;
            float bottom = baseline + glyph.bottom() * size;

            // Порядок вершин как у ванильной заливки
            emit(consumer, left, top, glyph.u0(), glyph.v0(), range);
            emit(consumer, left, bottom, glyph.u0(), glyph.v1(), range);
            emit(consumer, right, bottom, glyph.u1(), glyph.v1(), range);
            emit(consumer, right, top, glyph.u1(), glyph.v0(), range);
        }
    }

    private void emit(VertexConsumer consumer, float x, float y, float u, float v, int range) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(color)
                .setUv(u, v)
                .setUv1(range, 0);
    }

    private static @Nullable ScreenRectangle computeBounds(float x, float top, float right, float bottom,
                                                          Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        int left = (int) Math.floor(x) - ANTIALIAS_PADDING;
        int boxTop = (int) Math.floor(top) - ANTIALIAS_PADDING;
        int boxWidth = (int) Math.ceil(right) + ANTIALIAS_PADDING - left;
        int boxHeight = (int) Math.ceil(bottom) + ANTIALIAS_PADDING - boxTop;

        ScreenRectangle rectangle = new ScreenRectangle(left, boxTop, boxWidth, boxHeight).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(rectangle) : rectangle;
    }
}
