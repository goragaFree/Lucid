package dev.lucid.util.math;

import dev.lucid.util.render.WorldProjection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Мелкая математика кадра: интерполяция сущностей и сглаживание. */
public final class MathUtil {

    private MathUtil() {}

    /**
     * partialTick текущего кадра мира: с ним ваниль интерполировала сущности.
     *
     * <p>Захватывается в HEAD {@code LevelRenderer#renderLevel} (см. {@link WorldProjection}),
     * а вызывается это уже из отрисовки мира — там кадр всегда захвачен. До первого
     * кадра — ноль: интерполяция просто стоит на тиковой позиции.</p>
     */
    public static float partialTick() {
        return WorldProjection.isReady() ? WorldProjection.partialTick() : 0.0F;
    }

    /** Позиция сущности в этом кадре: между тиками, как её нарисовала игра. */
    public static Vec3 interpolate(Entity entity) {
        return entity.getPosition(partialTick());
    }

    /** Линейная интерполяция по доле тика. */
    public static double lerp(double from, double to, float partial) {
        return Mth.lerp(partial, from, to);
    }

    /** Сглаженный шаг 0..1: нулевая производная на обоих концах. */
    public static float smoothStep(float value) {
        return (float) Mth.smoothstep(value);
    }
}
