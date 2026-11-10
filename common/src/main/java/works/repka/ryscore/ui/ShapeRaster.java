package works.repka.ryscore.ui;

import java.util.Arrays;

/**
 * Скруглённые фигуры в пикселях экрана: построчная растеризация и заготовки для рисования девятью кусками.
 * Без зависимостей от игры, поэтому проверяется обычной программой. Рисует {@link Shapes}.
 */
final class ShapeRaster {
    private ShapeRaster() {}

    /** Приёмник заливок: прямоугольник от (x0, y0) до (x1, y1), не включая правый и нижний край. */
    interface Sink {
        void fill(int x0, int y0, int x1, int y1, int argb);
    }

    static double exactInset(int r, int row) {
        double dy = r - row - 0.5;
        return r - Math.sqrt(r * (double) r - dy * dy);
    }

    static int clampRadius(int r, int w, int h) {
        return Math.max(0, Math.min(r, Math.min(w, h) / 2));
    }

    static void fillRound(Sink g, int x, int y, int w, int h, int r, int color) {
        r = clampRadius(r, w, h);
        g.fill(x, y + r, x + w, y + h - r, color);
        int a = color >>> 24;
        for (int i = 0; i < r; i++) {
            double exact = exactInset(r, i);
            int dx = (int) Math.ceil(exact);
            g.fill(x + dx, y + i, x + w - dx, y + i + 1, color);
            g.fill(x + dx, y + h - 1 - i, x + w - dx, y + h - i, color);
            int aa = (int) (a * (dx - exact));
            if (aa > 6 && dx > 0) {
                int c2 = (aa << 24) | (color & 0xFFFFFF);
                g.fill(x + dx - 1, y + i, x + dx, y + i + 1, c2);
                g.fill(x + w - dx, y + i, x + w - dx + 1, y + i + 1, c2);
                g.fill(x + dx - 1, y + h - 1 - i, x + dx, y + h - i, c2);
                g.fill(x + w - dx, y + h - 1 - i, x + w - dx + 1, y + h - i, c2);
            }
        }
    }

    /** Толстая рамка такая же, как заливка: {@link #ring} в этом случае рисует {@link #fillRound}. */
    static boolean ringIsFill(int w, int h, int t) {
        return t * 2 >= Math.min(w, h);
    }

    static void ring(Sink g, int x, int y, int w, int h, int r, int t, int color) {
        r = clampRadius(r, w, h);
        if (ringIsFill(w, h, t)) {
            fillRound(g, x, y, w, h, r, color);
            return;
        }
        int corner = Math.max(r, t);
        // прямые участки боковых сторон
        g.fill(x, y + corner, x + t, y + h - corner, color);
        g.fill(x + w - t, y + corner, x + w, y + h - corner, color);
        int ri = Math.max(0, r - t);
        int a = color >>> 24;
        for (int i = 0; i < corner; i++) {
            double exact = i < r ? exactInset(r, i) : 0;
            int outer = (int) Math.ceil(exact);
            int innerRow = i - t;
            int inner;
            if (innerRow < 0) inner = -1;
            else inner = t + (innerRow < ri ? (int) Math.ceil(exactInset(ri, innerRow)) : 0);
            for (int pass = 0; pass < 2; pass++) {
                int yy = pass == 0 ? y + i : y + h - 1 - i;
                if (inner < 0) {
                    g.fill(x + outer, yy, x + w - outer, yy + 1, color);
                } else {
                    g.fill(x + outer, yy, x + inner, yy + 1, color);
                    g.fill(x + w - inner, yy, x + w - outer, yy + 1, color);
                }
                int aa = (int) (a * (outer - exact));
                if (aa > 6 && outer > 0) {
                    int c2 = (aa << 24) | (color & 0xFFFFFF);
                    g.fill(x + outer - 1, yy, x + outer, yy + 1, c2);
                    g.fill(x + w - outer, yy, x + w - outer + 1, yy + 1, c2);
                }
            }
        }
    }

    /** Прозрачность одного цвета, наложенная слоями, как при смешивании в игре. */
    static final class Canvas implements Sink {
        final int width, height;
        final float[] alpha;

        Canvas(int width, int height) {
            this.width = width;
            this.height = height;
            this.alpha = new float[width * height];
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            float a = (argb >>> 24) / 255f;
            if (a <= 0) return;
            int fx0 = Math.max(0, x0), fy0 = Math.max(0, y0), fx1 = Math.min(width, x1), fy1 = Math.min(height, y1);
            for (int y = fy0; y < fy1; y++) {
                int row = y * width;
                for (int x = fx0; x < fx1; x++) alpha[row + x] = a + alpha[row + x] * (1 - a);
            }
        }
    }

    /**
     * Заготовка: небольшой холст, у которого один столбец и одна строка растягиваются до нужного размера.
     * Слой: вид (0 заливка, 1 рамка), x, y, ширина, высота, радиус, толщина, прозрачность 0–255.
     */
    static final class Plan {
        final int width, height, stretchX, stretchY;
        final int[] layers;
        private final int hash;

