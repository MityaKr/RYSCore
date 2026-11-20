package works.repka.ryscore.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NoticePlaceTest {
    @Test
    void progressFromVanillaX() {
        // колонка игры: x = ширина экрана - ширина тоста × ход
        assertEquals(0f, NoticePlace.progress(400, 160, 400));
        assertEquals(0.5f, NoticePlace.progress(400, 160, 320));
        assertEquals(1f, NoticePlace.progress(400, 160, 240));
        assertEquals(1f, NoticePlace.progress(400, 160, 100), "за пределами хода");
        assertEquals(1f, NoticePlace.progress(400, 0, 400), "пустой тост стоит на месте");
    }

    @Test
    void horizontalPlaces() {
        assertEquals(240f, NoticePlace.x(Notices.Place.RIGHT, 400, 160));
        assertEquals(120f, NoticePlace.x(Notices.Place.CENTER, 400, 160));
        assertEquals(121f, NoticePlace.x(Notices.Place.CENTER, 401, 160), "по центру целым числом");
        assertEquals(0f, NoticePlace.x(Notices.Place.LEFT, 400, 160));
    }

    @Test
    void slidesDownFromAboveAndLeavesUp() {
        // в начале выезда карточка целиком над своим местом, на ходу 1 на месте; уход идёт тем же путём вверх
        assertEquals(100f - 30 - NoticePlace.GAP, NoticePlace.slide(100, 30, 0));
        assertEquals(100f - 16, NoticePlace.slide(100, 30, 0.5f));
        assertEquals(100f, NoticePlace.slide(100, 30, 1));
    }

    @Test
    void stackUnderZone() {
        assertEquals(0f, NoticePlace.base(0), "нет зоны: от края экрана");
        assertEquals(102f, NoticePlace.base(100), "под картой или боссбаром с зазором");
        assertEquals(102f, NoticePlace.stack(102));
        assertEquals(102f + 30 + 2 + 45 + 2, NoticePlace.stack(102, 30, 45));
    }

    @Test
    void approachIsSmoothAndSettles() {
        assertEquals(50f, NoticePlace.approach(Float.NaN, 50, 16), "первый кадр сразу на месте");
        float y = NoticePlace.approach(100, 50, 16);
        assertEquals(true, y < 100 && y > 50, "двигается, но не прыгает");
        assertEquals(50f, NoticePlace.approach(100, 50, 1000), "за долгий кадр приезжает");
        assertEquals(50f, NoticePlace.approach(50.1f, 50, 1), "дрожь у цели снимается");
    }
}
