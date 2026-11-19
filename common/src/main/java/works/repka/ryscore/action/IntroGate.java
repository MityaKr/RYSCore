package works.repka.ryscore.action;

/**
 * Когда показывать разовую карточку (1.15.2), без Minecraft, чтобы проверять в JUnit.
 *
 * <p>Карточка показывается один раз на профиль игры: отметка «видел» ставится и сохраняется в момент первого показа,
 * дальше ничто её не снимает, ни закрытое окном, ни проигнорированное уведомление. В пределах этого первого показа
 * карточка, которую спрятало открытое окно семьи, возвращается через ту же задержку после закрытия окна, не больше
 * {@code returns} раз и только до выхода из мира. При следующих входах в мир и запусках игры её нет.
 */
final class IntroGate {
    /** Хранилище отметки «видел» (у карточки клавиш это {@code config/ryscore/ui.json}). */
    interface Store {
        boolean seen();

        /** Поставить отметку и сразу сохранить на диск. */
        void markSeen();
    }

    private enum State { WAIT_FIRST, VISIBLE, WAIT_RETURN, DONE }

    private final int delay;
    private final Store store;
    private int returnsLeft;
    private int ticks;
    private State state = State.WAIT_FIRST;

    IntroGate(int delayTicks, int returns, Store store) {
        this.delay = delayTicks;
        this.returnsLeft = returns;
        this.store = store;
    }

    /**
     * Тик клиента. {@code true}: показать карточку сейчас.
     *
     * @param inWorld игрок в мире
     * @param screenOpen открыто любое окно (задержка считается только без окна)
     */
    boolean tick(boolean inWorld, boolean screenOpen) {
        switch (state) {
            case WAIT_FIRST -> {
                if (!inWorld) {
                    ticks = 0;
                    return false;
                }
                if (screenOpen || ++ticks < delay) return false;
                if (store.seen()) {
                    state = State.DONE;
                    return false;
                }
                store.markSeen();
                state = State.VISIBLE;
                return true;
            }
            case WAIT_RETURN -> {
                if (!inWorld) {
                    state = State.DONE;
                    return false;
                }
                if (screenOpen || ++ticks < delay) return false;
                state = State.VISIBLE;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Спрятать ли видимую карточку. Открыто окно семьи: карточка уходит; пока остались возвраты, она придёт снова
     * через задержку после закрытия окна. Отметку «видел» не трогает.
     */
    boolean covered(boolean familyWindow) {
        if (!familyWindow) return false;
        if (state == State.VISIBLE) {
            ticks = 0;
            state = returnsLeft-- > 0 ? State.WAIT_RETURN : State.DONE;
        }
        return true;
    }
}
