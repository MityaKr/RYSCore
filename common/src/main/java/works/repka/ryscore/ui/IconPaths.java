package works.repka.ryscore.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Значки, заданные контуром в квадрате 24×24, как в макетах (SVG с обводкой 2 и круглыми концами).
 * Контур один раз переводится в отрезки, значок рисуется по расстоянию до них: точка внутри, если ближе половины обводки.
 * Поддерживается подмножество команд пути SVG: M L H V C S A Z, строчные и прописные. Без зависимостей от игры.
 */
final class IconPaths {
    /** Половина обводки 2 в квадрате 24, в долях стороны: та же толщина, что у стрелки и лупы. */
    static final double HALF = 1.0 / 24.0;

    private IconPaths() {}

    /** Отрезки значка: по четыре числа (x0, y0, x1, y1) в долях стороны. */
    static double[] compile(String... parts) {
        List<double[]> out = new ArrayList<>();
        for (String part : parts) {
            if (part.startsWith("circle ")) {
                String[] v = part.substring(7).trim().split("\\s+");
                circle(out, d(v[0]), d(v[1]), d(v[2]));
            } else if (part.startsWith("rect ")) {
                String[] v = part.substring(5).trim().split("\\s+");
                rect(out, d(v[0]), d(v[1]), d(v[2]), d(v[3]), v.length > 4 ? d(v[4]) : 0);
            } else {
                path(out, part);
            }
        }
        double[] flat = new double[out.size() * 4];
        for (int i = 0; i < out.size(); i++) {
            double[] s = out.get(i);
            for (int k = 0; k < 4; k++) flat[i * 4 + k] = s[k] / 24.0;
        }
        return flat;
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    static boolean inside(double[] segs, double u, double v) {
        double h2 = HALF * HALF;
        for (int i = 0; i < segs.length; i += 4) {
            if (dist2(u, v, segs[i], segs[i + 1], segs[i + 2], segs[i + 3]) < h2) return true;
        }
        return false;
    }

    private static double dist2(double u, double v, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy;
        double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((u - x0) * dx + (v - y0) * dy) / l2));
        double ex = u - x0 - t * dx, ey = v - y0 - t * dy;
        return ex * ex + ey * ey;
    }

    private static void seg(List<double[]> out, double x0, double y0, double x1, double y1) {
        out.add(new double[] {x0, y0, x1, y1});
    }

    private static void circle(List<double[]> out, double cx, double cy, double r) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            seg(out, cx + r * Math.cos(a0), cy + r * Math.sin(a0), cx + r * Math.cos(a1), cy + r * Math.sin(a1));
        }
    }

    private static void rect(List<double[]> out, double x, double y, double w, double h, double r) {
        r = Math.min(r, Math.min(w, h) / 2);
        seg(out, x + r, y, x + w - r, y);
        seg(out, x + w, y + r, x + w, y + h - r);
        seg(out, x + w - r, y + h, x + r, y + h);
        seg(out, x, y + h - r, x, y + r);
        if (r > 0) {
            corner(out, x + w - r, y + r, r, -90);
            corner(out, x + w - r, y + h - r, r, 0);
            corner(out, x + r, y + h - r, r, 90);
            corner(out, x + r, y + r, r, 180);
        }
    }

    private static void corner(List<double[]> out, double cx, double cy, double r, double startDeg) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a0 = Math.toRadians(startDeg + 90.0 * i / n), a1 = Math.toRadians(startDeg + 90.0 * (i + 1) / n);
            seg(out, cx + r * Math.cos(a0), cy + r * Math.sin(a0), cx + r * Math.cos(a1), cy + r * Math.sin(a1));
        }
    }

    /** Разбор пути SVG. */
    private static void path(List<double[]> out, String d) {
        Tokens t = new Tokens(d);
        double x = 0, y = 0, sx = 0, sy = 0, lcx = 0, lcy = 0;
        char cmd = 'M';
        char last = ' ';
        while (t.more()) {
            if (t.isCommand()) cmd = t.command();
            boolean rel = Character.isLowerCase(cmd);
            char c = Character.toUpperCase(cmd);
            switch (c) {
                case 'M' -> {
                    double nx = t.num() + (rel ? x : 0), ny = t.num() + (rel ? y : 0);
                    x = sx = nx;
                    y = sy = ny;
                    // следующие пары после M считаются L
                    cmd = rel ? 'l' : 'L';
                }
                case 'L' -> {
                    double nx = t.num() + (rel ? x : 0), ny = t.num() + (rel ? y : 0);
                    seg(out, x, y, nx, ny);
                    x = nx;
                    y = ny;
                }
                case 'H' -> {
                    double nx = t.num() + (rel ? x : 0);
                    seg(out, x, y, nx, y);
                    x = nx;
                }
                case 'V' -> {
                    double ny = t.num() + (rel ? y : 0);
                    seg(out, x, y, x, ny);
                    y = ny;
                }
                case 'C', 'S' -> {
                    double x1, y1;
                    if (c == 'C') {
                        x1 = t.num() + (rel ? x : 0);
                        y1 = t.num() + (rel ? y : 0);
                    } else {
                        boolean smooth = last == 'C' || last == 'S';
                        x1 = smooth ? 2 * x - lcx : x;
                        y1 = smooth ? 2 * y - lcy : y;
                    }
                    double x2 = t.num() + (rel ? x : 0), y2 = t.num() + (rel ? y : 0);
                    double ex = t.num() + (rel ? x : 0), ey = t.num() + (rel ? y : 0);
                    cubic(out, x, y, x1, y1, x2, y2, ex, ey);
                    lcx = x2;
                    lcy = y2;
                    x = ex;
                    y = ey;
                }
                case 'A' -> {
                    double rx = t.num(), ry = t.num(), rot = t.num();
                    boolean large = t.flag(), sweep = t.flag();
                    double ex = t.num() + (rel ? x : 0), ey = t.num() + (rel ? y : 0);
                    arc(out, x, y, rx, ry, rot, large, sweep, ex, ey);
                    x = ex;
                    y = ey;
                }
                case 'Z' -> {
                    seg(out, x, y, sx, sy);
                    x = sx;
                    y = sy;
                }
                default -> throw new IllegalArgumentException("Path command not supported: " + cmd);
            }
            last = c;
        }
    }

    private static void cubic(List<double[]> out, double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3) {
        int n = 16;
        double px = x0, py = y0;
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n, m = 1 - t;
            double qx = m * m * m * x0 + 3 * m * m * t * x1 + 3 * m * t * t * x2 + t * t * t * x3;
            double qy = m * m * m * y0 + 3 * m * m * t * y1 + 3 * m * t * t * y2 + t * t * t * y3;
            seg(out, px, py, qx, qy);
            px = qx;
            py = qy;
        }
    }

    /** Дуга SVG по концам: перевод в центр и углы (SVG 1.1, приложение F.6). */
    private static void arc(List<double[]> out, double x1, double y1, double rx, double ry, double rotDeg, boolean large, boolean sweep, double x2, double y2) {
        if (rx == 0 || ry == 0) {
            seg(out, x1, y1, x2, y2);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(rotDeg), cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x1 - x2) / 2, dy = (y1 - y2) / 2;
        double x1p = cos * dx + sin * dy, y1p = -sin * dx + cos * dy;
        double lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry);
        if (lambda > 1) {
            double k = Math.sqrt(lambda);
            rx *= k;
            ry *= k;
        }
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coef = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
        double cxp = coef * rx * y1p / ry, cyp = -coef * ry * x1p / rx;
        double cx = cos * cxp - sin * cyp + (x1 + x2) / 2, cy = sin * cxp + cos * cyp + (y1 + y2) / 2;
        double a1 = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
        double a2 = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
        double delta = a2 - a1;
        if (sweep && delta < 0) delta += Math.PI * 2;
        if (!sweep && delta > 0) delta -= Math.PI * 2;
        int n = Math.max(4, (int) Math.ceil(Math.abs(delta) / (Math.PI / 16)));
        double px = x1, py = y1;
        for (int i = 1; i <= n; i++) {
            double a = a1 + delta * i / n;
            double ux = rx * Math.cos(a), uy = ry * Math.sin(a);
            double qx = cos * ux - sin * uy + cx, qy = sin * ux + cos * uy + cy;
            if (i == n) {
                qx = x2;
                qy = y2;
            }
            seg(out, px, py, qx, qy);
            px = qx;
            py = qy;
        }
    }

    /** Числа и команды пути: «M8.5 6H12a3 3 0 0 1 3 3v.6» и т. п. */
    private static final class Tokens {
        private final String s;
        private int i;

        Tokens(String s) {
            this.s = s;
        }

        private void skip() {
            while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == ',' || s.charAt(i) == '\n')) i++;
        }

        boolean more() {
            skip();
            return i < s.length();
        }

        boolean isCommand() {
            skip();
            return i < s.length() && Character.isLetter(s.charAt(i)) && s.charAt(i) != 'e' && s.charAt(i) != 'E';
        }

        char command() {
            skip();
            return s.charAt(i++);
        }

        boolean flag() {
            skip();
            return s.charAt(i++) == '1';
        }

        double num() {
            skip();
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            boolean dot = false;
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (Character.isDigit(ch)) {
                    i++;
                } else if (ch == '.' && !dot) {
                    dot = true;
                    i++;
                } else if ((ch == 'e' || ch == 'E') && i + 1 < s.length()) {
                    i++;
                    if (s.charAt(i) == '-' || s.charAt(i) == '+') i++;
                } else {
                    break;
                }
            }
            return Double.parseDouble(s.substring(start, i));
        }
    }
}
