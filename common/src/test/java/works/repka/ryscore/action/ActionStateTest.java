package works.repka.ryscore.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionStateTest {
    @Test
    void clock() {
        assertEquals("0:24", Action.State.pause(24).clock());
        assertEquals("10:00", Action.State.pause(600).clock());
        assertEquals("1:05:00", Action.State.pause(3900).clock());
    }

    @Test
    void zeroPauseIsReady() {
        assertSame(Action.State.ready(), Action.State.pause(0));
        assertTrue(Action.State.active().usable());
        assertFalse(Action.State.off("Дом не поставлен").usable());
        assertEquals("Дом не поставлен", Action.State.off("Дом не поставлен").reason());
    }
}
