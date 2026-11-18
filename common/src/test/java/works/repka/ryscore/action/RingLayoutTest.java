package works.repka.ryscore.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Раскладка колец: умолчания, подсказки, запасные и сохранённое игроком. */
class RingLayoutTest {
    private static ResourceLocation id(String s) {
        return ResourceLocation.parse(s);
    }

    private static List<ResourceLocation> ids(String... s) {
        return Arrays.stream(s).map(RingLayoutTest::id).toList();
    }

    @Test
    void defaultsSkipMissingAndPack() {
        Set<ResourceLocation> have = Set.of(id("rysmap:home"), id("rysmap:spawn"), id("ryscore:dock/book"));
        var out = Ring.layout(null, ids("rysmap:home", "rysmap:back", "rysmap:spawn"), List.of(), ids("ryscore:dock/book"), have::contains);
        assertEquals(8, out.size());
        assertEquals(id("rysmap:home"), out.get(0));
        assertEquals(id("rysmap:spawn"), out.get(1));
        assertEquals(id("ryscore:dock/book"), out.get(2));
        assertNull(out.get(3));
    }

    @Test
    void suggestionsAfterDefaultsWithoutDuplicates() {
        Set<ResourceLocation> have = Set.of(id("a:one"), id("a:two"), id("b:three"));
        var out = Ring.layout(null, ids("a:one"), ids("a:one", "b:three", "a:two"), List.of(), have::contains);
        assertEquals(List.of(id("a:one"), id("b:three"), id("a:two")), out.subList(0, 3));
    }

    @Test
    void neverMoreThanEight() {
        List<ResourceLocation> many = ids("a:1", "a:2", "a:3", "a:4", "a:5", "a:6", "a:7", "a:8", "a:9", "a:10");
        var out = Ring.layout(null, many, List.of(), List.of(), x -> true);
        assertEquals(8, out.size());
        assertEquals(id("a:8"), out.get(7));
    }

    @Test
    void savedKeepsPositionsAndEmptiesMissing() {
        Set<ResourceLocation> have = Set.of(id("a:one"), id("a:three"));
        var out = Ring.layout(List.of("", "a:one", "gone:mod", "a:three"), ids("a:one"), List.of(), List.of(), have::contains);
        assertNull(out.get(0));
        assertEquals(id("a:one"), out.get(1));
        assertNull(out.get(2));
        assertEquals(id("a:three"), out.get(3));
        assertNull(out.get(7));
    }
}
