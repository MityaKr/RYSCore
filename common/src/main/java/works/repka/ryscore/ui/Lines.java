package works.repka.ryscore.ui;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * Линии под углом, пунктир, ломаные, дуги и кольца для {@link Draw}. Каждая полоса это квады с цветом в вершинах:
 * середина сплошная, по краю полоса в один пиксель экрана, которая уходит в прозрачность. Так край сглажен
 * при любом угле и масштабе, а всё уходит в общую пачку ({@link Batch}) с белым участком атласа.
 *
 * <p>Толщина {@code w} пикселей экрана даёт сплошную середину {@code w - 1} и по полупрозрачному пикселю с каждой
 * стороны: на глаз это ровно {@code w}. Линия тоньше пикселя рисуется шириной в пиксель с долей прозрачности.
 */
final class Lines {
    /** Во сколько раз угол ломаной может вытянуть стык, прежде чем его срежет. */
    private static final float MITER_LIMIT = 4f;

    private Lines() {}

    /** Общие данные одного вызова: матрица, белая точка атласа, размер пикселя экрана в единицах. */
    private static final class Pen {
        final GuiGraphics g;
        final Matrix4f m;
        final float u, v, px;
        final int page;

        Pen(GuiGraphics g) {
            this.g = g;
            this.m = g.pose().last().pose();
            Atlas.Region white = Atlas.white();
            this.u = white.u(1.5f);
            this.v = white.v(1.5f);
            this.page = white.page();
            this.px = (float) (1.0 / Draw.scale(g));
        }

