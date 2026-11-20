package dev.lucid.mixin;

import dev.lucid.module.impl.render.FreeCam;
import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FreeCam: гасит авто-прыжок ({@code LocalPlayer.aiStep} вызывает {@code makeJump},
 * когда включена опция «Автопрыжок») — иначе игрок подпрыгивал бы, пока камера летает.
 */
@Mixin(ClientInput.class)
public abstract class ClientInputMixin {

    @Inject(method = "makeJump", at = @At("HEAD"), cancellable = true)
    private void lucid$freezeAutoJump(CallbackInfo ci) {
        if (FreeCam.shouldFreezeInput()) ci.cancel();
    }
}
