package works.repka.ryscore.neoforge.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import works.repka.ryscore.ui.Scissor;

/** Запоминает состояние обрезки, чтобы кор не спрашивал его у драйвера в каждом кадре. */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerScissorMixin {
    @Inject(method = "_enableScissorTest", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$enable(CallbackInfo ci) {
        Scissor.onEnable(true);
    }

    @Inject(method = "_disableScissorTest", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$disable(CallbackInfo ci) {
        Scissor.onEnable(false);
    }

    @Inject(method = "_scissorBox", at = @At("HEAD"), require = 0, remap = false)
    private static void ryscore$box(int x, int y, int width, int height, CallbackInfo ci) {
        Scissor.onBox(x, y, width, height);
    }
}
