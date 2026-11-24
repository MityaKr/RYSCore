package works.repka.ryscore.ui;

/**
 * Раскладка сегментного переключателя (1.18) в пикселях экрана, без игры: проверяется в JUnit. Края подложки и пунктов
 * прилипают к пикселям, отступ пункта от подложки один и тот же со всех четырёх сторон, промежутки между пунктами равны.
 * Ширины пунктов делятся между ними по долям, так что дробный масштаб (75 %, 125 %, «Авто») не съедает отступ у крайнего.
 */
final class SegmentLayout {
    /** Подложка: левый, верхний, правый, нижний край в пикселях. */
    final int x0, y0, x1, y1;
    /** Отступ пункта от края подложки и промежуток между пунктами, в пикселях. */
    final int inset, gap;
    /** Верх и низ всех пунктов. */
    final int top, bottom;
    /** Левый и правый край каждого пункта. */
    final int[] left, right;

    private SegmentLayout(int x0, int y0, int x1, int y1, int inset, int gap, int[] left, int[] right) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.inset = inset;
        this.gap = gap;
        this.top = y0 + inset;
        this.bottom = y1 - inset;
        this.left = left;
        this.right = right;
    }

    /** Ширина переключателя в единицах: отступы, пункты и промежутки. */
    static float width(float[] widths, float inset, float gap) {
        float w = inset * 2 + gap * Math.max(0, widths.length - 1);
        for (float v : widths) w += v;
        return w;
    }

    /**
     * @param s      пикселей экрана в единице
     * @param x      левый край в единицах
     * @param y      верх в единицах
     * @param h      высота подложки в единицах
     * @param widths ширины пунктов в единицах
     */
    static SegmentLayout of(double s, float x, float y, float h, float[] widths, float inset, float gap) {
        int n = widths.length;
        int px0 = (int) Math.round(x * s), py0 = (int) Math.round(y * s);
        int px1 = (int) Math.round((x + width(widths, inset, gap)) * s), py1 = (int) Math.round((y + h) * s);
        int ip = Math.max(1, (int) Math.round(inset * s));
        int gp = n > 1 ? Math.max(1, (int) Math.round(gap * s)) : 0;
        int[] left = new int[n], right = new int[n];
        int room = Math.max(0, px1 - px0 - ip * 2 - gp * Math.max(0, n - 1));
        float sum = 0;
        for (float v : widths) sum += Math.max(0, v);
        float acc = 0;
        int start = px0 + ip, edge = 0;
        for (int i = 0; i < n; i++) {
            acc += Math.max(0, widths[i]);
            int next = i == n - 1 ? room : sum > 0 ? Math.round(room * (acc / sum)) : Math.round(room * (i + 1f) / n);
            left[i] = start + edge + gp * i;
            right[i] = start + next + gp * i;
            edge = next;
        }
        return new SegmentLayout(px0, py0, px1, py1, ip, gp, left, right);
    }
}
