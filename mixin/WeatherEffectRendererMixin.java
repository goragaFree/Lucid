package dev.lucid.mixin;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.Ambience;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * В ванили вид осадков зависит от биома: в тайге снег, в дубовом лесу дождь.
 * Ambience подменяет ответ, поэтому снег идёт где угодно.
 *
 * <p>Метод private и зовётся из двух мест — из отрисовки колонок и из спавна
 * частиц у земли, — так что подмену видят обе ветки и частицы не расходятся с картинкой.</p>
 */
@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void lucid$forcePrecipitation(Level level, BlockPos pos,
                                          CallbackInfoReturnable<Biome.Precipitation> cir) {
        Ambience ambience = Lucid.getInstance().getModuleManager().get(Ambience.class);
        if (ambience == null || !ambience.isEnabled()) return;
        Biome.Precipitation forced = ambience.getForcedPrecipitation();
        if (forced == null) return;
        // Незагруженный чанк — колонки рисовать негде, ваниль в этом случае тоже отдаёт NONE
        if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()))) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }
        cir.setReturnValue(forced);
    }
}
