package works.repka.ryscore.action;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.ryscore.DocExample;

/** The keys.json example in docs/for-mods.md is read by ActionConfig. */
class KeysDocExampleTest {
    @Test
    void keysExampleParses(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("keys.json");
        Files.writeString(file, DocExample.json("keys"), StandardCharsets.UTF_8);
        ActionConfig c = ActionConfig.load(file);
        assertEquals(1, c.version);
        assertEquals("key.keyboard.h+alt", c.bindings.get("rysmap:home"));
        assertEquals(ActionConfig.HOLD, c.wheelMode);
        List<String> ring = c.ring("ryscore:mine");
        assertEquals(Ring.SECTORS, ring.size());
        assertEquals("rysmap:home", ring.get(0));
        assertEquals("", ring.get(1));
        assertEquals(Ring.MINE.toString(), "ryscore:mine");
    }
}
