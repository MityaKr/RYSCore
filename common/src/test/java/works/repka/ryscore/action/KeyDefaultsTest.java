package works.repka.ryscore.action;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyDefaultsTest {
    @Test
    void retiresOnlyUntouchedOldDefault() {
        assertTrue(KeyDefaults.stale("key.keyboard.g", 0, "key.keyboard.g"), "сохранено прежнее умолчание: снимаем");
        assertFalse(KeyDefaults.stale("key.keyboard.h", 0, "key.keyboard.g"), "игрок назначил другую клавишу");
        assertFalse(KeyDefaults.stale("key.keyboard.g", 2, "key.keyboard.g"), "игрок назначил Ctrl+G");
        assertFalse(KeyDefaults.stale("key.keyboard.unknown", 0, "key.keyboard.g"), "новая установка: уже без клавиши");
    }
}
