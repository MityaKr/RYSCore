package works.repka.ryscore.ui;

/**
 * Где стоит уведомление семьи (1.17): чистый расчёт без игры, под JUnit. Всё в единицах GUI игры.
 *
 * <p>Тост игры выезжает справа; уведомление семьи стоит на своём месте и выезжает сверху вниз из-под верхнего края своего
 * места, уходит вверх туда же (место обрезает его сверху, поэтому соседей выше оно не задевает). Ход берётся у колонки
 * тостов игры: она двигает тост по x как {@code ширина экрана - ширина × ход}, ход 0…1 (появление) и 1…0 (уход).
 */
final class NoticePlace {
    /** Высота места колонки тостов игры. */
    static final int SLOT = 32;
    /** Зазор между уведомлениями в своей стопке и от верхней зоны (карта, боссбары). */
    static final int GAP = 2;

    private NoticePlace() {}

    /** Ход выезда по x, который поставила колонка тостов игры. */
    static float progress(int screenW, int toastW, float vanillaX) {
        if (toastW <= 0) return 1;
        return clamp((screenW - vanillaX) / toastW);
    }

    /** Левый край уведомления. */
    static float x(Notices.Place place, int screenW, int toastW) {
        return switch (place) {
            case RIGHT -> screenW - toastW;
            case CENTER -> Math.round((screenW - toastW) / 2f);
            case LEFT -> 0;
        };
    }

    /** Верх с выездом: при ходе 0 уведомление целиком над своим местом, при 1 на месте. */
    static float slide(float top, int toastH, float progress) {
        return top - (1 - clamp(progress)) * (toastH + GAP);
    }

    /** Верх места в своей стопке: верх зоны и высоты уведомлений выше с зазором. */
    static float stack(float base, int... heightsAbove) {
        float y = base;
        for (int h : heightsAbove) y += h + GAP;
        return y;
    }

    /** Верх стопки под занятой зоной высотой {@code zone}: без зоны от края экрана, иначе с зазором. */
    static float base(float zone) {
        return zone > 0 ? zone + GAP : 0;
    }

    /** Плавный подъезд места к цели, когда уведомление выше ушло: за 120 мс почти до конца. */
    static float approach(float current, float target, long dtMillis) {
        if (Float.isNaN(current)) return target;
        float k = Math.min(1f, Math.max(0, dtMillis) / 60f);
        float next = current + (target - current) * k;
        return Math.abs(target - next) < 0.25f ? target : next;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : Math.min(1, v);
    }
}
