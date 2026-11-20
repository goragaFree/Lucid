package dev.lucid.mixin;

import dev.lucid.util.render.ItemPhysicState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicState {

    @Unique
    private float lucid$spinYaw;

    @Override
    public void lucid$setSpinYaw(float yaw) {
        this.lucid$spinYaw = yaw;
    }

    @Override
    public float lucid$spinYaw() {
        return this.lucid$spinYaw;
    }
}
