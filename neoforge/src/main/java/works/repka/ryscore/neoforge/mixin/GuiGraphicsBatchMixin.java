package works.repka.ryscore.neoforge.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import works.repka.ryscore.ui.Batch;
import works.repka.ryscore.ui.BatchTarget;

/**
 * Пачка кора рисуется раньше любой отрисовки игры через GuiGraphics: так фигуры и текст кора ложатся
 * в том же порядке, в каком их вызвали. Заливки и текст игры сами вызывают {@code flush}, blit и предметы
 * рисуют сразу, ножницы меняют состояние, а выданный {@code bufferSource} кто-то заполнит сам.
 * Если в другой версии игры какое-то место пропадёт, кор просто рисует каждый квад сразу.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsBatchMixin implements BatchTarget {
    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Override
    public boolean ryscore$vanillaPending() {
        // без доступа к буферам считаем, что они заняты: лишний сброс лучше нарушенного порядка
        return !(bufferSource instanceof BufferSourceAccessor buffers) || !buffers.ryscore$startedBuilders().isEmpty();
    }

    @Inject(method = "flush()V", at = @At("HEAD"), require = 0)
    private void ryscore$flush(CallbackInfo ci) {
        Batch.beforeVanilla();
    }

    @Inject(method = "applyScissor", at = @At("HEAD"), require = 0)
    private void ryscore$scissor(CallbackInfo ci) {
        Batch.beforeVanilla();
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("HEAD"), require = 0)
    private void ryscore$blit(CallbackInfo ci) {
        Batch.beforeVanilla();
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V", at = @At("HEAD"), require = 0)
    private void ryscore$blitColored(CallbackInfo ci) {
        Batch.beforeVanilla();
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V", at = @At("HEAD"), require = 0)
    private void ryscore$item(CallbackInfo ci) {
        Batch.beforeVanilla();
    }

    @Inject(method = "bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;", at = @At("HEAD"), require = 0)
    private void ryscore$buffers(CallbackInfoReturnable<MultiBufferSource.BufferSource> cir) {
        Batch.beforeVanilla();
    }
}
