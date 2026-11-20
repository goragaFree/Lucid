package dev.lucid.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lucid.module.impl.render.ViewModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ViewModel: отдельный сдвиг для каждой руки от первого лица.
 *
 * <p>Точка врезки: сразу после первого {@code pushPose()} в {@code renderArmWithItem}.
 * Метод вызывается отдельно для основной и второй руки. Сдвиг кладётся в свежий
 * уровень стека и снимается парным {@code popPose()}, поэтому руки друг на друга
 * не влияют. Ванильные анимации (покачивание, взмах, экипировка) идут дальше
 * по стеку и работают поверх сдвига.</p>
 *
 * <p>Когда игрок смотрит в подзорную трубу, ваниль не вызывает {@code pushPose()},
 * и сдвиг тоже не применяется. Так и задумано.</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    ordinal = 0, shift = At.Shift.AFTER))
    private void lucid$applyViewModel(AbstractClientPlayer player, float partialTick, float pitch,
                                      InteractionHand hand, float swingProgress, ItemStack stack,
                                      float equipProgress, PoseStack poseStack,
                                      SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ViewModel module = ViewModel.get();
        if (module == null || !module.isEnabled()) return;

        // Как в ванили: сторона на экране, а не основная или вторая рука.
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();

        poseStack.translate(module.getOffsetX(arm), module.getOffsetY(arm), module.getOffsetZ(arm));
    }
}
