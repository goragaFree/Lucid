package dev.lucid.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lucid.Lucid;
import dev.lucid.module.impl.render.NoRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender → Screen → Fire: пламя перед лицом. Вода и блок в голове остаются штатными. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void lucid$noFire(PoseStack pose, MultiBufferSource buffers,
                                     TextureAtlasSprite sprite, CallbackInfo ci) {
        var client = Lucid.getInstance();
        if (client == null || client.getModuleManager() == null) return;
        NoRender noRender = client.getModuleManager().get(NoRender.class);
        if (noRender != null && noRender.hidesFire()) ci.cancel();
    }
}
