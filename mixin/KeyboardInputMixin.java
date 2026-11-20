package dev.lucid.mixin;

import dev.lucid.module.impl.render.FreeCam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FreeCam: гасит ввод игрока, пока камера-призрак летает.
 *
 * <p>{@code LocalPlayer} читает и {@code keyPresses} (прыжок, присед, спринт), и
 * {@code moveVector} (WASD). Обнуляем оба в TAIL {@code tick}, чтобы переписать то,
 * что ваниль только что посчитала по клавишам. Камера читает клавиши из
 * {@code Options} напрямую, поэтому полёт не страдает.</p>
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void lucid$freezeInput(CallbackInfo ci) {
        if (!FreeCam.shouldFreezeInput()) return;

        ((ClientInput) (Object) this).keyPresses =
                new Input(false, false, false, false, false, false, false);
        ((ClientInputAccess) (Object) this).setMoveVector(Vec2.ZERO);
    }
}
