package works.repka.ryscore.action;

/**
 * Чистая часть колеса (1.16): сколько места у подписи в секторе, как подпись сокращается, с какой стороны встаёт подменю
 * и когда его открывает наведение. Без Minecraft, проверяется JUnit.
 */
final class WheelLayout {
    /** Кегль подписи сектора и чуть меньший, до которого она сжимается (не мельче подписей окон семьи). */
    static final float LABEL_PX = 13, LABEL_PX_SMALL = 12;
    /** Задержка мыши на секторе, после которой подменю открывается само (только в режиме «по зажатию»). */
    static final long HOVER_OPEN_MS = 450;

    private WheelLayout() {}

    /** Ширина текста в единицах GUI при кегле {@code px}. */
    interface Measure {
        float width(String s, float px);
    }

    /** Подпись сектора: текст, кегль и признак, что показано не полное имя. */
    record Fit(String text, float px, boolean shortened) {}

    /**
     * Подпись, которая помещается в {@code room}: полное имя, затем оно же чуть мельче, затем короткое имя действия
     * (тоже сначала обычным, потом мельче), и только в крайнем случае многоточие.
     */
    static Fit fit(String full, String shortName, float room, Measure m) {
        if (m.width(full, LABEL_PX) <= room) return new Fit(full, LABEL_PX, false);
        if (m.width(full, LABEL_PX_SMALL) <= room) return new Fit(full, LABEL_PX_SMALL, false);
        boolean hasShort = shortName != null && !shortName.isBlank() && !shortName.equals(full);
        if (hasShort) {
            if (m.width(shortName, LABEL_PX) <= room) return new Fit(shortName, LABEL_PX, true);
            if (m.width(shortName, LABEL_PX_SMALL) <= room) return new Fit(shortName, LABEL_PX_SMALL, true);
        }
        return new Fit(ellipsize(hasShort ? shortName : full, room, LABEL_PX_SMALL, m), LABEL_PX_SMALL, true);
    }

    /** Обрезает по символам с конца и ставит «…»; пробел перед многоточием не оставляет. */
    static String ellipsize(String s, float room, float px, Measure m) {
        if (m.width(s, px) <= room) return s;
        int end = s.length();
        while (end > 0) {
            end = s.offsetByCodePoints(end, -1);
            String t = s.substring(0, end).stripTrailing() + "…";
            if (m.width(t, px) <= room) return t;
        }
        return "…";
    }

    /**
     * Сколько в ширину помещается подпись высотой {@code h} с центром ({@code lx}, {@code ly}) от центра колеса в секторе
     * {@code sector} (0 сверху, по часовой) кольца {@code ir}…{@code r} с зазором {@code gapDeg} между секторами и отступом {@code pad}
     * от краёв. 0, если не помещается даже точка.
     */
    static float labelRoom(int sector, float lx, float ly, float h, float ir, float r, float gapDeg, float pad) {
        if (!rectInside(sector, lx, ly, 0, h, ir, r, gapDeg, pad)) return 0;
        float lo = 0, hi = r * 2;
        for (int k = 0; k < 24; k++) {
            float mid = (lo + hi) / 2f;
            if (rectInside(sector, lx, ly, mid, h, ir, r, gapDeg, pad)) lo = mid;
            else hi = mid;
        }
        return lo * 2;
    }

    /** Лежит ли прямоугольник (центр, полуширина {@code hw}, высота {@code h}) целиком в секторе с отступом. */
    static boolean rectInside(int sector, float cx, float cy, float hw, float h, float ir, float r, float gapDeg, float pad) {
        float x0 = cx - hw, x1 = cx + hw, y0 = cy - h / 2f, y1 = cy + h / 2f;
        // внешняя дуга: дальше всего от центра угол прямоугольника
        float[][] corners = {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}};
        double c = Math.toRadians(-90 + sector * 45.0), half = Math.toRadians(22.5 - gapDeg / 2.0);
        double ax = Math.cos(c - half), ay = Math.sin(c - half), bx = Math.cos(c + half), by = Math.sin(c + half);
        for (float[] p : corners) {
            if (Math.hypot(p[0], p[1]) > r - pad) return false;
            // клин сектора выпуклый: хватает углов прямоугольника
            if (ax * p[1] - ay * p[0] < pad) return false;
            if (p[0] * by - p[1] * bx < pad) return false;
        }
        // внутренняя дуга: ближе всего к центру точка прямоугольника, ближайшая к началу координат
        float nx = Math.max(x0, Math.min(0, x1)), ny = Math.max(y0, Math.min(0, y1));
        return Math.hypot(nx, ny) >= ir + pad;
    }

    /**
     * Левый край подменю шириной {@code w}: справа от колеса (центр {@code cx}, занятый радиус {@code reach}), если помещается
     * в экран {@code screenW} с полем {@code margin}; иначе слева; если нет места ни там, ни там, у правого края поверх колеса.
     */
    static float subX(float cx, float reach, float w, float screenW, float margin) {
        float right = cx + reach;
        if (right + w <= screenW - margin) return right;
        float left = cx - reach - w;
        if (left >= margin) return left;
        return Math.max(margin, screenW - margin - w);
    }

    /** Верх подменю высотой {@code h}: по центру колеса, но внутри экрана. */
    static float subY(float cy, float h, float screenH, float margin) {
        float y = cy - h / 2f;
        return Math.max(margin, Math.min(y, screenH - margin - h));
    }

    /** Угол знака подменю от середины сектора и шаг точек набора. */
    static final float MARK_DEG = 16, MARK_STEP_DEG = 3.5f;

    /**
     * Угол знака подменю от середины сектора, по часовой: у внешнего края в верхнем углу сектора, подальше от названия
     * под значком (справа против часовой, слева по часовой, сверху и снизу правый угол).
     */
    static float markDeg(int sector) {
        int s = Math.floorMod(sector, 8);
        return s >= 1 && s <= 4 ? -MARK_DEG : MARK_DEG;
    }

    /** Точка на радиусе {@code r} под углом {@code offsetDeg} от середины сектора {@code sector}, от центра колеса. */
    static float[] polar(int sector, float offsetDeg, float r) {
        double a = Math.toRadians(-90 + sector * 45.0 + offsetDeg);
        return new float[] {(float) (Math.cos(a) * r), (float) (Math.sin(a) * r)};
    }

    /** Смещение значка с названием от середины кольца: в верхней половине чуть наружу, где сектор шире. */
    static float contentRadius(int sector, float ir, float r) {
        return (r + ir) / 2f - 5 * Math.min(0, (float) Math.sin(Math.toRadians(-90 + sector * 45.0)));
    }

    /**
     * Открывает ли задержка мыши на секторе его подменю. Только в режиме «по зажатию»: в режиме «по нажатию» наведение
     * лишь подсвечивает, подменю открывает клик (автор, 20.11.2026).
     */
    static boolean hoverOpens(boolean holdMode, boolean openForThis, boolean dismissedHere, long hoveredMs) {
        return holdMode && !openForThis && !dismissedHere && hoveredMs >= HOVER_OPEN_MS;
    }
}
