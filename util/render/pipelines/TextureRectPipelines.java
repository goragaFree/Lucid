package dev.lucid.util.render.pipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Элемент отрисовки: текстурный квад с тонированием — как ванильный blit, но
 * с дробными координатами и любым поворотом в pose (стрелки Arrows крутятся
 * каждый кадр на свой угол, целочисленный blit тут дрожал бы).
 *
 * <p>Пайплайн ванильный {@link RenderPipelines#GUI_TEXTURED}: формат
 * POSITION_TEX_COLOR, поэтому в вершины едут позиция (уже с pose), UV всего
 * рисунка и ARGB-цвет. Тонировка умножается на текстуру, так что белая
 * текстура становится любым заданным цветом, а её собственная альфа
 * сохраняется. Сэмплер LINEAR: текстуры со сглаженным краем не должны
 * огрубеть при уменьшении.</p>
 *
 * <p>Класс, а не record, по той же причине, что и {@link PlayerHeadRenderState}:
 * TextureSetup лениво кэшируется в поле, а record не умеет нестатические поля.</p>
 */
public final class TextureRectPipelines implements GuiElementRenderState {

    private final Identifier texture;
    private final Matrix3x2f pose;
    private final float x0;
    private final float y0;
    private final float x1;
    private final float y1;
    private final int color;
    private final @Nullable ScreenRectangle scissorArea;
    private final @Nullable ScreenRectangle bounds;
    private @Nullable TextureSetup textureSetup;

    public TextureRectPipelines(Identifier texture, Matrix3x2f pose,
                                float x0, float y0, float x1, float y1,
                                int color, @Nullable ScreenRectangle scissorArea) {
        this.texture = texture;
        this.pose = pose;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.color = color;
        this.scissorArea = scissorArea;
        this.bounds = computeBounds(x0, y0, x1, y1, pose, scissorArea);
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI_TEXTURED;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return scissorArea;
    }

    /** Порядок вершин как у ванильного blit: слева-сверху, слева-снизу, справа-снизу, справа-сверху. */
    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(pose, x0, y0).setUv(0.0F, 0.0F).setColor(color);
        consumer.addVertexWith2DPose(pose, x0, y1).setUv(0.0F, 1.0F).setColor(color);
        consumer.addVertexWith2DPose(pose, x1, y1).setUv(1.0F, 1.0F).setColor(color);
        consumer.addVertexWith2DPose(pose, x1, y0).setUv(1.0F, 0.0F).setColor(color);
    }

    @Override
    public TextureSetup textureSetup() {
        if (textureSetup == null) {
            var loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
            textureSetup = TextureSetup.singleTexture(loaded.getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        }
        return textureSetup;
    }

    /**
     * Границы нужны игре, чтобы понять, какие элементы перекрываются и в какой слой
     * положить этот (см. RectRenderPipelines). transformMaxBounds разворачивает
     * квад по pose — поворот стрелки не даст ей обрезаться.
     */
    private static @Nullable ScreenRectangle computeBounds(float x0, float y0, float x1, float y1,
                                                           Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        int left = (int) Math.floor(Math.min(x0, x1));
        int top = (int) Math.floor(Math.min(y0, y1));
        int width = (int) Math.ceil(Math.max(x0, x1)) - left;
        int height = (int) Math.ceil(Math.max(y0, y1)) - top;

        ScreenRectangle rectangle = new ScreenRectangle(left, top, width, height).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(rectangle) : rectangle;
    }
}
