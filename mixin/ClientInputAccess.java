package dev.lucid.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Доступ к protected {@code ClientInput#moveVector} — тот, что читает физика игрока.
 * Без него обнуление только {@code keyPresses} не останавливает WASD.
 */
@Mixin(ClientInput.class)
public interface ClientInputAccess {

    @Accessor("moveVector")
    void setMoveVector(Vec2 moveVector);
}
