package dev.lucid.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.lucid.module.impl.render.ItemPhysic;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemPhysic: физику считаем на TAIL ванильного {@code extractRenderState}, а сам
 * ванильный submit (hover + спин) отменяем целиком — его заменяет
 * {@link ItemPhysic#submit}. Дескрипторы полные: у обоих методов есть перегрузки.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Shadow
    @Final
    private RandomSource random;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void lucid$capturePhysics(ItemEntity entity, ItemEntityRenderState state,
                                      float partialTick, CallbackInfo ci) {
        ItemPhysic physics = ItemPhysic.get();
        if (physics != null) physics.capture(entity, state);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void lucid$replaceSubmit(ItemEntityRenderState state, PoseStack poseStack,
                                     SubmitNodeCollector collector, CameraRenderState camera,
                                     CallbackInfo ci) {
        ItemPhysic physics = ItemPhysic.get();
        if (physics != null && physics.submit(state, poseStack, collector, this.random)) {
            ci.cancel();
        }
    }
}
