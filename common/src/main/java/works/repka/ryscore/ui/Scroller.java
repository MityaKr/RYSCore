package works.repka.ryscore.ui;

/**
 * Плавная прокрутка списка, числа в единицах GUI. Если список уже у края и колесо крутят дальше, он слегка
 * уходит за границу и мягко возвращается. С пределом 0 это толчок для постраничных списков.
 */
public final class Scroller {
    private static final float EDGE = 5f;
    private static final float FOLLOW = 18f;
    private static final float RETURN = 12f;
    /** Ближе этого расстояния список считается стоящим у края. */
    private static final float AT_EDGE = 0.5f;

    private float target, position, max;
    private long last;

    /** Сдвиг от колеса: положительный листает к концу. */
    public void by(float delta) {
        if (delta > 0 && target >= max - AT_EDGE) target = max + EDGE;
        else if (delta < 0 && target <= AT_EDGE) target = -EDGE;
        else target = clamp(target + delta, 0, max);
    }

    /** Вызывается один раз за кадр. Возвращает положение для отрисовки, у края оно может слегка выходить за предел. */
    public float update(float limit) {
        limit(limit);
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        float rest = clamp(target, 0, max);
        if (target != rest) {
            target += (rest - target) * Math.min(1f, dt * RETURN);
            if (Math.abs(target - rest) < 0.05f) target = rest;
        }
        position += (target - position) * Math.min(1f, dt * FOLLOW);
        if (Math.abs(target - position) < 0.1f) position = target;
        return position;
    }

    /** Новый предел без анимации: нужен, когда длина списка изменилась. */
    public void limit(float limit) {
        max = Math.max(0, limit);
        if (target > max + EDGE || target < -EDGE) target = clamp(target, 0, max);
        if (position > max + EDGE || position < -EDGE) position = clamp(position, 0, max);
    }

    /** Сразу в указанное место, без анимации. */
    public void jump(float value) {
        target = position = clamp(value, 0, max);
    }

    public void reset() {
        target = position = 0;
    }

    public float position() {
        return position;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(value, high));
    }
}
