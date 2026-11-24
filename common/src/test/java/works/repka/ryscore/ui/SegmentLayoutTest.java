package works.repka.ryscore.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Отступ выбранного пункта от подложки равен со всех сторон при любом масштабе (замечание автора 24.11). */
class SegmentLayoutTest {
    /** 75–150 % при двух пикселях в единице, «Авто» на разных окнах, сжатая база и масштаб матрицы карты. */
    private static final double[] SCALES = {1.5, 2, 2.5, 3, 1.0, 1.125, 1.37, 1.6667, 2.2, 2.75, 3.33, 4};
    private static final float[][] WIDTHS = {
        {30.5f, 61.25f, 33f},
        {33f, 41.7f},
        {24f, 22.3f, 27.9f, 27.9f, 27.9f},
        {28.4f, 35.6f},
        {50f},
    };

    @Test
    void insetIsEqualOnAllSides() {
        for (double s : SCALES) {
            for (float[] widths : WIDTHS) {
                for (float inset : new float[] {1.5f, 2f}) {
                    for (float x : new float[] {0, 100.3f, 217.77f, 311.5f}) {
                        for (float y : new float[] {0, 41.25f, 88.6f, 133.33f}) {
                            SegmentLayout l = SegmentLayout.of(s, x, y, 17, widths, inset, 1);
                            String at = "s=" + s + " x=" + x + " y=" + y + " n=" + widths.length + " inset=" + inset;
                            int n = widths.length;
                            assertEquals(l.inset, l.left[0] - l.x0, "left " + at);
                            assertEquals(l.inset, l.x1 - l.right[n - 1], "right " + at);
                            assertEquals(l.inset, l.top - l.y0, "top " + at);
                            assertEquals(l.inset, l.y1 - l.bottom, "bottom " + at);
                            for (int i = 0; i < n; i++) {
                                assertTrue(l.right[i] > l.left[i], "empty item " + i + " " + at);
                                if (i > 0) assertEquals(l.gap, l.left[i] - l.right[i - 1], "gap " + i + " " + at);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void itemsKeepTheirShare() {
        SegmentLayout l = SegmentLayout.of(2.5, 10.2f, 5, 17, new float[] {20, 40}, 1.5f, 1);
        int a = l.right[0] - l.left[0], b = l.right[1] - l.left[1];
        assertTrue(Math.abs(b - 2 * a) <= 1, a + " " + b);
        assertEquals(Math.round((10.2f + SegmentLayout.width(new float[] {20, 40}, 1.5f, 1)) * 2.5), l.x1);
    }
}
