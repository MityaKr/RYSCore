package works.repka.ryscore.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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
 * ({@link Notices}), а строки RYSIndex и RYSTeams идут молча. С 1.17 место тоста решает ядро ({@link Notices#toastAt}):
 * уведомление семьи стоит, где выбрал игрок, и выезжает сверху; колонка «справа» встаёт под зону карты.
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceMixin {
    @Shadow
    @Final
    private Toast toast;

    @Shadow
    @Final
    int index;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void ryscore$place(PoseStack pose, float x, float y, float z) {
        float[] at = Notices.toastAt(toast, Minecraft.getInstance().getWindow().getGuiScaledWidth(), index, x, y);
        pose.translate(at[0], at[1], z);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;playSound(Lnet/minecraft/client/sounds/SoundManager;)V"))
    private void ryscore$quietNotice(Toast.Visibility visibility, SoundManager sounds) {
        if (!Notices.silent(toast)) visibility.playSound(sounds);
    }
}
