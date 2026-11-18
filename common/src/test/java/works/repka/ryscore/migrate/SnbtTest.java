package works.repka.ryscore.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Заготовки повторяют устройство файлов FTB Teams, FTB Chunks и настроек FTB Library; UUID выдуманы. */
class SnbtTest {
    static final String TEAM = """
        {
        \tid: "11111111-2222-4333-8444-555555555555"
        \ttype: "party"
        \towner: "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        \tranks: {
        \t\taaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee: "owner"
        \t\t12e45678-0000-4000-8000-000000000001: "officer"
        \t}
        \tproperties: {
        \t\t"ftbteams:display_name": "VENIK"
        \t\t"ftbteams:color": "#59FF80"
        \t\t"ftbteams:team_stages": [ ]
        \t\t"ftbchunks:allow_fake_players": 0b
        \t\t"ftbchunks:allow_fake_players_by_id": 1b
        \t\t"ftbchunks:block_edit_mode": "allies"
        \t}
        \tmessage_history: [
        \t\t{ from: "00000000-0000-0000-0000-000000000000", date: 1793285616962L, text: "{\\"translate\\":\\"x\\",\\"with\\":[\\"Mitya\\"]}" }
        \t]
        }
        """;

    static final String CHUNKS = """
        {
        \tmax_claim_chunks: 500
        \tmax_force_load_chunks: 125
        \tlast_login_time: 1794941418389L
        \tchunks: {
        \t\t"minecraft:the_nether": [{ x: 41, z: -187, time: 1793981256753L, force_loaded: 1793981260553L }]
        \t\t"minecraft:overworld": [
        \t\t\t{ x: 338, z: 1469, time: 1794613063967L, force_loaded: 1794770974418L }
        \t\t\t{ x: 337, z: 1464, time: 1794613063967L }
        \t\t]
        \t}
        \tmember_data: { aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee: { max_claimed_chunks: 500, max_force_loaded_chunks: 125, original_claims: { } } }
        }
        """;

    static final String CONFIG = """
        # Server-specific configuration for FTB Chunks
        #   (may be overwritten on modpack update)

        {
        \t# Forced modes won't let players change their ally settings
        \t# Valid values: "default", "forced_all", "forced_none"
        \tally_mode: "default"
        \t
        \tdisable_protection: false
        \tclaim_dimension_blacklist: [ ]
        \tmax_claimed_chunks: 25
        \tscale: 1.5d
        \tratio: -.25f
        }
        """;

