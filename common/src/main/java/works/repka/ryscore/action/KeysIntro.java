package works.repka.ryscore.action;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Notice;
import works.repka.ryscore.ui.Onboarding;
import works.repka.ryscore.ui.ScaledScreen;

/**
 * Карточка «Клавиши семьи» (1.15): через 5 с игры в мире без открытого окна уведомление семьи с главными клавишами и
 * кнопкой «Открыть клавиши». Один раз на профиль игры: флаг в {@code config/ryscore/ui.json} ставится и сохраняется в
 * момент первого показа, даже если карточку проигнорировали (1.15.2, логика в {@link IntroGate}).
 *
 * <p>С 1.15.1 не встаёт поверх окон семьи: если игрок открыл окно семьи, пока карточка видна, она уходит и один раз
 * вернётся через 5 с после закрытия окна, пока игрок в этом мире.
 */
final class KeysIntro {
    private static final int DELAY_TICKS = 100;
    private static final IntroGate GATE = new IntroGate(DELAY_TICKS, 1, new IntroGate.Store() {
        @Override
        public boolean seen() {
            return Onboarding.seen(Onboarding.KEYS);
        }

        @Override
        public void markSeen() {
            Onboarding.markSeen(Onboarding.KEYS);
        }
    });

    private KeysIntro() {}

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!GATE.tick(mc.player != null && mc.level != null, mc.screen != null)) return;
        String wheel = Actions.WHEEL.isUnbound() ? "—" : Binding.keyLabel(Binding.keyOf(Actions.WHEEL));
        String yes = Answers.acceptKey(), no = Answers.declineKey();
        Notice.buttons(Notice.CORE, "keys_intro").icon(Icons.Shape.KEYCAPS)
            .title(Component.translatable("ryscore.intro.keys.title").getString())
            .lines(Component.translatable("ryscore.intro.keys.long", wheel, yes, no).getString(),
                Component.translatable("ryscore.intro.keys.short", wheel).getString())
            .button(Component.translatable("ryscore.intro.keys.open").getString(), true, () -> Actions.openKeys(null))
            .button(Component.translatable("ryscore.intro.keys.ok").getString(), false, () -> {})
            .hiddenWhen(() -> GATE.covered(Minecraft.getInstance().screen instanceof ScaledScreen))
            .millis(30_000).show();
    }
}
