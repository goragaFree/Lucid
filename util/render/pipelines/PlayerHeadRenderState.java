package dev.lucid.util.render.pipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lucid.util.render.clientpipeline.LucidPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/** Голова и внешний слой скина в одном кваде, с настоящей округлой альфа-маской. */
public final class PlayerHeadRenderState implements GuiElementRenderState {
    private final RectRenderPipelines geometry;
    private final Identifier skin;
    private @Nullable TextureSetup texture;

    public PlayerHeadRenderState(GuiGraphics graphics, Identifier skin, float x, float y, float size, float radius) {
        this.skin = skin;
        geometry = new RectRenderPipelines(LucidPipelines.PLAYER_HEAD, new Matrix3x2f(graphics.pose()),
                x, y, x + size, y + size, Math.max(0f, Math.min(radius, size * .5f)), 0f,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, graphics.scissorStack.peek());
    }

    @Override public RenderPipeline pipeline() { return LucidPipelines.PLAYER_HEAD; }
    @Override public @Nullable ScreenRectangle bounds() { return geometry.bounds(); }
    @Override public @Nullable ScreenRectangle scissorArea() { return geometry.scissorArea(); }
    @Override public void buildVertices(VertexConsumer consumer) { geometry.buildVertices(consumer); }
    @Override public TextureSetup textureSetup() {
        if (texture == null) {
            var loaded = Minecraft.getInstance().getTextureManager().getTexture(skin);
            texture = TextureSetup.singleTexture(loaded.getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        }
        return texture;
    }
}
