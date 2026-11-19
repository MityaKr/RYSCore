package works.repka.ryscore.action;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Notice;
import works.repka.ryscore.ui.Onboarding;
import works.repka.ryscore.ui.ScaledScreen;

/**
 * Карточка «Клавиши семьи» (1.15): один раз, через 5 с игры в мире без открытого окна, уведомление семьи с главными
 * клавишами и кнопкой «Открыть клавиши». Флаг в {@code config/ryscore/ui.json}, больше не появляется.
 *
 * <p>С 1.15.1 не встаёт поверх окон семьи (ночные проверки 19.11: закрывала шапку окна «Сервер» и кнопки карты). Пять
 * секунд считаются только без открытого окна; если игрок открыл окно семьи, пока карточка видна, она уходит и вернётся
 * через 5 с после закрытия окна.
 */
final class KeysIntro {
    private static final int DELAY_TICKS = 100;
    private static int ticks;

    private KeysIntro() {}

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            ticks = 0;
            return;
        }
        if (mc.screen != null) return;
        if (ticks > DELAY_TICKS || ++ticks < DELAY_TICKS) return;
        ticks = DELAY_TICKS + 1;
        if (Onboarding.seen(Onboarding.KEYS)) return;
        Onboarding.markSeen(Onboarding.KEYS);
        String wheel = Actions.WHEEL.isUnbound() ? "—" : Binding.keyLabel(Binding.keyOf(Actions.WHEEL));
        String yes = Answers.acceptKey(), no = Answers.declineKey();
        Notice.buttons(Notice.CORE, "keys_intro").icon(Icons.Shape.LETTER_YO)
            .title(Component.translatable("ryscore.intro.keys.title").getString())
            .lines(Component.translatable("ryscore.intro.keys.long", wheel, yes, no).getString(),
                Component.translatable("ryscore.intro.keys.short", wheel).getString())
            .button(Component.translatable("ryscore.intro.keys.open").getString(), true, () -> Actions.openKeys(null))
            .button(Component.translatable("ryscore.intro.keys.ok").getString(), false, () -> {})
            .hiddenWhen(KeysIntro::covered)
            .millis(30_000).show();
    }

    /** Открыто окно семьи: карточка уходит, флаг снимается, счёт пяти секунд начнётся заново после закрытия окна. */
    private static boolean covered() {
        if (!(Minecraft.getInstance().screen instanceof ScaledScreen)) return false;
        Onboarding.forget(Onboarding.KEYS);
        ticks = 0;
        return true;
    }
}
