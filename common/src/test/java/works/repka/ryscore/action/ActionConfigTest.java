package works.repka.ryscore.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Файл клавиш игрока: чтение, запись, битый файл, короткая запись кольца. */
class ActionConfigTest {
    @Test
    void roundTrip(@TempDir Path dir) {
        Path file = dir.resolve("config/ryscore/keys.json");
        ActionConfig c = ActionConfig.load(file);
        assertEquals(ActionConfig.HOLD, c.wheelMode);
        c.bindings.put("rysmap:home", "key.keyboard.h+alt");
        c.rings.put("ryscore:mine", List.of("rysmap:home", ""));
        c.wheelMode = ActionConfig.CLICK;
        c.save(file);
        ActionConfig back = ActionConfig.load(file);
        assertEquals("key.keyboard.h+alt", back.bindings.get("rysmap:home"));
        assertEquals(ActionConfig.CLICK, back.wheelMode);
        List<String> ring = back.ring("ryscore:mine");
        assertEquals(8, ring.size());
        assertEquals("rysmap:home", ring.get(0));
        assertEquals("", ring.get(7));
        assertNull(back.ring("ryscore:operator"));
    }

    @Test
    void brokenFileGivesDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("keys.json");
        Files.writeString(file, "{ not json");
        ActionConfig c = ActionConfig.load(file);
        assertEquals(ActionConfig.HOLD, c.wheelMode);
        assertEquals(0, c.bindings.size());
    }

    @Test
    void nullFieldsAreFilled(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("keys.json");
        Files.writeString(file, "{\"version\":1,\"bindings\":null,\"rings\":null,\"wheelMode\":null}");
        ActionConfig c = ActionConfig.load(file);
        assertEquals(ActionConfig.HOLD, c.wheelMode);
        assertEquals(0, c.rings.size());
    }
}
