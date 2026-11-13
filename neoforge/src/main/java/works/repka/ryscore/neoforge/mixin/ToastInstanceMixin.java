package works.repka.ryscore.neoforge.mixin;

import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import works.repka.ryscore.ui.Notices;

/**
 * Уведомления семьи стоят в колонке тостов игры, но шороха появления и ухода тоста у них нет: свой короткий звук играет ядро
 * ({@link Notices}), а строки RYScope и RYSTeams идут молча.
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceMixin {
    @Shadow
    @Final
    private Toast toast;

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;playSound(Lnet/minecraft/client/sounds/SoundManager;)V"))
    private void ryscore$quietNotice(Toast.Visibility visibility, SoundManager sounds) {
        if (!Notices.silent(toast)) visibility.playSound(sounds);
    }
}
