package works.repka.ryscore.perm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Кольца колеса по роли с сервера (вопрос автора 23.11: «если у игрока нет ОП, кнопки не показываются?»). Кольцо
 * «Оператор» живёт на {@code atLeastClient(OPERATOR)}, «Модерация» на {@code moderationRing()}.
 */
class RolesClientTest {
    @AfterEach
    void reset() {
        Roles.setClient(null);
        Roles.setModerationActive(false);
    }

    @Test
    void playerSeesNoStaffRings() {
        Roles.setClient(Roles.PLAYER);
        Roles.setModerationActive(true);
        assertFalse(Roles.atLeastClient(Roles.OPERATOR));
        assertFalse(Roles.moderationRing());
    }

    @Test
    void moderatorSeesModerationOnly() {
        Roles.setClient(Roles.MODERATOR);
        assertFalse(Roles.atLeastClient(Roles.OPERATOR));
        assertTrue(Roles.moderationRing());
    }

    @Test
    void operatorSeesOperatorRing() {
        Roles.setClient(Roles.OPERATOR);
        assertTrue(Roles.atLeastClient(Roles.OPERATOR));
        assertFalse(Roles.moderationRing(), "без модераторов и жалоб кольца «Модерация» нет");
        Roles.setModerationActive(true);
        assertTrue(Roles.moderationRing());
    }
}
