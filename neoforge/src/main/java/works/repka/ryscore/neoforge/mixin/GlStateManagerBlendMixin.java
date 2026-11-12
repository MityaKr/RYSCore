package works.repka.ryscore.neoforge.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import works.repka.ryscore.ui.GlState;

/** Запоминает смешивание и глубину: пачка кора возвращает их после себя такими, какими они были. */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerBlendMixin {
    @Inject(method = "_enableBlend", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$enableBlend(CallbackInfo ci) {
        GlState.onBlend(true);
    }

    @Inject(method = "_disableBlend", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$disableBlend(CallbackInfo ci) {
        GlState.onBlend(false);
    }

    @Inject(method = "_blendFunc", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$blendFunc(int src, int dst, CallbackInfo ci) {
        GlState.onBlendFunc(src, dst, src, dst);
    }

    @Inject(method = "_blendFuncSeparate", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$blendFuncSeparate(int src, int dst, int srcAlpha, int dstAlpha, CallbackInfo ci) {
        GlState.onBlendFunc(src, dst, srcAlpha, dstAlpha);
    }

    @Inject(method = "_enableDepthTest", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$enableDepth(CallbackInfo ci) {
        GlState.onDepth(true);
    }

    @Inject(method = "_disableDepthTest", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$disableDepth(CallbackInfo ci) {
        GlState.onDepth(false);
    }

    @Inject(method = "_depthFunc", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$depthFunc(int func, CallbackInfo ci) {
        GlState.onDepthFunc(func);
    }
}