    @Test
    void readsTeamFile() {
        JsonObject team = Snbt.parse(TEAM).getAsJsonObject();
        assertEquals("party", Snbt.string(team, "type", ""));
        JsonObject ranks = Snbt.object(team, "ranks");
        assertEquals("owner", Snbt.string(ranks, "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", ""));
        // UUID с цифрами и «e» в начале не становится числом
        assertEquals("officer", Snbt.string(ranks, "12e45678-0000-4000-8000-000000000001", ""));
        JsonObject props = Snbt.object(team, "properties");
        assertEquals("VENIK", Snbt.string(props, "ftbteams:display_name", ""));
        assertEquals("#59FF80", Snbt.string(props, "ftbteams:color", ""));
        assertFalse(Snbt.bool(props, "ftbchunks:allow_fake_players", true));
        assertTrue(Snbt.bool(props, "ftbchunks:allow_fake_players_by_id", false));
        assertEquals(0, Snbt.array(props, "ftbteams:team_stages").size());
        JsonObject msg = Snbt.array(team, "message_history").get(0).getAsJsonObject();
        assertEquals(1793285616962L, Snbt.longValue(msg, "date", 0));
        // экранированный JSON внутри строки читается как есть
        assertEquals("x", JsonParser.parseString(Snbt.string(msg, "text", "")).getAsJsonObject().get("translate").getAsString());
        assertEquals(UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"), Snbt.uuid(team.get("owner")));
    }

    @Test
    void readsChunksFile() {
        JsonObject root = Snbt.parse(CHUNKS).getAsJsonObject();
        assertEquals(500, Snbt.integer(root, "max_claim_chunks", 0));
        JsonObject chunks = Snbt.object(root, "chunks");
        JsonArray over = Snbt.array(chunks, "minecraft:overworld");
        assertEquals(2, over.size());
        int forced = 0, total = 0;
        for (String dim : chunks.keySet()) {
            for (JsonElement e : chunks.getAsJsonArray(dim)) {
                total++;
                if (Snbt.longValue(e.getAsJsonObject(), "force_loaded", 0) > 0) forced++;
            }
        }
        assertEquals(3, total);
        assertEquals(2, forced);
        assertEquals(-187, Snbt.integer(Snbt.array(chunks, "minecraft:the_nether").get(0).getAsJsonObject(), "z", 0));
        JsonObject member = Snbt.object(Snbt.object(root, "member_data"), "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        assertEquals(125, Snbt.integer(member, "max_force_loaded_chunks", 0));
        assertEquals(0, Snbt.object(member, "original_claims").size());
    }

    @Test
    void skipsComments() {
        JsonObject cfg = Snbt.parse(CONFIG).getAsJsonObject();
        assertEquals("default", Snbt.string(cfg, "ally_mode", ""));
        assertFalse(Snbt.bool(cfg, "disable_protection", true));
        assertEquals(25, Snbt.integer(cfg, "max_claimed_chunks", 0));
        assertEquals(1.5, cfg.get("scale").getAsDouble(), 1e-9);
        assertEquals(-0.25, cfg.get("ratio").getAsDouble(), 1e-9);
    }

    @Test
    void readsTypedArraysAndCommas() {
        JsonObject o = Snbt.parse("{a: [I; 1, -2, 3], b: [L;4L,5L], c: [B; 1b, 0b], 'q': 'it''s', n: 7s}".replace("''", "\\'")).getAsJsonObject();
        assertEquals(3, o.getAsJsonArray("a").size());
        assertEquals(-2, o.getAsJsonArray("a").get(1).getAsInt());
        assertEquals(5, o.getAsJsonArray("b").get(1).getAsLong());
        assertEquals("it's", o.get("q").getAsString());
        assertEquals(7, Snbt.integer(o, "n", 0));
        // UUID массивом четырёх чисел, как пишет игра
        UUID id = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        int[] parts = {(int) (id.getMostSignificantBits() >> 32), (int) id.getMostSignificantBits(), (int) (id.getLeastSignificantBits() >> 32), (int) id.getLeastSignificantBits()};
        JsonElement arr = Snbt.parse("[I; " + parts[0] + ", " + parts[1] + ", " + parts[2] + ", " + parts[3] + "]");
        assertEquals(id, Snbt.uuid(arr));
        assertNull(Snbt.uuid(Snbt.parse("\"не uuid\"")));
    }

    @Test
    void errorsNameLineAndFile(@TempDir Path dir) throws IOException {
        Snbt.SyntaxError e = assertThrows(Snbt.SyntaxError.class, () -> Snbt.parse("{\n\ta: 1\n\tb 2\n}"));
        assertEquals(3, e.line());
        Path f = dir.resolve("broken.snbt");
        Files.writeString(f, "{\n\tchunks: [ { x: 1 }\n");
        Snbt.SyntaxError inFile = assertThrows(Snbt.SyntaxError.class, () -> Snbt.read(f));
        assertEquals(f, inFile.file());
        assertTrue(inFile.getMessage().contains("broken.snbt"));
        assertThrows(Snbt.SyntaxError.class, () -> Snbt.parse("{a: 1} лишнее"));
    }

    @Test
    void helpersTolerateMissingAndWrongTypes() {
        JsonObject o = Snbt.parse("{n: 1b, s: \"x\", flag: 1}").getAsJsonObject();
        assertEquals(1, Snbt.integer(o, "n", 0));
        assertTrue(Snbt.bool(o, "flag", false));
        assertEquals(9, Snbt.integer(o, "missing", 9));
        assertEquals(9, Snbt.integer(o, "s", 9));
        assertEquals(0, Snbt.array(o, "s").size());
        assertEquals(0, Snbt.object(null, "x").size());
    }
}
