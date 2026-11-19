package works.repka.ryscore.ui;

import java.util.LinkedHashSet;

/**
 * Разовые подсказки семьи (1.15): что игрок уже видел, хранится в {@code config/ryscore/ui.json} (поле {@code seen}),
 * общее для всех миров и серверов. Только клиент.
 */
public final class Onboarding {
    /** Карточка «Клавиши семьи» при первом входе в мир. */
    public static final String KEYS = "keys";

    private Onboarding() {}

    public static boolean seen(String id) {
        UiConfig c = UiConfig.get();
        return c.seen != null && c.seen.contains(id);
    }

    public static void markSeen(String id) {
        UiConfig c = UiConfig.get();
        if (c.seen == null) c.seen = new LinkedHashSet<>();
        if (c.seen.add(id)) UiConfig.save();
    }

    /** Снова показать подсказку (кнопка в настройках, проверки). */
    public static void forget(String id) {
        UiConfig c = UiConfig.get();
        if (c.seen != null && c.seen.remove(id)) UiConfig.save();
    }
}