        Plan(int width, int height, int stretchX, int stretchY, int[] layers) {
            this.width = width;
            this.height = height;
            this.stretchX = stretchX;
            this.stretchY = stretchY;
            this.layers = layers;
            this.hash = 31 * (31 * (31 * (31 * width + height) + stretchX) + stretchY) + Arrays.hashCode(layers);
        }

        Canvas paint() {
            Canvas c = new Canvas(width, height);
            for (int i = 0; i < layers.length; i += 8) {
                int color = layers[i + 7] << 24 | 0xFFFFFF;
                if (layers[i] == 0) fillRound(c, layers[i + 1], layers[i + 2], layers[i + 3], layers[i + 4], layers[i + 5], color);
                else ring(c, layers[i + 1], layers[i + 2], layers[i + 3], layers[i + 4], layers[i + 5], layers[i + 6], color);
            }
            return c;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Plan p && p.hash == hash && p.width == width && p.height == height
                && p.stretchX == stretchX && p.stretchY == stretchY && Arrays.equals(p.layers, layers);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /** Ось заготовки: размер холста и растягиваемый столбец, или -1, если фигура рисуется без растяжения. */
    private static int[] axis(int size, int before, int after) {
        int compact = before + 1 + after;
        return size > compact ? new int[] {compact, before} : new int[] {size, -1};
    }

    static Plan round(int w, int h, int r) {
        r = clampRadius(r, w, h);
        int[] ax = axis(w, r, r), ay = axis(h, r, r);
        return new Plan(ax[0], ay[0], ax[1], ay[1], new int[] {0, 0, 0, ax[0], ay[0], r, 0, 255});
    }

    static Plan ring(int w, int h, int r, int t) {
        if (ringIsFill(w, h, t)) return round(w, h, r);
        r = clampRadius(r, w, h);
        int corner = Math.max(r, t);
        int[] ax = axis(w, corner, corner), ay = axis(h, corner, corner);
        return new Plan(ax[0], ay[0], ax[1], ay[1], new int[] {1, 0, 0, ax[0], ay[0], r, t, 255});
    }

    /**
     * Несколько скруглённых заливок одного цвета: мягкая тень. {@code boxes} по пять чисел на слой
     * (x, y, ширина, высота, радиус) в пикселях экрана. {@code out} получает левый верхний угол и размер всей фигуры.
     */
    static Plan layered(int[] boxes, int alpha, int[] out) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int i = 0; i < boxes.length; i += 5) {
            x0 = Math.min(x0, boxes[i]);
            y0 = Math.min(y0, boxes[i + 1]);
            x1 = Math.max(x1, boxes[i] + boxes[i + 2]);
            y1 = Math.max(y1, boxes[i + 1] + boxes[i + 3]);
        }
        int left = 0, right = 0, top = 0, bottom = 0;
        for (int i = 0; i < boxes.length; i += 5) {
            int r = clampRadius(boxes[i + 4], boxes[i + 2], boxes[i + 3]);
            left = Math.max(left, boxes[i] - x0 + r);
            right = Math.max(right, x1 - boxes[i] - boxes[i + 2] + r);
            top = Math.max(top, boxes[i + 1] - y0 + r);
            bottom = Math.max(bottom, y1 - boxes[i + 1] - boxes[i + 3] + r);
        }
        int w = x1 - x0, h = y1 - y0;
        int[] ax = axis(w, left, right), ay = axis(h, top, bottom);
        int shrinkX = w - ax[0], shrinkY = h - ay[0];
        int[] layers = new int[boxes.length / 5 * 8];
        for (int i = 0, j = 0; i < boxes.length; i += 5, j += 8) {
            layers[j + 1] = boxes[i] - x0;
            layers[j + 2] = boxes[i + 1] - y0;
            layers[j + 3] = boxes[i + 2] - shrinkX;
            layers[j + 4] = boxes[i + 3] - shrinkY;
            layers[j + 5] = clampRadius(boxes[i + 4], boxes[i + 2], boxes[i + 3]);
            layers[j + 7] = alpha;
        }
        out[0] = x0;
        out[1] = y0;
        out[2] = w;
        out[3] = h;
        return new Plan(ax[0], ay[0], ax[1], ay[1], layers);
    }

    /**
     * Куски по одной оси: тройки (начало на экране, длина на экране, начало на холсте, длина на холсте).
     * Без растяжения кусок один.
     */
    static int[] slices(int size, int canvas, int stretch) {
        if (stretch < 0) return new int[] {0, size, 0, canvas};
        int middle = size - canvas + 1, after = canvas - stretch - 1;
        return new int[] {0, stretch, 0, stretch, stretch, middle, stretch, 1, stretch + middle, after, stretch + 1, after};
    }
}
