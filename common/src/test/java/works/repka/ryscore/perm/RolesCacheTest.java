package works.repka.ryscore.perm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Кэш ролей: один вопрос поставщику на игрока, сброс по игроку и целиком, смена поставщика сбрасывает. */
class RolesCacheTest {
    @AfterEach
    void reset() {
        Roles.provide(null);
    }

    @Test
    void cachedUntilInvalidated() {
        AtomicInteger calls = new AtomicInteger();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Roles.provide((server, id) -> {
            calls.incrementAndGet();
            return id.equals(a) ? Roles.MODERATOR : Roles.PLAYER;
        });
        assertEquals(Roles.MODERATOR, Roles.of(null, a));
        assertEquals(Roles.MODERATOR, Roles.of(null, a));
        assertEquals(1, calls.get());
        assertEquals(Roles.PLAYER, Roles.of(null, b));
        assertEquals(2, calls.get());
        Roles.invalidate(a);
        Roles.of(null, a);
        Roles.of(null, b);
        assertEquals(3, calls.get());
        Roles.invalidateAll();
        Roles.of(null, b);
        assertEquals(4, calls.get());
    }

    @Test
    void newProviderForgetsOld() {
        UUID a = UUID.randomUUID();
        Roles.provide((server, id) -> Roles.MODERATOR);
        assertEquals(Roles.MODERATOR, Roles.of(null, a));
        Roles.provide((server, id) -> Roles.OPERATOR);
        assertEquals(Roles.OPERATOR, Roles.of(null, a));
    }
}
