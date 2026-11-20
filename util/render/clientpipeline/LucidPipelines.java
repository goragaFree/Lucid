package dev.lucid.util.render.clientpipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.lucid.Lucid;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Все пайплайны клиента.
 *
 * Форма считается в шейдере rect формулой, а не треугольниками, поэтому любой
 * прямоугольник — всегда четыре вершины. Вид угла задаётся константой SHAPE
 * при сборке шейдера: вершинная программа одна, а пайплайнов из неё три — ROUND,
 * SQUIRCLE и SHADOW_SQUIRCLE (у последнего своя пиксельная часть, shadow.fsh).
 * Константа вместо переменной позволяет круглому варианту обойтись без pow().
 *
 * Регистрировать пайплайны не нужно: игра компилирует их при первом использовании
 * и сама пересобирает после перезагрузки ресурсов (F3+T).
 */
public final class LucidPipelines {

    /**
     * Формат вершины. Параметры фигуры едут в атрибутах, а не в uniform'ах —
     * иначе GUI не смог бы склеить много фигур в один draw call.
     *
     *   Position — позиция на экране (уже с pose)
     *   Color    — цвет ARGB
     *   UV0      — float2: позиция относительно центра фигуры
     *   UV1      — short2: половина ширины/высоты * 16
     *   UV2      — short2: радиус * 16, толщина обводки * 16 (0 = заливка)
     */
    public static final VertexFormat RECT_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .build();

    /**
     * Формат вершины текста: вместо размеров фигуры — место буквы в атласе (UV0)
     * и ширина полосы расстояний (UV1.x * 16). Она зависит от того, как пекли атлас,
     * поэтому передаётся данными, а не вбита в шейдер.
     */
    public static final VertexFormat TEXT_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .build();

    /** Обычное круглое скругление. Радиус 0 — обычный прямоугольник. */
    public static final RenderPipeline ROUND = create("round", 2);

    /**
     * Squircle: угол с плавно нарастающей кривизной, как у иконок Apple.
     * При том же радиусе выглядит «полнее», поэтому радиус ему обычно дают чуть больше.
     */
    public static final RenderPipeline SQUIRCLE = create("squircle", 5);

    /**
     * Тень под squircle-фигурой.
     *
     * Вершинная программа та же, что у фигур: тени нужны ровно те же атрибуты.
     * Отличается только пиксельная часть — вместо резкого края широкая полоса затухания,
     * а в слоте толщины обводки едет радиус размытия (см. shadow.fsh).
     */
    public static final RenderPipeline SHADOW_SQUIRCLE = create("shadow_squircle", "shadow", 5);

    /** Текст по MSDF-атласу. Единственный пайплайн клиента с текстурой (Sampler0). */
    public static final RenderPipeline TEXT = RenderPipeline.builder()
            .withLocation(id("pipeline/text"))
            .withVertexShader(id("core/text"))
            .withFragmentShader(id("core/text"))
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(TEXT_FORMAT, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    /** Округлая текстура головы: базовый слой и шапка скина за один draw. */
    public static final RenderPipeline PLAYER_HEAD = RenderPipeline.builder()
            .withLocation(id("pipeline/player_head"))
            .withVertexShader(id("core/rect"))
            .withFragmentShader(id("core/player_head"))
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(RECT_FORMAT, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    /**
     * Мировая геометрия Render3D: цветные квады в пространстве мира (лента линии,
     * контур и заливка боксов). Вершинный и пиксельный шейдеры ванильные, из
     * отладочных фигур, и сниппет матриц тот же, что у ванильной заливки боксов.
     *
     * <p>Глубина пишется false: квады рисуются после сущностей в том же проходе,
     * и писать в буфер глубины они не должны — иначе перекрыли бы полупрозрачный
     * ландшафт, который рисуется позже.</p>
     */
    public static final RenderPipeline WORLD_QUAD = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(id("pipeline/world_quad"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    /** То же, но без теста глубины: «сквозь стены». */
    public static final RenderPipeline WORLD_QUAD_NO_DEPTH = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(id("pipeline/world_quad_no_depth"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    /**
     * Светящаяся линия: биллборды с радиальным градиентом (bloom.png), сложение
     * яркости вместо смешивания — градиенты соседних биллбордов складываются в
     * сплошное свечение. {@code SRC_ALPHA, ONE} — как у молнии: цвет умножается
     * на свою альфу и целиком прибавляется к кадру.
     */
    public static final RenderPipeline WORLD_GLOW = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(id("pipeline/world_glow"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE))
            .withCull(false)
            .withDepthWrite(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    /** Светящаяся линия «сквозь стены». */
    public static final RenderPipeline WORLD_GLOW_NO_DEPTH = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(id("pipeline/world_glow_no_depth"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE))
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private LucidPipelines() {}

    private static RenderPipeline create(String name, int shape) {
        return create(name, "rect", shape);
    }

    private static RenderPipeline create(String name, String fragment, int shape) {
        return RenderPipeline.builder()
                .withLocation(id("pipeline/" + name))
                .withVertexShader(id("core/rect"))
                .withFragmentShader(id("core/" + fragment))
                .withShaderDefine("SHAPE", shape)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(RECT_FORMAT, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Lucid.MOD_ID, path);
    }
}
