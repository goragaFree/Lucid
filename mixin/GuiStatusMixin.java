package dev.lucid.mixin;

import dev.lucid.sreen.hud.HotbarStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Цветные квадраты заменяют сердца, защиту и голод; воздух и транспорт сохранены. */
@Mixin(Gui.class)
public abstract class GuiStatusMixin {
    @Unique private boolean lucid$vehicleOffset;

    // Штатные пузырьки и сердца транспорта сохраняются, но не накладываются
    // на новые квадраты и исходное положение Armor.
    @ModifyArg(method = "renderPlayerHealth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderAirBubbles(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;III)V"), index = 3)
    private int lucid$airLine(int y) {
        if (!HotbarStatus.active()) return y;
        var player = Minecraft.getInstance().player;
        return y - (player != null && player.isPassenger() ? 90 : 40);
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"))
    private void lucid$vehicleTop(GuiGraphics graphics, CallbackInfo ci) {
        lucid$vehicleOffset = HotbarStatus.active();
        if (lucid$vehicleOffset) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0f, -60f);
        }
    }

    @Inject(method = "renderVehicleHealth", at = @At("RETURN"))
    private void lucid$vehicleBottom(GuiGraphics graphics, CallbackInfo ci) {
        if (lucid$vehicleOffset) {
            graphics.pose().popMatrix();
            lucid$vehicleOffset = false;
        }
    }

    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
    private void lucid$health(GuiGraphics graphics, Player player, int x, int y, int spacing,
                              int regeneration, float maxHealth, int health, int displayHealth,
                              int absorption, boolean blink, CallbackInfo ci) {
        if (HotbarStatus.active()) ci.cancel();
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void lucid$food(GuiGraphics graphics, Player player, int y, int x, CallbackInfo ci) {
        if (HotbarStatus.active()) ci.cancel();
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void lucid$defense(GuiGraphics graphics, Player player, int y,
                                      int rows, int spacing, int x, CallbackInfo ci) {
        if (HotbarStatus.active()) ci.cancel();
    }

    @Redirect(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"))
    private void lucid$experienceLevel(GuiGraphics graphics, Font font, int level) {
        if (HotbarStatus.active()) HotbarStatus.level(graphics, level);
        else ContextualBarRenderer.renderExperienceLevel(graphics, font, level);
    }
}