        void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c0, int c1, int c2, int c3) {
            Batch.quad4(g, m, x0, y0, x1, y1, x2, y2, x3, y3, c0, c1, c2, c3, u, v, page);
        }
    }

    /** Половина сплошной середины и цвет с учётом линии тоньше пикселя: {середина, цвет}. */
    private static float core(Pen pen, float width, int[] argb) {
        float wpx = width / pen.px;
        if (wpx < 1f) {
            argb[0] = Draw.alpha(argb[0], wpx);
            wpx = 1f;
        }
        return (wpx - 1f) * 0.5f * pen.px;
    }

    static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int argb) {
        if ((argb >>> 24) == 0 || width <= 0) return;
        Pen pen = new Pen(g);
        Batch.hold();
        try {
            segment(pen, x0, y0, x1, y1, width, argb);
        } finally {
            Batch.release();
        }
    }

    /** Много отдельных отрезков: по четыре числа на отрезок (x0, y0, x1, y1). Цвета по одному на отрезок или один на всех. */
    static void lines(GuiGraphics g, float[] xy, int segments, float width, int argb, int[] colors) {
        if (width <= 0 || segments <= 0) return;
        Pen pen = new Pen(g);
        Batch.hold();
        try {
            for (int i = 0; i < segments; i++) {
                int c = colors != null ? colors[i] : argb;
                if ((c >>> 24) == 0) continue;
                int k = i * 4;
                segment(pen, xy[k], xy[k + 1], xy[k + 2], xy[k + 3], width, c);
            }
        } finally {
            Batch.release();
        }
    }

    private static void segment(Pen pen, float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6f) return;
        int[] c = {argb};
        float half = core(pen, width, c);
        int solid = c[0], clear = solid & 0x00FFFFFF;
        float nx = -dy / len, ny = dx / len;
        float ax = nx * half, ay = ny * half;
        float bx = nx * (half + pen.px), by = ny * (half + pen.px);
        if (half > 1e-6f) {
            pen.quad(x0 + ax, y0 + ay, x1 + ax, y1 + ay, x1 - ax, y1 - ay, x0 - ax, y0 - ay, solid, solid, solid, solid);
        }
        pen.quad(x0 + ax, y0 + ay, x0 + bx, y0 + by, x1 + bx, y1 + by, x1 + ax, y1 + ay, solid, clear, clear, solid);
        pen.quad(x0 - ax, y0 - ay, x0 - bx, y0 - by, x1 - bx, y1 - by, x1 - ax, y1 - ay, solid, clear, clear, solid);
    }

    static void dashed(GuiGraphics g, float x0, float y0, float x1, float y1, float width, float dash, float gap, float phase, int argb) {
        if ((argb >>> 24) == 0 || width <= 0) return;
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6f) return;
        if (dash <= 0 || gap <= 0) {
            line(g, x0, y0, x1, y1, width, argb);
            return;
        }
        Pen pen = new Pen(g);
        float ux = dx / len, uy = dy / len, period = dash + gap;
        // сдвиг узора: положительный двигает штрихи вперёд по линии
        float start = -(((phase % period) + period) % period);
        Batch.hold();
        try {
            for (float t = start; t < len; t += period) {
                float a = Math.max(0, t), b = Math.min(len, t + dash);
                if (b - a <= 1e-4f) continue;
                segment(pen, x0 + ux * a, y0 + uy * a, x0 + ux * b, y0 + uy * b, width, argb);
            }
        } finally {
            Batch.release();
        }
    }

    /**
     * Ломаная толщиной {@code width} со стыками без зазоров и наложений: соседние отрезки делят точки края.
     * {@code xy} по два числа на точку, {@code closed} замыкает последнюю точку с первой.
     */
    static void polyline(GuiGraphics g, float[] xy, int points, boolean closed, float width, int argb) {
        if ((argb >>> 24) == 0 || width <= 0 || points < 2) return;
        Pen pen = new Pen(g);
        int[] c = {argb};
        float half = core(pen, width, c);
        int solid = c[0], clear = solid & 0x00FFFFFF;
        // направление сдвига края в каждой точке и его длина
        float[] mx = new float[points], my = new float[points], mk = new float[points];
        for (int i = 0; i < points; i++) {
            int prev = i - 1, next = i + 1;
            if (closed) {
                prev = (i - 1 + points) % points;
                next = (i + 1) % points;
            }
            float[] n0 = prev >= 0 ? normal(xy, prev, i) : null;
            float[] n1 = next < points ? normal(xy, i, next) : null;
            if (n0 == null && n1 == null) n0 = new float[] {0, 0};
            if (n0 == null) n0 = n1;
            if (n1 == null) n1 = n0;
            float sx = n0[0] + n1[0], sy = n0[1] + n1[1];
            float sl = (float) Math.sqrt(sx * sx + sy * sy);
            if (sl < 1e-5f) {
                // разворот назад: край ставим по нормали входящего отрезка
                mx[i] = n0[0];
                my[i] = n0[1];
                mk[i] = 1f;
                continue;
            }
            sx /= sl;
            sy /= sl;
            float cos = sx * n0[0] + sy * n0[1];
            mx[i] = sx;
            my[i] = sy;
            mk[i] = Math.min(MITER_LIMIT, cos > 1e-4f ? 1f / cos : MITER_LIMIT);
        }
        int count = closed ? points : points - 1;
        Batch.hold();
        try {
            for (int s = 0; s < count; s++) {
                int i = s, j = (s + 1) % points;
                float xi = xy[i * 2], yi = xy[i * 2 + 1], xj = xy[j * 2], yj = xy[j * 2 + 1];
                if (xi == xj && yi == yj) continue;
                float ai = half * mk[i], aj = half * mk[j], bi = (half + pen.px) * mk[i], bj = (half + pen.px) * mk[j];
                float aix = mx[i] * ai, aiy = my[i] * ai, ajx = mx[j] * aj, ajy = my[j] * aj;
                float bix = mx[i] * bi, biy = my[i] * bi, bjx = mx[j] * bj, bjy = my[j] * bj;
                if (half > 1e-6f) pen.quad(xi + aix, yi + aiy, xj + ajx, yj + ajy, xj - ajx, yj - ajy, xi - aix, yi - aiy, solid, solid, solid, solid);
                pen.quad(xi + aix, yi + aiy, xi + bix, yi + biy, xj + bjx, yj + bjy, xj + ajx, yj + ajy, solid, clear, clear, solid);
                pen.quad(xi - aix, yi - aiy, xi - bix, yi - biy, xj - bjx, yj - bjy, xj - ajx, yj - ajy, solid, clear, clear, solid);
            }
        } finally {
            Batch.release();
        }
    }

    private static float[] normal(float[] xy, int a, int b) {
        float dx = xy[b * 2] - xy[a * 2], dy = xy[b * 2 + 1] - xy[a * 2 + 1];
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6f) return null;
        return new float[] {-dy / len, dx / len};
    }

    /** Сколько отрезков нужно дуге радиуса {@code radiusPx} пикселей, чтобы хорда отходила от окружности не больше чем на четверть пикселя. */
    static int steps(float radiusPx, float sweepRad) {
        if (radiusPx <= 0.5f) return Math.max(3, (int) Math.ceil(Math.abs(sweepRad) / (Math.PI / 4)));
        double step = 2 * Math.acos(Math.max(-1, 1 - 0.25 / radiusPx));
        int n = (int) Math.ceil(Math.abs(sweepRad) / Math.max(step, 1e-3));
        return Math.max(3, Math.min(720, n));
    }

    /** Дуга по средней линии радиуса {@code radius}: начало и размах в градусах, отсчёт от оси x по часовой стрелке (y вниз). */
    static void arc(GuiGraphics g, float cx, float cy, float radius, float startDeg, float sweepDeg, float width, int argb) {
        if ((argb >>> 24) == 0 || width <= 0 || radius <= 0 || sweepDeg == 0) return;
        if (Math.abs(sweepDeg) >= 360f) {
            ring(g, cx, cy, radius + width / 2f, width, argb);
            return;
        }
        float px = (float) (1.0 / Draw.scale(g));
        double a0 = Math.toRadians(startDeg), sweep = Math.toRadians(sweepDeg);
        int n = steps(radius / px, (float) sweep);
        float[] xy = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = a0 + sweep * i / n;
            xy[i * 2] = cx + (float) (Math.cos(a) * radius);
            xy[i * 2 + 1] = cy + (float) (Math.sin(a) * radius);
        }
        polyline(g, xy, n + 1, false, width, argb);
    }

    /**
     * Кольцо: внешний радиус {@code radius}, толщина внутрь {@code thickness}. Толщина не меньше радиуса даёт круг.
     * Края с обеих сторон сглажены; у круга внутреннего края нет.
     */
    static void ring(GuiGraphics g, float cx, float cy, float radius, float thickness, int argb) {
        if ((argb >>> 24) == 0 || radius <= 0 || thickness <= 0) return;
        Pen pen = new Pen(g);
        float p = pen.px;
        float tpx = Math.min(thickness, radius) / p;
        int solid = argb;
        if (tpx < 1f) {
            solid = Draw.alpha(argb, tpx);
            thickness = p;
        }
        int clear = solid & 0x00FFFFFF;
        float outer = radius, inner = radius - thickness;
        boolean disc = inner <= 0;
        // сплошная часть между полупикселями от краёв, мягкие края по пикселю
        float coreOut = outer - p * 0.5f, coreIn = disc ? 0 : inner + p * 0.5f;
        if (coreIn > coreOut) coreIn = coreOut = (coreIn + coreOut) / 2f;
        int n = steps(radius / p, (float) (Math.PI * 2));
        float[] cos = new float[n + 1], sin = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            cos[i] = (float) Math.cos(a);
            sin[i] = (float) Math.sin(a);
        }
        Batch.hold();
        try {
            band(pen, cx, cy, cos, sin, n, coreIn, coreOut, solid, solid);
            band(pen, cx, cy, cos, sin, n, coreOut, outer + p * 0.5f, solid, clear);
            if (!disc) band(pen, cx, cy, cos, sin, n, Math.max(0, inner - p * 0.5f), coreIn, clear, solid);
        } finally {
            Batch.release();
        }
    }

    /** Полоса между радиусами r0 и r1 с цветом c0 у внутреннего края и c1 у внешнего. */
    private static void band(Pen pen, float cx, float cy, float[] cos, float[] sin, int n, float r0, float r1, int c0, int c1) {
        if (r1 - r0 <= 1e-6f) return;
        for (int i = 0; i < n; i++) {
            float ca = cos[i], sa = sin[i], cb = cos[i + 1], sb = sin[i + 1];
            pen.quad(cx + ca * r0, cy + sa * r0, cx + ca * r1, cy + sa * r1, cx + cb * r1, cy + sb * r1, cx + cb * r0, cy + sb * r0, c0, c1, c1, c0);
        }
    }
}
