package works.repka.ryscore.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PendingTest {
    @Test
    void optimisticUntilAnswer() {
        Pending p = new Pending();
        int id = p.start("toggle", true, 1000);
        assertTrue(p.busy("toggle"));
        assertTrue(p.value("toggle", false));
        assertEquals("toggle", p.done(id));
        assertFalse(p.busy("toggle"));
        assertFalse(p.value("toggle", false));
    }

    @Test
    void spinnerOnlyAfterDelay() {
        Pending p = new Pending();
        p.start("save", null, 1000);
        assertFalse(p.spinning("save", 1000 + Pending.SPIN_DELAY - 1));
        assertTrue(p.spinning("save", 1000 + Pending.SPIN_DELAY));
        // без оптимистичного значения показывается значение сервера
        assertEquals("old", p.value("save", "old"));
    }

    @Test
    void lastClickWins() {
        Pending p = new Pending();
        int first = p.start("days", 35, 1000);
        int second = p.start("days", 40, 1010);
        // ответ на первое нажатие не снимает второе
        assertNull(p.done(first));
        assertEquals(40, p.value("days", 30));
        assertEquals("days", p.done(second));
        assertEquals(30, p.value("days", 30));
    }

    @Test
    void wrongTypeFallsBackToServer() {
        Pending p = new Pending();
        p.start("x", "text", 0);
        assertEquals(5, p.value("x", 5));
    }

    @Test
    void timeoutRollsBack() {
        Pending p = new Pending();
        p.start("a", 1, 0);
        p.start("b", 2, 5000);
        assertEquals(List.of(), p.expire(Pending.TIMEOUT - 1));
        assertEquals(List.of("a"), p.expire(Pending.TIMEOUT));
        assertFalse(p.busy("a"));
        assertTrue(p.busy("b"));
        p.clear();
        assertFalse(p.any());
    }
}
