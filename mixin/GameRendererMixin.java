package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.CameraSetting;
import dev.lucid.module.impl.render.FreeCam;
import dev.lucid.module.impl.render.Saturation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Unique private static final Identifier LUCID$SATURATION =
            Identifier.fromNamespaceAndPath("lucid", "saturation");
    @Unique private static final String LUCID$UBO = "SaturationConfig";

    /** Цепочка, в которую уже подложен свой UBO; после F3+T ShaderManager даёт новую. */
    @Unique private PostChain lucid$preparedChain;
    @Unique private GpuBuffer lucid$saturationUbo;

    /**
     * Saturation: первый clearDepthTexture в render — граница «мир / GUI»: мир и ванильный
     * постэффект уже нарисованы, интерфейс ещё нет. Так насыщенность не трогает HUD и меню.
     */
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
                    ordinal = 0))
    private void lucid$saturation(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        if (!renderLevel || minecraft.level == null) return;
        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return;
        Saturation module = lucid.getModuleManager().get(Saturation.class);
        if (module == null || !module.isEnabled()) return;

        PostChain chain = minecraft.getShaderManager().getPostChain(LUCID$SATURATION, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) return;
        if (chain != lucid$preparedChain && !lucid$prepare(chain)) return;

        int size = new Std140SizeCalculator().putFloat().putFloat().get();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, size).putFloat(module.getSaturation())
                    .putFloat(module.getContrast()).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(lucid$saturationUbo.slice(), data);
        }
        chain.process(minecraft.getMainRenderTarget(), resourcePool);
    }

    /**
     * Ванильный UBO прохода создан только с USAGE_UNIFORM, писать в него нельзя.
     * Заменяем его своим, с USAGE_COPY_DST; старый закрываем. Новый закроет PostPass.close
     * вместе с цепочкой при перезагрузке ресурсов.
     */
    @Unique
    private boolean lucid$prepare(PostChain chain) {
        int size = new Std140SizeCalculator().putFloat().putFloat().get();
        for (PostPass pass : ((PostChainAccess) (Object) chain).lucid$getPasses()) {
            Map<String, GpuBuffer> uniforms = ((PostPassAccess) (Object) pass).lucid$getCustomUniforms();
            GpuBuffer old = uniforms.get(LUCID$UBO);
            if (old == null) continue;
            GpuBuffer ubo = RenderSystem.getDevice().createBuffer(() -> "lucid saturation",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
            uniforms.put(LUCID$UBO, ubo);
            old.close();
            lucid$saturationUbo = ubo;
            lucid$preparedChain = chain;
            return true;
        }
        return false;
    }

    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F",
            at = @At("RETURN"), cancellable = true)
    private void lucid$heldZoom(Camera camera, float partialTick, boolean useSettingFov,
                                CallbackInfoReturnable<Float> cir) {
        // The false branch is the hand projection; leave its FOV untouched.
        if (!useSettingFov) return;
        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return;
        CameraSetting settings = lucid.getModuleManager().get(CameraSetting.class);
        if (settings != null) cir.setReturnValue(settings.applyZoom(cir.getReturnValueF()));
    }

    /** FreeCam: рука с предметом у летящей камеры выглядела бы чужеродно. */
    @Inject(method = "renderItemInHand(FZLorg/joml/Matrix4f;)V", at = @At("HEAD"),
            cancellable = true)
    private void lucid$hideHand(float partialTick, boolean firstPerson, Matrix4f matrix,
                                CallbackInfo ci) {
        if (FreeCam.shouldHideHand()) ci.cancel();
    }
}
