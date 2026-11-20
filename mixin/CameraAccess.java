package dev.lucid.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Доступ к protected {@code Camera#setPosition(Vec3)} и {@code #setRotation(float,float)}.
 *
 * <p>Наследовать целевой класс из миксина нельзя (валидация Mixin 0.8.7 это отвергает),
 * поэтому protected-сеттеры берём через {@link Invoker} из интерфейса.</p>
 */
@Mixin(Camera.class)
public interface CameraAccess {

    @Invoker("setPosition")
    void invokeSetPosition(Vec3 position);

    @Invoker("setRotation")
    void invokeSetRotation(float yaw, float pitch);
}
