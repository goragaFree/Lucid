package dev.lucid.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Проходы цепочки: Saturation меняет uniform своего прохода на лету. */
@Mixin(PostChain.class)
public interface PostChainAccess {
    @Accessor("passes")
    List<PostPass> lucid$getPasses();
}
