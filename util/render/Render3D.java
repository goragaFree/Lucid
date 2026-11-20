package dev.lucid.util.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lucid.Lucid;
import dev.lucid.util.render.clientpipeline.LucidPipelines;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Отрисовка геометрии в пространстве мира: линии, контуры и заливка боксов,
 * светящиеся линии.
 *
 * <p>Модуль регистрирует колбэк через {@link #register}, и каждый кадр он получает
 * {@link Frame} — живую ручку к буферам прохода сущностей. Все вершины подаются
 * относительно камеры: модельный вид уже стоит на матрице кадра, и ваниль кладёт
 * вершины так же (см. отладочные фигуры). Пайплайны — в {@link LucidPipelines}:
 * обычный цветной quad и текстурный quad со сложением яркости, у каждого свой
 * вариант без теста глубины.</p>
 *
 * <p>Момент вызова — AFTER_ENTITIES: сущности уже нарисованы, а полупрозрачный
 * ландшафт ещё нет. Линия читается поверх целей, а вода и стекло ложатся на неё
 * сверху, как на настоящую геометрию. Буферы после нас закрывает сам проход.</p>
 *
 * <p>Потребитель буфера не кэшируется: {@code BufferSource} закрывает батч при
 * каждом переключении типа, и запись в закрытый билдер роняет игру
 * («Not building!»). Каждый вызов рисования берёт буфер заново — повторный
 * {@code getBuffer} того же типа вернёт живой билдер, переключение типа само
 * закроет предыдущий батч, и оба случая корректны.</p>
 */
public final class Render3D {

    /** Радиальный градиент для светящейся линии: белый центр, прозрачный край. */
    private static final Identifier BLOOM_TEXTURE =
            Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "textures/world/bloom.png");

    private static final RenderType QUADS =
            RenderType.create("lucid_world_quad", RenderSetup.builder(LucidPipelines.WORLD_QUAD).createRenderSetup());
    private static final RenderType QUADS_SEETHROUGH = RenderType.create(
            "lucid_world_quad_no_depth",
            RenderSetup.builder(LucidPipelines.WORLD_QUAD_NO_DEPTH).createRenderSetup());
    private static final RenderType GLOW = RenderType.create(
            "lucid_world_glow",
            RenderSetup.builder(LucidPipelines.WORLD_GLOW).withTexture("Sampler0", BLOOM_TEXTURE).createRenderSetup());
    private static final RenderType GLOW_SEETHROUGH = RenderType.create(
            "lucid_world_glow_no_depth",
            RenderSetup.builder(LucidPipelines.WORLD_GLOW_NO_DEPTH)
                    .withTexture("Sampler0", BLOOM_TEXTURE)
                    .createRenderSetup());

    /** Вырожденный отрезок: короче этого направления не существует. */
    private static final double MIN_LENGTH = 1.0E-6;

    /**
     * Предел плотности свечения вдоль линии: на отрезок ставится не больше этого
     * числа биллбордов каждого слоя.
     */
    private static final int GLOW_MAX_STEPS = 10;

    /**
     * Пределы раздува свечения у камеры, в блоках.
     *
     * <p>У Rockstar всё, что ближе двух блоков от глаза, просто не рисуется — но у
     * нас линия прицела выходит из руки, и на её начале осталась бы дыра. Вместо
     * дыры свечение плавно набирает силу: ближе {@link #GLOW_FADE_NEAR} его нет
     * вовсе, дальше {@link #GLOW_FADE_FAR} оно полное. Тонкое ядро при этом гаснет
     * вместе с ореолом — линия «разгорается» по мере отхода от руки.</p>
     */
    private static final double GLOW_FADE_NEAR = 1.0;
    private static final double GLOW_FADE_FAR = 2.5;

    private static final List<Consumer<Frame>> CALLBACKS = new ArrayList<>();
    private static boolean hooked;

    private Render3D() {}

    /**
     * Регистрирует колбэк кадра. Вызывается один раз на модуль при создании:
     * колбэк живёт всегда и сам смотрит, включён ли его модуль.
     */
    public static void register(Consumer<Frame> callback) {
        CALLBACKS.add(callback);

        if (!hooked) {
            hooked = true;
            WorldRenderEvents.AFTER_ENTITIES.register(Render3D::afterEntities);
        }
    }

    private static void afterEntities(WorldRenderContext context) {
        if (CALLBACKS.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null) {
            return;
        }

        new Frame(context.consumers(), client.gameRenderer.getMainCamera()).draw();
    }

    /** Кадр отрисовки: живёт один кадр и раздаёт буферы по типам. */
    public static final class Frame {

        private final MultiBufferSource buffers;
        private final Vec3 camera;

        /** Базис камеры для биллбордов: правый и верхний векторы в мире. */
        private final Vector3f right;
        private final Vector3f up;

        private Frame(MultiBufferSource buffers, Camera camera) {
            this.buffers = buffers;
            this.camera = camera.position();

            Quaternionf rotation = camera.rotation();

            this.right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
            this.up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);
        }

        private void draw() {
            for (Consumer<Frame> callback : CALLBACKS) {
                callback.accept(this);
            }
        }

        /**
         * Ломаная с толщиной в блоках: на каждый отрезок — лента, развёрнутая
         * лицом к камере. Точки стоят плотно и изгиб плавный, поэтому стыки
         * соседних лент не видны; концы отрезков раздвинуты на пол-толщины,
         * чтобы в изломах не было щелей.
         *
         * @param points точки по три числа на точку
         * @param count  сколько точек брать
         * @param start  точка, с которой начинается видимая линия (голова)
         */
        public void polyline(
                double[] points,
                int count,
                double startX,
                double startY,
                double startZ,
                double thickness,
                int color,
                boolean throughWalls) {
            if (count < 2) {
                return;
            }

            VertexConsumer quads = this.buffers.getBuffer(throughWalls ? QUADS_SEETHROUGH : QUADS);
            double half = thickness * 0.5;

            double previousX = startX;
            double previousY = startY;
            double previousZ = startZ;

            for (int index = 0; index < count; index++) {
                double x = points[index * 3];
                double y = points[index * 3 + 1];
                double z = points[index * 3 + 2];

                this.ribbon(quads, previousX, previousY, previousZ, x, y, z, half, color);

                previousX = x;
                previousY = y;
                previousZ = z;
            }
        }

        /**
         * Светящаяся линия — визуал Rockstar: вдоль пути плотная цепочка
         * биллбордов с радиальным градиентом, сложение яркости вместо смешивания.
         *
         * <p>У Rockstar размеры биллбордов привязаны к длине тикового отрезка:
         * узкий яркий — треть отрезка, широкий тусклый — два отрезка, альфа ореола
         * в двадцать раз реже ядра. Стрела в начале полёта быстрая, и её линия
         * толстая; к концу полёта снаряд замедляется, и линия сужается — свечение
         * «тает» к точке попадания. Здесь та же привязка: {@code speeds} несёт
         * путь снаряда за тик у каждой точки, размеры выводятся из него теми же
         * долями, а шаг между биллбордами подстраивается, чтобы соседние
         * градиенты перекрывались так же плотно, как у десятки шагов Rockstar.</p>
         *
         * <p>Свечение у камеры плавно гаснет (см. {@link #GLOW_FADE_NEAR}): у руки
         * линия прицела только нарождается, а дальше горит как у Rockstar.</p>
         *
         * @param speeds путь снаряда за тик у каждой точки, в блоках; той же длины,
         *               что {@code points}, или null — тогда размер берётся из
         *               длины самого отрезка
         */
        public void glowLine(
                double[] points,
                int count,
                double startX,
                double startY,
                double startZ,
                float[] speeds,
                int color,
                float alpha,
                boolean throughWalls) {
            if (count < 2 || alpha <= 0.0F) {
                return;
            }

            VertexConsumer quads = this.buffers.getBuffer(throughWalls ? GLOW_SEETHROUGH : GLOW);

            double previousX = startX;
            double previousY = startY;
            double previousZ = startZ;
            double previousSpeed = speeds != null && speeds.length > 0 ? speeds[0] : 0.0;

            for (int index = 0; index < count; index++) {
                double x = points[index * 3];
                double y = points[index * 3 + 1];
                double z = points[index * 3 + 2];

                double dx = x - previousX;
                double dy = y - previousY;
                double dz = z - previousZ;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (length > MIN_LENGTH) {
                    // Путь за тик у отрезка: среднее двух его концов, а без данных —
                    // длина самого отрезка.
                    double speed = speeds != null && index < speeds.length
                            ? (previousSpeed + speeds[index]) * 0.5
                            : length;

                    // Те же доли, что у Rockstar: ядро — треть, ореол — два отрезка.
                    float core = (float) (speed / 3.0);
                    float halo = (float) (speed * 2.0);

                    // Плотность цепочки — как у их десяти шагов на отрезок: шаг не
                    // больше трети ядра. Медленное ядро узкое, и шагов нужно больше,
                    // поэтому число шагов подстраивается.
                    int steps = Math.max(1, Math.min(
                            GLOW_MAX_STEPS,
                            (int) Math.ceil(length / Math.max(core * 0.3, MIN_LENGTH))));

                    for (int step = 0; step < steps; step++) {
                        float part = (float) step / steps;

                        double atX = previousX + dx * part;
                        double atY = previousY + dy * part;
                        double atZ = previousZ + dz * part;

                        float fade = this.glowFade(atX, atY, atZ);

                        if (fade > 0.0F) {
                            this.billboard(quads, atX, atY, atZ, core, alpha * fade, color);
                            this.billboard(quads, atX, atY, atZ, halo, alpha * 0.05F * fade, color);
                        }
                    }
                }

                previousX = x;
                previousY = y;
                previousZ = z;
                previousSpeed = speeds != null && index < speeds.length ? speeds[index] : length;
            }
        }

        /** Заливка бокса: шесть граней, как у ванильных отладочных фигур. */
        public void filledBox(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                int color,
                boolean throughWalls) {
            VertexConsumer quads = this.buffers.getBuffer(throughWalls ? QUADS_SEETHROUGH : QUADS);

            this.quad(quads, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color);
            this.quad(quads, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, color);
            this.quad(quads, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color);
            this.quad(quads, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, color);
            this.quad(quads, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
            this.quad(quads, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, color);
        }

        /** Контур бокса: двенадцать рёбер теми же лентами, что и линия. */
        public void boxOutline(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                double thickness,
                int color,
                boolean throughWalls) {
            VertexConsumer quads = this.buffers.getBuffer(throughWalls ? QUADS_SEETHROUGH : QUADS);
            double half = thickness * 0.5;

            // Нижнее кольцо.
            this.ribbon(quads, minX, minY, minZ, maxX, minY, minZ, half, color);
            this.ribbon(quads, maxX, minY, minZ, maxX, minY, maxZ, half, color);
            this.ribbon(quads, maxX, minY, maxZ, minX, minY, maxZ, half, color);
            this.ribbon(quads, minX, minY, maxZ, minX, minY, minZ, half, color);

            // Верхнее кольцо.
            this.ribbon(quads, minX, maxY, minZ, maxX, maxY, minZ, half, color);
            this.ribbon(quads, maxX, maxY, minZ, maxX, maxY, maxZ, half, color);
            this.ribbon(quads, maxX, maxY, maxZ, minX, maxY, maxZ, half, color);
            this.ribbon(quads, minX, maxY, maxZ, minX, maxY, minZ, half, color);

            // Стойки.
            this.ribbon(quads, minX, minY, minZ, minX, maxY, minZ, half, color);
            this.ribbon(quads, maxX, minY, minZ, maxX, maxY, minZ, half, color);
            this.ribbon(quads, maxX, minY, maxZ, maxX, maxY, maxZ, half, color);
            this.ribbon(quads, minX, minY, maxZ, minX, maxY, maxZ, half, color);
        }

        /**
         * Лента одного отрезка: квад, развёрнутый плоскостью к камере.
         *
         * <p>Поперёк отрезка берётся направление, лежащее в плоскости кадра:
         * перпендикуляр к самому отрезку и к взгляду. Толщина от такой ленты
         * не зависит от угла — это мировая толщина, а не экранная. Если отрезок
         * смотрит точно в камеру, лента видна с торца и любой поперечник годится:
         * берётся правый вектор камеры.</p>
         */
        private void ribbon(
                VertexConsumer quads,
                double ax,
                double ay,
                double az,
                double bx,
                double by,
                double bz,
                double half,
                int color) {
            double dx = bx - ax;
            double dy = by - ay;
            double dz = bz - az;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (length < MIN_LENGTH) {
                return;
            }

            dx /= length;
            dy /= length;
            dz /= length;

            double mx = (ax + bx) * 0.5 - this.camera.x;
            double my = (ay + by) * 0.5 - this.camera.y;
            double mz = (az + bz) * 0.5 - this.camera.z;
            double view = Math.sqrt(mx * mx + my * my + mz * mz);

            double sideX;
            double sideY;
            double sideZ;

            if (view < MIN_LENGTH) {
                sideX = this.right.x;
                sideY = this.right.y;
                sideZ = this.right.z;
            } else {
                mx /= view;
                my /= view;
                mz /= view;

                // Перпендикуляр к плоскости «отрезок × взгляд» — поперёк ленты.
                sideX = dy * mz - dz * my;
                sideY = dz * mx - dx * mz;
                sideZ = dx * my - dy * mx;

                double side = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);

                if (side < MIN_LENGTH) {
                    sideX = this.right.x;
                    sideY = this.right.y;
                    sideZ = this.right.z;
                } else {
                    sideX /= side;
                    sideY /= side;
                    sideZ /= side;
                }
            }

            // Концы раздвинуты на пол-толщины вдоль отрезка: стыки соседних
            // лент перекрываются, и в изломах не прорезается щель.
            double extensionX = dx * half;
            double extensionY = dy * half;
            double extensionZ = dz * half;

            this.quad(
                    quads,
                    ax - extensionX - sideX * half, ay - extensionY - sideY * half, az - extensionZ - sideZ * half,
                    bx + extensionX - sideX * half, by + extensionY - sideY * half, bz + extensionZ - sideZ * half,
                    bx + extensionX + sideX * half, by + extensionY + sideY * half, bz + extensionZ + sideZ * half,
                    ax - extensionX + sideX * half, ay - extensionY + sideY * half, az - extensionZ + sideZ * half,
                    color);
        }

        /** Биллборд свечения: квад с градиентом, развёрнутый по базису камеры. */
        private void billboard(VertexConsumer quads, double x, double y, double z, float size, float alpha, int rgb) {
            int argb = ColorUtil.withAlpha(rgb, Math.round(alpha * 255.0F));

            if ((argb >>> 24) == 0) {
                return;
            }

            float half = size * 0.5F;

            float rightX = this.right.x * half;
            float rightY = this.right.y * half;
            float rightZ = this.right.z * half;

            float upX = this.up.x * half;
            float upY = this.up.y * half;
            float upZ = this.up.z * half;

            float atX = (float) (x - this.camera.x);
            float atY = (float) (y - this.camera.y);
            float atZ = (float) (z - this.camera.z);

            quads.addVertex(atX - rightX - upX, atY - rightY - upY, atZ - rightZ - upZ)
                    .setUv(0.0F, 0.0F)
                    .setColor(argb);
            quads.addVertex(atX + rightX - upX, atY + rightY - upY, atZ + rightZ - upZ)
                    .setUv(1.0F, 0.0F)
                    .setColor(argb);
            quads.addVertex(atX + rightX + upX, atY + rightY + upY, atZ + rightZ + upZ)
                    .setUv(1.0F, 1.0F)
                    .setColor(argb);
            quads.addVertex(atX - rightX + upX, atY - rightY + upY, atZ - rightZ + upZ)
                    .setUv(0.0F, 1.0F)
                    .setColor(argb);
        }

        /**
         * Во сколько раз свечение живо у этой точки: ноль у лица, единица дальше
         * {@link #GLOW_FADE_FAR}. Плавный шаг — без него свечение вспыхивало бы,
         * как только точка перевалила порог.
         */
        private float glowFade(double x, double y, double z) {
            double dx = x - this.camera.x;
            double dy = y - this.camera.y;
            double dz = z - this.camera.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            float fade = Mth.clamp(
                    (float) ((distance - GLOW_FADE_NEAR) / (GLOW_FADE_FAR - GLOW_FADE_NEAR)), 0.0F, 1.0F);

            return (float) Mth.smoothstep(fade);
        }

        private void quad(
                VertexConsumer quads,
                double ax,
                double ay,
                double az,
                double bx,
                double by,
                double bz,
                double cx,
                double cy,
                double cz,
                double dx,
                double dy,
                double dz,
                int color) {
            quads.addVertex((float) (ax - this.camera.x), (float) (ay - this.camera.y), (float) (az - this.camera.z))
                    .setColor(color);
            quads.addVertex((float) (bx - this.camera.x), (float) (by - this.camera.y), (float) (bz - this.camera.z))
                    .setColor(color);
            quads.addVertex((float) (cx - this.camera.x), (float) (cy - this.camera.y), (float) (cz - this.camera.z))
                    .setColor(color);
            quads.addVertex((float) (dx - this.camera.x), (float) (dy - this.camera.y), (float) (dz - this.camera.z))
                    .setColor(color);
        }
    }
}
