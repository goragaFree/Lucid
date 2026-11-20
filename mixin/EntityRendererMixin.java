package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.NameTags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Пока NameTags рисует свой тег игроку — ванильный ник над ним не нужен. */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    // require = 0: если в версии сигнатура другая, просто останется ванильный ник, игра не упадёт.
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true, require = 0)
    private void lucid$hideVanillaName(Entity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        NameTags module = Lucid.getInstance().getModuleManager().get(NameTags.class);
        if (module != null && module.replacesVanilla(entity)) cir.setReturnValue(false);
    }
}
