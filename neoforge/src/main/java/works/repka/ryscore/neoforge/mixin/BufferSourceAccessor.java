package works.repka.ryscore.neoforge.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Начатые и ещё не нарисованные буферы игры: пачка кора не должна их обогнать. */
@Mixin(MultiBufferSource.BufferSource.class)
public interface BufferSourceAccessor {
    @Accessor("startedBuilders")
    Map<RenderType, BufferBuilder> ryscore$startedBuilders();
}
