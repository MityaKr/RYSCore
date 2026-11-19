package works.repka.ryscore.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntroGateTest {
    private static final int DELAY = 100;

    /** Отметка «видел» как файл на диске: переживает новый {@link IntroGate} (следующий запуск игры). */
    private static final class Disk implements IntroGate.Store {
        boolean seen;
        int saves;

        @Override
        public boolean seen() {
            return seen;
        }

        @Override
        public void markSeen() {
            seen = true;
            saves++;
        }
    }

    /** Сколько раз карточка показалась за {@code n} тиков. */
    private static int run(IntroGate gate, int n, boolean inWorld, boolean screen) {
        int shown = 0;
        for (int i = 0; i < n; i++) if (gate.tick(inWorld, screen)) shown++;
        return shown;
    }

    @Test
    void showsOnceAfterDelayAndMarksSeenAtOnce() {
        Disk disk = new Disk();
        IntroGate gate = new IntroGate(DELAY, 1, disk);
        assertEquals(0, run(gate, DELAY - 1, true, false));
        assertFalse(disk.seen);
        assertTrue(gate.tick(true, false));
        assertTrue(disk.seen, "отметка ставится в момент показа, без нажатия кнопки");
        assertEquals(1, disk.saves);
        assertEquals(0, run(gate, 10 * DELAY, true, false), "проигнорированная карточка сама не возвращается");
    }

    @Test
    void ignoredCardNeverComesBackNextLaunch() {
        Disk disk = new Disk();
        assertEquals(1, run(new IntroGate(DELAY, 1, disk), 3 * DELAY, true, false));
        IntroGate next = new IntroGate(DELAY, 1, disk);
        assertEquals(0, run(next, 10 * DELAY, true, false));
        assertEquals(1, disk.saves);
    }

    @Test
    void delayCountsOnlyWithoutScreenAndInWorld() {
        Disk disk = new Disk();
        IntroGate gate = new IntroGate(DELAY, 1, disk);
        assertEquals(0, run(gate, 5 * DELAY, false, false), "вне мира не считается");
        assertEquals(0, run(gate, 5 * DELAY, true, true), "с открытым окном не считается");
        assertEquals(0, run(gate, DELAY - 1, true, false));
        assertTrue(gate.tick(true, false));
    }

    @Test
    void familyWindowHidesCardButKeepsSeen() {
        Disk disk = new Disk();
        IntroGate gate = new IntroGate(DELAY, 1, disk);
        run(gate, DELAY, true, false);
        assertFalse(gate.covered(false), "без окна семьи карточка видна");
        assertTrue(gate.covered(true));
        assertTrue(disk.seen, "окно семьи отметку не снимает");
        assertEquals(0, run(new IntroGate(DELAY, 1, disk), 10 * DELAY, true, false), "следующий запуск: карточки нет");
    }

    @Test
    void returnsOnceAfterFamilyWindowClosesInSameWorld() {
        Disk disk = new Disk();
        IntroGate gate = new IntroGate(DELAY, 1, disk);
        run(gate, DELAY, true, false);
        gate.covered(true);
        assertEquals(0, run(gate, 5 * DELAY, true, true), "пока окно открыто, не возвращается");
        assertEquals(0, run(gate, DELAY - 1, true, false));
        assertTrue(gate.tick(true, false), "через задержку после закрытия окна возвращается");
        assertTrue(gate.covered(true), "второй раз окно прячет карточку насовсем");
        assertEquals(0, run(gate, 10 * DELAY, true, false));
        assertEquals(1, disk.saves);
    }

    @Test
    void returnIsDroppedOnLeavingWorld() {
        Disk disk = new Disk();
        IntroGate gate = new IntroGate(DELAY, 1, disk);
        run(gate, DELAY, true, false);
        gate.covered(true);
        run(gate, 1, false, false);
        assertEquals(0, run(gate, 10 * DELAY, true, false), "в следующем мире повтора нет");
    }
}
