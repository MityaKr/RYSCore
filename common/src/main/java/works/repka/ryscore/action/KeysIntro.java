package works.repka.ryscore.action;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Notice;
import works.repka.ryscore.ui.Onboarding;

/**
 * Карточка «Клавиши семьи» (1.15): один раз, через 5 с после первого входа в мир, уведомление семьи с главными клавишами
 * и кнопкой «Открыть клавиши». Флаг в {@code config/ryscore/ui.json}, больше не появляется.
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
            .millis(30_000).show();
    }
}
