package works.repka.ryscore.perm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Узлы прав: имена, повторное объявление, старшинство ролей. */
class PermsTest {
    @Test
    void nodeName() {
        Perms.Node n = Perms.node("rysmap", "place.manage", Roles.MODERATOR);
        assertEquals("rysmap.place.manage", n.id());
        assertEquals(Roles.MODERATOR, n.defaultRole());
        assertSame(n, Perms.node("rysmap", "place.manage", Roles.PLAYER));
        assertSame(n, Perms.get("rysmap.place.manage"));
        assertTrue(Perms.nodes().contains(n));
    }

    @Test
    void badNames() {
        assertThrows(IllegalArgumentException.class, () -> Perms.node("RYSMap", "x", Roles.PLAYER));
        assertThrows(IllegalArgumentException.class, () -> Perms.node("rysmap", "place manage", Roles.PLAYER));
        assertThrows(IllegalArgumentException.class, () -> Perms.node("rysmap", "place..manage", Roles.PLAYER));
    }

    @Test
    void ranks() {
        assertEquals(2, Roles.rank(Roles.OPERATOR));
        assertEquals(1, Roles.rank(Roles.MODERATOR));
        assertEquals(0, Roles.rank(Roles.PLAYER));
        assertEquals(0, Roles.rank("helper"));
        assertEquals(0, Roles.rank(null));
    }
}
