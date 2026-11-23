package works.repka.ryscore.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Колесо 1.16: сокращение подписи, место в секторе, сторона подменю, наведение по режиму. */
class WheelLayoutTest {
    /** Каждый символ шириной в кегль, делённый на 3: «Пинг команде» (12) при 13 = 52, при 12 = 48. */
    private static final WheelLayout.Measure M = (s, px) -> s.codePointCount(0, s.length()) * px / 3f;
    private static final float R = 88, IR = 36, GAP = 1.6f;

    @Test
    void fullNameWhenFits() {
        WheelLayout.Fit f = WheelLayout.fit("Дом", "Д", 60, M);
        assertEquals("Дом", f.text());
        assertEquals(WheelLayout.LABEL_PX, f.px());
        assertFalse(f.shortened());
    }

    @Test
    void smallerFontBeforeShortName() {
        WheelLayout.Fit f = WheelLayout.fit("Пинг команде", "Пинг", 49, M);
        assertEquals("Пинг команде", f.text());
        assertEquals(WheelLayout.LABEL_PX_SMALL, f.px());
    }

    @Test
    void shortNameThenEllipsis() {
        WheelLayout.Fit f = WheelLayout.fit("Пинг команде", "Пинг", 40, M);
        assertEquals("Пинг", f.text());
        assertEquals(WheelLayout.LABEL_PX, f.px());
        assertTrue(f.shortened());
        // короткое имя не влезает и мельче: многоточие по короткому имени
        WheelLayout.Fit tight = WheelLayout.fit("Инвентарь игрока", "Инвентарь", 30, M);
        assertTrue(tight.text().endsWith("…"));
        assertTrue(M.width(tight.text(), tight.px()) <= 30);
        assertTrue(tight.text().startsWith("Инвен"));
    }

    @Test
    void ellipsisWithoutShortName() {
        WheelLayout.Fit f = WheelLayout.fit("Случайное место", "", 36, M);
        assertEquals("Случайно…", f.text());
        assertEquals(WheelLayout.LABEL_PX_SMALL, f.px());
        // пробел перед многоточием не остаётся
        assertEquals("Дом…", WheelLayout.ellipsize("Дом команды", 20, 12, M));
    }

