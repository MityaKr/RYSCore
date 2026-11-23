package works.repka.ryscore.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import works.repka.ryscore.DocExample;

/** The ui.json example in docs/for-mods.md is read by UiConfig, and a partial file keeps the defaults. */
class UiDocExampleTest {
    private static final Gson GSON = new Gson();

    @Test
    void uiExampleParses() throws Exception {
        UiConfig c = GSON.fromJson(DocExample.json("ui"), UiConfig.class);
        assertEquals(UiScale.AUTO, c.scale);
        assertEquals(Anchors.Corner.TOP_RIGHT, c.mapCorner);
        assertEquals(25, c.mapPercent);
        assertEquals(Anchors.Corner.TOP_LEFT, c.corners.get(Anchors.Element.PINNED_QUESTS));
        assertEquals(Anchors.Corner.BOTTOM_RIGHT, c.corners.get(Anchors.Element.INVITES));
        assertTrue(c.noticeSound);
        assertEquals(100, c.noticeVolume);
        assertEquals(Notices.Place.RIGHT, c.noticePlace);
        assertNotNull(c.seen);
    }

    @Test
    void partialFileKeepsDefaults() {
        UiConfig c = GSON.fromJson("{\"noticePlace\": \"LEFT\", \"seen\": [\"keys\"]}", UiConfig.class);
        assertEquals(Notices.Place.LEFT, c.noticePlace);
        assertEquals(UiScale.AUTO, c.scale);
        assertEquals(Anchors.Corner.TOP_RIGHT, c.mapCorner);
        assertEquals(100, c.noticeVolume);
        assertTrue(c.seen.contains(Onboarding.KEYS));
    }
}
