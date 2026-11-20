package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.CameraSetting;
import dev.lucid.module.impl.render.FreeCam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private boolean lucid$initialRotation;

    @Inject(method = "setup", at = @At("HEAD"))
    private void lucid$beginSetup(Level level, Entity entity, boolean detached,
                                 boolean mirrored, float partialTick, CallbackInfo ci) {
        lucid$initialRotation = entity == Minecraft.getInstance().player;
    }

    @ModifyArgs(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void lucid$freeLookRotation(Args args) {
        // 1.21.11 has two mutually exclusive initial setRotation call sites
        // (normal / vehicle camera). Modify the first EXECUTED call only.
        // Later front-view mirroring must use vanilla's yaw+180 / -pitch.
        if (!lucid$initialRotation) return;
        lucid$initialRotation = false;
        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return;
        CameraSetting cameraSettings = lucid.getModuleManager().get(CameraSetting.class);
        if (cameraSettings == null || !cameraSettings.isLooking()) return;
        args.set(0, cameraSettings.getCameraYaw());
        args.set(1, cameraSettings.getCameraPitch());
        // Vanilla then computes camera distance and wall collision using these angles.
    }

    @Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true)
    private void lucid$cameraClip(float requestedDistance, CallbackInfoReturnable<Float> cir) {
        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return;
        CameraSetting settings = lucid.getModuleManager().get(CameraSetting.class);
        if (settings != null && settings.isClipEnabled()) {
            // Skip only vanilla's wall-distance clamp, not the camera's positioning.
            cir.setReturnValue(requestedDistance);
        }
    }

    /**
     * FreeCam владеет камерой: ванильный {@code setup} уже поставил её у глаз игрока,
     * а мы в TAIL переливаем свою позицию и углы — после этого ни коллизий, ни
     * физики у камеры нет. Protected-сеттеры берём через {@link CameraAccess}
     * (наследовать целевой класс из миксина нельзя).
     */
    @Inject(method = "setup", at = @At("TAIL"))
    private void lucid$freeCamSetup(Level level, Entity entity, boolean detached,
                                    boolean mirrored, float partialTick, CallbackInfo ci) {
        FreeCam freeCam = FreeCam.get();
        if (freeCam == null || !freeCam.isActive() || entity != Minecraft.getInstance().player) {
            return;
        }

        freeCam.advanceCamera((Camera) (Object) this);
        ((CameraAccess) this).invokeSetPosition(freeCam.getCameraPosition());
        ((CameraAccess) this).invokeSetRotation(freeCam.getCameraYaw(), freeCam.getCameraPitch());
    }

    /**
     * «Показывать себя» во FreeCam: ваниль не рисует сущность камеры, пока камера не
     * отцеплена. Говорим, что отцеплена, — и тело игрока появляется на своём месте.
     */
    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void lucid$freeCamShowSelf(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCam.shouldRenderSelf()) cir.setReturnValue(true);
    }
}
