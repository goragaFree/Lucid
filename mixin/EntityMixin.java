package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.SeeInvisible;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    /** SeeInvisible: для нас никто не невидим — рендер сделает таких полупрозрачными. */
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void lucid$seeInvisible(Player player, CallbackInfoReturnable<Boolean> cir) {
        SeeInvisible module = Lucid.getInstance().getModuleManager().get(SeeInvisible.class);
        if (module != null && module.isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
