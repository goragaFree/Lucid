package dev.lucid.util.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Привязка атласа шрифта к элементу отрисовки.
 *
 * Картинка грузится менеджером текстур игры как обычный ресурс — при первом обращении,
 * отдельно регистрировать её не нужно.
 */
public final class FontTexture {

    private FontTexture() {}

    public static TextureSetup of(Identifier texture) {
        AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
        return TextureSetup.singleTexture(loaded.getTextureView(), sampler());
    }

    /**
     * Плавная выборка с прижатием к краю. Поле расстояний работает только с LINEAR:
     * промежуточные значения между пикселями и дают гладкий край буквы. Прижатие к краю —
     * чтобы соседние буквы в атласе не подтекали друг в друга.
     *
     * Кэш отдаёт один и тот же объект на одинаковые настройки, поэтому строки
     * с одним атласом склеиваются в одну пачку.
     */
    private static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }
}
