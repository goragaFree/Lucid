package dev.lucid.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.lucid.util.render.WorldProjection;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Забирает у ванили матрицы кадра для проекции ESP.
 *
 * <p>Инъекция в HEAD, а не в GUI: к моменту {@code Gui#render} игра уже переключила
 * проекцию на ортографическую, а CPU-матриц мира наружу не отдаёт. Здесь же они лежат
 * в аргументах ровно в том виде, в каком уйдут в рендер уровня.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void lucid$captureFrame(GraphicsResourceAllocator allocator, DeltaTracker delta,
                                    boolean renderBlockOutline, Camera camera,
                                    Matrix4f viewMatrix, Matrix4f projectionMatrix,
                                    Matrix4f cullProjectionMatrix, GpuBufferSlice fogBuffer,
                                    Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        // false — как в самом renderLevel: игровой, а не реальный partialTick.
        WorldProjection.capture(camera, viewMatrix, projectionMatrix,
                delta.getGameTimeDeltaPartialTick(false));
    }
}