    @Test
    void labelRoomInsideSectors() {
        // под значком верхнего сектора место уже, чем у нижнего: нижний расширяется наружу
        float topRoom = WheelLayout.labelRoom(0, 0, -51, 7, IR, R, GAP, 2.5f);
        float bottomRoom = WheelLayout.labelRoom(4, 0, 73, 7, IR, R, GAP, 2.5f);
        assertTrue(topRoom > 20 && topRoom < bottomRoom, topRoom + " / " + bottomRoom);
        // боковой сектор: подпись ниже оси, место меньше ширины кольца
        float side = WheelLayout.labelRoom(2, 62, 11, 7, IR, R, GAP, 2.5f);
        assertTrue(side > 20 && side < R - IR, Float.toString(side));
        // все восемь секторов симметричны: противоположные равны
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(-90 + i * 45);
            float x = (float) Math.cos(a) * 62, y = (float) Math.sin(a) * 62;
            float room = WheelLayout.labelRoom(i, x, y + 11, 7, IR, R, GAP, 2.5f);
            assertTrue(room > 10, "сектор " + i + ": " + room);
        }
        // в чужом секторе места нет
        assertEquals(0, WheelLayout.labelRoom(0, 0, 60, 7, IR, R, GAP, 2.5f));
    }

    @Test
    void rectInsideRespectsInnerCircle() {
        assertTrue(WheelLayout.rectInside(4, 0, 62, 5, 7, IR, R, GAP, 2));
        assertFalse(WheelLayout.rectInside(4, 0, 36, 5, 7, IR, R, GAP, 2));
        assertFalse(WheelLayout.rectInside(4, 0, 87, 5, 7, IR, R, GAP, 2));
    }

    @Test
    void subMarkStaysClearOfIconAndLabel() {
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(-90 + i * 45);
            float cr = WheelLayout.contentRadius(i, IR, R);
            float x = (float) Math.cos(a) * cr, y = (float) Math.sin(a) * cr;
            float top = y - 17, tile = 19, ly = top + tile + 6;
            float room = WheelLayout.labelRoom(i, x, ly, 7, IR, R, GAP, 2.5f);
            for (int k = -1; k <= 1; k++) {
                float[] p = WheelLayout.polar(i, WheelLayout.markDeg(i) + k * WheelLayout.MARK_STEP_DEG, R - 6);
                // шеврон 5×5 в середине, точки набора по краям, целиком в секторе
                float half = k == 0 ? 2.5f : 1.2f;
                assertTrue(WheelLayout.rectInside(i, p[0], p[1], half, half * 2, IR, R, GAP, 0.5f), "сектор " + i + " вне");
                // не на плашке значка (с числом над правым углом) и не на названии
                assertFalse(overlap(p[0], p[1], 2.5f, x - tile / 2f, top - 3, x + tile / 2f + 4, top + tile), "сектор " + i + " на значке");
                assertFalse(overlap(p[0], p[1], 2.5f, x - room / 2f, ly - 3.5f, x + room / 2f, ly + 3.5f), "сектор " + i + " на названии");
            }
        }
    }

    private static boolean overlap(float px, float py, float h, float x0, float y0, float x1, float y1) {
        return px + h > x0 && px - h < x1 && py + h > y0 && py - h < y1;
    }

    @Test
    void submenuGoesRightThenLeft() {
        // колесо по центру экрана 640: справа помещается
        assertEquals(320 + 102, WheelLayout.subX(320, 102, 160, 640, 6));
        // колесо у правого края: слева
        assertEquals(500 - 102 - 160, WheelLayout.subX(500, 102, 160, 640, 6));
        // нигде не помещается: у правого края поверх колеса, не за экраном
        assertEquals(400 - 6 - 160, WheelLayout.subX(200, 102, 160, 400, 6));
        assertEquals(6, WheelLayout.subX(80, 102, 160, 150, 6));
    }

    @Test
    void submenuStaysOnScreenVertically() {
        assertEquals(194 - 100, WheelLayout.subY(194, 200, 400, 6));
        assertEquals(6, WheelLayout.subY(50, 200, 400, 6));
        assertEquals(400 - 6 - 200, WheelLayout.subY(380, 200, 400, 6));
    }

    @Test
    void hoverOpensOnlyInHoldMode() {
        assertTrue(WheelLayout.hoverOpens(true, false, false, 500));
        assertFalse(WheelLayout.hoverOpens(false, false, false, 5000), "по нажатию наведение только подсвечивает");
        assertFalse(WheelLayout.hoverOpens(true, false, false, 100), "раньше задержки");
        assertFalse(WheelLayout.hoverOpens(true, true, false, 500), "уже открыто");
        assertFalse(WheelLayout.hoverOpens(true, false, true, 500), "закрыто Esc на этом секторе");
    }

    /** Отзыв автора 23.11: цифра «1» висела на вкладках колец. Плашка вкладок кончается выше клавиши верхнего сектора. */
    @Test
    void ringTabsClearTopSectorKey() {
        float cy = 194;
        float tabsBottom = WheelLayout.tabsY(cy, R) - 2 + WheelLayout.TABS_H;
        float keyTop = cy - WheelLayout.keyRadius(R) - WheelLayout.KEY_H / 2f;
        assertTrue(tabsBottom + WheelLayout.TABS_CLEAR <= keyTop + 0.001f, tabsBottom + " / " + keyTop);
        assertTrue(cy - WheelLayout.keyRadius(R) + WheelLayout.KEY_H / 2f < cy - R, "плашка клавиши за внешним краем колеса");
        assertTrue(WheelLayout.tabsY(cy, R) - 2 >= 0, "вкладки на экране 640×400");
    }

    /** Строка подсказок не налезает на клавишу нижнего сектора. */
    @Test
    void hintClearsBottomSectorKey() {
        float keyBottom = WheelLayout.keyRadius(R) + WheelLayout.KEY_H / 2f;
        float hintTop = WheelLayout.hintOffset(R) - 9 / 2f;
        assertTrue(hintTop - keyBottom >= WheelLayout.TABS_CLEAR, hintTop + " / " + keyBottom);
        assertTrue(194 + WheelLayout.hintOffset(R) + 6 < 400, "подсказка на экране 640×400");
    }
}
