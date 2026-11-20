package dev.lucid.util.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.lucid.util.render.clientpipeline.LucidPipelines;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import dev.lucid.util.render.pipelines.RectRenderPipelines;
import dev.lucid.util.render.pipelines.ShadowRenderPipelines;
import dev.lucid.util.render.pipelines.TextRenderPipelines;
import dev.lucid.util.render.pipelines.TextureRectPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * 2D-рендер клиента. Все фигуры идут через один шейдер rect и GuiRenderState —
 * так же, как рисует сама игра: учитываются pose (translate/scale), scissor и порядок слоёв.
 *
 * Координаты: x, y, ширина, высота. Цвета ARGB.
 */
public final class Render2D {

    private static final float FILLED = 0.0F;

    private Render2D() {}

    // ------------------------------------------------------------------ rect

    public static void rect(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        roundedRect(graphics, x, y, width, height, 0, color);
    }

    /** Вертикальный градиент: сверху top, снизу bottom. */
    public static void verticalGradient(GuiGraphics graphics, float x, float y, float width, float height, int top, int bottom) {
        shape(graphics, LucidPipelines.ROUND, x, y, width, height, 0, FILLED, top, top, bottom, bottom);
    }

    /** Горизонтальный градиент: слева left, справа right. */
    public static void horizontalGradient(GuiGraphics graphics, float x, float y, float width, float height, int left, int right) {
        shape(graphics, LucidPipelines.ROUND, x, y, width, height, 0, FILLED, left, right, right, left);
    }

    // ---------------------------------------------------------- rounded rect

