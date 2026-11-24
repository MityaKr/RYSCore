package works.repka.ryscore.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.ryscore.DocExample;
import works.repka.ryscore.migrate.Migrations;

/** The theme and migrate flag examples in docs/for-mods.md are read by the core as the document says. */
class ThemeDocExampleTest {
    private static final int MISSING = 0xFFFF00FF;
    /** Tokens the core itself paints with (grep of c("...") in common/src/main, 1.17.0). */
    private static final List<String> CORE_TOKENS = List.of("accent", "accent.text", "button.bg", "button.border", "button.fg",
        "button.hover", "callout.bg", "callout.border", "card.bg", "card.border", "cell.bg", "cell.border", "coin", "column.bg",
        "filter.active.bg", "hud.bg", "hud.border", "hud.fg", "hud.gold", "hud.muted", "hud.ok", "input.bg", "input.border",
        "input.fg", "link", "overlay", "panel.bevel.dark", "panel.bevel.light", "panel.bg", "panel.border", "plate.bg",
        "plate.border", "row.selected.bg", "search.bg", "sidebar.bg", "slot.bevel.dark", "slot.bevel.light", "slot.bg", "slot.border",
        "slot.hover", "state.bad", "state.ok", "state.warn", "tab.active.bg", "tab.active.fg", "tab.inactive.bg",
        "tab.inactive.fg", "text.body", "text.dim", "text.muted", "text.primary", "text.secondary", "tooltip.bg",
        "tooltip.border", "tooltip.fg", "wheel.center", "wheel.dim", "wheel.fg", "wheel.hover", "wheel.hover.line", "wheel.line",
        "wheel.muted", "wheel.sector", "wheel.sector.off", "wheel.tile");

    @Test
    void themeExampleParses() throws Exception {
        JsonObject json = JsonParser.parseString(DocExample.json("theme")).getAsJsonObject();
        Theme t = new Theme("mypack:ember", json);
        assertEquals("Ember", t.name);
        assertTrue(t.usesType(), "font ryscore:inter");
        assertTrue(t.flushSidebar);
        assertFalse(t.pillTabs);
        assertFalse(t.beveled());
        assertTrue(t.shadow);
        assertFalse(t.reducedMotion);
        assertEquals(0xFFFF8A3D, t.c("accent"));
        // overlay.alpha 0.55 -> alpha 140
        assertEquals(0x8C0E0C0A, t.c("overlay"));
        // radii are given in design pixels: 6 px of the mockup is 3 GUI units
        assertEquals(3f, t.r("slot.radius"));

        Set<String> tokens = new LinkedHashSet<>(CORE_TOKENS);
        try (InputStream in = ThemeDocExampleTest.class.getResourceAsStream("/assets/ryscore/ryscore_themes/slate.json")) {
            JsonObject slate = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : slate.getAsJsonObject("colors").keySet()) if (!key.endsWith(".alpha")) tokens.add(key);
        }
        for (int i = 1; i <= 12; i++) tokens.add("cat." + i);
        for (String token : tokens) assertNotEquals(MISSING, t.c(token), "token without color: " + token);
    }

    @Test
    void emptyThemeShowsMissingRoots() {
        Theme t = new Theme("mypack:empty", new JsonObject());
        assertEquals(MISSING, t.c("panel.bg"));
        assertEquals(MISSING, t.c("card.bg"));
        assertFalse(t.usesType());
    }

    @Test
    void migrateFlagExampleParses(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(Migrations.FLAG_FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, DocExample.json("migrate"), StandardCharsets.UTF_8);
        Migrations.Flag flag = Migrations.flag(dir);
        assertTrue(flag.present());
        assertEquals(1, flag.version());
        assertEquals("ftb", flag.from());
        assertEquals(List.of("teams", "quests", "claims", "map"), flag.steps());
    }
}
