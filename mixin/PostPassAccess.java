package dev.lucid.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** UBO из блока "uniforms" JSON постэффекта: имя блока → буфер. Карта изменяемая (HashMap). */
@Mixin(PostPass.class)
public interface PostPassAccess {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> lucid$getCustomUniforms();
}
