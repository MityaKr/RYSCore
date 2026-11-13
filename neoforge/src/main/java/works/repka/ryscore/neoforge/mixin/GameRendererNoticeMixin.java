package works.repka.ryscore.neoforge.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import works.repka.ryscore.ui.Notices;

/**
 * Пока курсор лежит на уведомлении семьи, окно под ним не видит мыши: не подсвечивает слот и не показывает подсказку предмета
 * под карточкой. Так же NeoForge прячет мышь от нижних слоёв окна ({@code Integer.MAX_VALUE}).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererNoticeMixin {
    @ModifyArgs(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void ryscore$hideMouseUnderNotice(Args args) {
        int x = args.get(2), y = args.get(3);
        if (Notices.over(x, y)) {
            args.set(2, Integer.MAX_VALUE);
            args.set(3, Integer.MAX_VALUE);
        }
    }
}