    public static void roundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        shape(graphics, LucidPipelines.ROUND, x, y, width, height, radius, FILLED, color, color, color, color);
    }

    public static void roundedGradient(GuiGraphics graphics, float x, float y, float width, float height, float radius, int top, int bottom) {
        shape(graphics, LucidPipelines.ROUND, x, y, width, height, radius, FILLED, top, top, bottom, bottom);
    }

    /** Только обводка толщиной thickness, внутри прозрачно. */
    public static void roundedOutline(GuiGraphics graphics, float x, float y, float width, float height,
                                      float radius, float thickness, int color) {
        shape(graphics, LucidPipelines.ROUND, x, y, width, height, radius, thickness, color, color, color, color);
    }

    public static void circle(GuiGraphics graphics, float centerX, float centerY, float radius, int color) {
        roundedRect(graphics, centerX - radius, centerY - radius, radius * 2, radius * 2, radius, color);
    }

    /**
     * Заливка с обводкой одной командой: заливка ужимается на половину толщины обводки.
     *
     * Если рисовать заливку во всю фигуру, а обводку поверх, то на дуге фон смешивается
     * дважды: сначала с заливкой, потом с обводкой. На прямых сторонах этого не видно —
     * там покрытие пикселя 0 или 1. А на скруглении оно дробное, и угол получается другого
     * цвета. Ужатая заливка прячет свой сглаженный край под обводкой, и наружная кромка
     * смешивается с фоном ровно один раз.
     */
    public static void roundedRectOutlined(GuiGraphics graphics, float x, float y, float width, float height,
                                           float radius, float thickness, int fill, int outline) {
        float inset = outlineInset(width, height, thickness);
        roundedRect(graphics, x + inset, y + inset, width - inset * 2, height - inset * 2, Math.max(radius - inset, 0), fill);
        roundedOutline(graphics, x, y, width, height, radius, thickness, outline);
    }

    /** На сколько ужать заливку под обводкой толщиной thickness. */
    public static float outlineInset(float width, float height, float thickness) {
        return Math.min(Math.max(thickness, 0) * 0.5F, Math.min(width, height) * 0.5F);
    }

    // -------------------------------------------------------------- squircle

    public static void squircle(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        shape(graphics, LucidPipelines.SQUIRCLE, x, y, width, height, radius, FILLED, color, color, color, color);
    }

    public static void squircleGradient(GuiGraphics graphics, float x, float y, float width, float height, float radius, int top, int bottom) {
        shape(graphics, LucidPipelines.SQUIRCLE, x, y, width, height, radius, FILLED, top, top, bottom, bottom);
    }

    public static void squircleOutline(GuiGraphics graphics, float x, float y, float width, float height,
                                       float radius, float thickness, int color) {
        shape(graphics, LucidPipelines.SQUIRCLE, x, y, width, height, radius, thickness, color, color, color, color);
    }

    /** То же, что roundedRectOutlined, для squircle. */
    public static void squircleRectOutlined(GuiGraphics graphics, float x, float y, float width, float height,
                                            float radius, float thickness, int fill, int outline) {
        float inset = outlineInset(width, height, thickness);
        squircle(graphics, x + inset, y + inset, width - inset * 2, height - inset * 2, Math.max(radius - inset, 0), fill);
        squircleOutline(graphics, x, y, width, height, radius, thickness, outline);
    }

    /**
     * Мягкая тень под squircle-фигурой.
     *
     * Тень — не заливка: шейдер выбрасывает всё, что внутри фигуры, и гасит плотность наружу
     * на {@code blur}. Поэтому раздувание и смещение обычно нулевые — любой сдвиг загнал бы
     * часть тени под подложку.
     */
    public static void squircleShadow(GuiGraphics graphics, float x, float y, float width, float height,
                                      float radius, float blur, float spread, float offsetX, float offsetY, int color) {
        // Совсем пустая фигура тени не даёт: рисовать нечего, а шейдеру пришлось бы
        // делить на ноль при поиске размера пикселя.
        if (width <= 0 || height <= 0) return;

        float centerX = x + width * 0.5F + offsetX;
        float centerY = y + height * 0.5F + offsetY;

        // Раздувание меняет и радиус: иначе раздутая тень окажется угловатее фигуры,
        // а ужатая — круглее, и край тени перестанет идти вдоль края панели.
        float halfWidth = Math.max(width * 0.5F + spread, 0);
        float halfHeight = Math.max(height * 0.5F + spread, 0);
        if (halfWidth <= 0 || halfHeight <= 0) return;

        float shadowRadius = Math.max(radius + spread, 0);

        // Геометрия шире самой тени на радиус размытия и ещё на пиксель запаса.
        // Без этого размытый хвост обрежется по краю прямоугольника и вокруг панели
        // появится чёткая рамка — самый частый баг теней.
        float margin = Math.max(blur, 0) + 1;

        graphics.guiRenderState.submitGuiElement(new ShadowRenderPipelines(
                LucidPipelines.SHADOW_SQUIRCLE, new Matrix3x2f(graphics.pose()),
                centerX - halfWidth - margin, centerY - halfHeight - margin,
                centerX + halfWidth + margin, centerY + halfHeight + margin,
                centerX, centerY, halfWidth, halfHeight,
                shadowRadius, Math.max(blur, 0),
                color, graphics.scissorStack.peek()
        ));
    }

    // ----------------------------------------------------------------- shape

    /**
     * Любая фигура: pipeline выбирает вид угла, radius = 0 — прямые углы,
     * thickness > 0 — только обводка. Цвета по углам дают градиент в любом направлении.
     */
    public static void shape(GuiGraphics graphics, RenderPipeline pipeline,
                             float x, float y, float width, float height,
                             float radius, float thickness,
                             int topLeft, int topRight, int bottomRight, int bottomLeft) {
        if (width <= 0 || height <= 0) return;

        float clampedRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2.0F));
        graphics.guiRenderState.submitGuiElement(new RectRenderPipelines(
                pipeline, new Matrix3x2f(graphics.pose()),
                x, y, x + width, y + height,
                clampedRadius, thickness,
                topLeft, topRight, bottomRight, bottomLeft,
                graphics.scissorStack.peek()
        ));
    }

    // ------------------------------------------------------------------ text
    // Текст рисуется своим MSDF-шрифтом (util/render/font). Y везде — верх строки,
    // а не базовая линия: так удобнее стыковать текст с прямоугольниками.
    // Без явного шрифта берётся Fonts.regular().

    private static final float SHADOW_OFFSET = 1.0F;
    private static final int SHADOW_COLOR = 0xA0000000;

    public static void text(GuiGraphics graphics, String text, float x, float y, float size, int color) {
        text(graphics, Fonts.regular(), text, x, y, size, color);
    }

    public static void text(GuiGraphics graphics, FontAtlas font, String text, float x, float y, float size, int color) {
        submitText(graphics, font, text, x, y + font.ascender(size), size, color);
    }

    /** Текст с мягкой тенью на пиксель вниз и вправо. */
    public static void textShadow(GuiGraphics graphics, FontAtlas font, String text, float x, float y, float size, int color) {
        text(graphics, font, text, x + SHADOW_OFFSET, y + SHADOW_OFFSET, size, ColorUtil.multiplyAlpha(SHADOW_COLOR, ColorUtil.alpha(color) / 255.0F));
        text(graphics, font, text, x, y, size, color);
    }

    /** Текст по центру относительно точки centerX. */
    public static void textCentered(GuiGraphics graphics, FontAtlas font, String text, float centerX, float y, float size, int color) {
        text(graphics, font, text, centerX - font.width(text, size) * 0.5F, y, size, color);
    }

    /**
     * Текст по середине полосы rowY..rowY+rowHeight. Центрируется высота заглавной буквы,
     * а не вся строка: у Golos Text над заглавными много воздуха, иначе текст съезжал бы вниз.
     */
    public static void textInRow(GuiGraphics graphics, FontAtlas font, String text, float x, float rowY, float rowHeight, float size, int color) {
        submitText(graphics, font, text, x, rowY + (rowHeight + font.capHeight(size)) * 0.5F, size, color);
    }

    /** То же, что textInRow, но по центру и по горизонтали. */
    public static void textCenteredInRow(GuiGraphics graphics, FontAtlas font, String text, float centerX, float rowY, float rowHeight, float size, int color) {
        textInRow(graphics, font, text, centerX - font.width(text, size) * 0.5F, rowY, rowHeight, size, color);
    }

    public static float textWidth(String text, float size) {
        return Fonts.regular().width(text, size);
    }

    public static float textWidth(FontAtlas font, String text, float size) {
        return font.width(text, size);
    }

    public static float textHeight(FontAtlas font, float size) {
        return font.lineHeight(size);
    }

    /** Иконка из шрифта (Icons, глиф U+E000..): квадрат size × size от левого верхнего угла. */
    public static void icon(GuiGraphics graphics, char icon, float x, float y, float size, int color) {
        text(graphics, Fonts.regular(), String.valueOf(icon), x, y, size, color);
    }

    /** Иконка с центром в точке: рисунок отцентрован внутри своего квадрата ещё в атласе. */
    public static void iconCentered(GuiGraphics graphics, char icon, float centerX, float centerY, float size, int color) {
        icon(graphics, icon, centerX - size * 0.5F, centerY - size * 0.5F, size, color);
    }

    private static void submitText(GuiGraphics graphics, FontAtlas font, String text, float x, float baseline, float size, int color) {
        if (text.isEmpty() || ColorUtil.alpha(color) == 0) return;
        // Начало строки и базовая линия — ровно на пиксель экрана. Иначе при центрировании
        // строка встаёт на полпикселя, и горизонтальные штрихи букв размазываются на два ряда.
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        float snappedX = Math.round(x * scale) / scale;
        float snappedBaseline = Math.round(baseline * scale) / scale;

        graphics.guiRenderState.submitGuiElement(new TextRenderPipelines(
                font, text, new Matrix3x2f(graphics.pose()), snappedX, snappedBaseline, size, color, graphics.scissorStack.peek()
        ));
    }

    // --------------------------------------------------------------- texture

    /**
     * Текстурный квад с тонированием: x/y — левый верхний угол, размер в GUI-пикселях,
     * UV на весь рисунок, pose применяется как у всех фигур (повороты и масштаб работают).
     * Цвет умножается на текстуру: белая текстура становится цветом color, альфа текстуры
     * сохраняется. Текстура берётся из TextureManager по пути вида
     * {@code textures/world/arrow_classic.png}.
     */
    public static void texture(GuiGraphics graphics, Identifier texture, float x, float y, float width, float height, int color) {
        if (ColorUtil.alpha(color) == 0 || width <= 0 || height <= 0) return;
        graphics.guiRenderState.submitGuiElement(new TextureRectPipelines(
                texture, new Matrix3x2f(graphics.pose()), x, y, x + width, y + height,
                color, graphics.scissorStack.peek()
        ));
    }

    // --------------------------------------------------------------- helpers

    public static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
