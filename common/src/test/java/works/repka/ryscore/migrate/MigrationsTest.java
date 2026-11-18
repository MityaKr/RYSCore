package works.repka.ryscore.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationsTest {
    @TempDir
    Path game;
    Path world;
    final List<String> ran = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        Migrations.reset();
        world = game.resolve("world");
        Files.createDirectories(world.resolve("ftbteams/party"));
        Files.writeString(world.resolve("ftbteams/party/team.snbt"), "{ id: \"x\" }");
        Files.createDirectories(world.resolve("data"));
        Files.writeString(world.resolve("data/rysteams.dat"), "teams");
        Files.writeString(world.resolve("data/other.dat"), "чужое");
    }

    private void flag(String json) throws IOException {
        Path f = game.resolve(Migrations.FLAG_FILE);
        Files.createDirectories(f.getParent());
        Files.writeString(f, json, StandardCharsets.UTF_8);
    }

    /** Шаг, который записывает своё имя в {@link #ran}; {@code fail} бросает исключение, пока истинно. */
    private Migrations.Step step(String id, int order, Predicate<Migrations.Context> needed, boolean[] fail) {
        return new Migrations.Step() {
            @Override public String id() { return id; }
            @Override public int order() { return order; }
            @Override public Migrations.Side side() { return Migrations.Side.SERVER; }
            @Override public boolean needed(Migrations.Context c) { return needed.test(c); }
            @Override public Migrations.Report run(Migrations.Context c) {
                ran.add(id);
                if (fail != null && fail[0]) throw new IllegalStateException("сломано: " + id);
                return Migrations.Report.create().count("n", 1).skipped("VENIK", "уже есть");
            }
        };
    }

    private Migrations.Step step(String id, int order) {
        return step(id, order, c -> true, null);
    }

    private Migrations.Result run() {
        return Migrations.runServer(game, world, null);
    }

    private long backups() throws IOException {
        Path dir = world.resolve("rys/backup");
        if (!Files.isDirectory(dir)) return 0;
        try (var s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    void withoutFlagNothingRuns() throws IOException {
        Migrations.register(step("teams", 10));
        Migrations.Result r = run();
        assertTrue(ran.isEmpty());
        assertEquals(Migrations.Status.NOT_IN_FLAG, r.outcome("teams").status());
        assertFalse(Files.exists(world.resolve("rys/migrate.json")));
        assertEquals(0, backups());
    }

    @Test
    void stepsRunInOrder() throws IOException {
        flag("{\"v\":1,\"from\":\"ftb\",\"steps\":[\"teams\",\"quests\",\"claims\"]}");
        Migrations.register(step("claims", 30));
        Migrations.register(step("teams", 10));
        Migrations.register(step("quests", 20));
        Migrations.Result r = run();
        assertEquals(List.of("teams", "quests", "claims"), ran);
        assertTrue(r.ran());
        assertEquals(Migrations.Status.DONE, r.outcome("claims").status());
        String report = Files.readString(world.resolve("rys/migrate-report.txt"), StandardCharsets.UTF_8);
        assertTrue(report.contains("VENIK: уже есть"), report);
    }

    @Test
    void stepMissingFromFlagIsSkipped() throws IOException {
        flag("{\"v\":1,\"from\":\"ftb\",\"steps\":[\"teams\"]}");
        Migrations.register(step("teams", 10));
        Migrations.register(step("claims", 30));
        Migrations.Result r = run();
        assertEquals(List.of("teams"), ran);
        assertEquals(Migrations.Status.NOT_IN_FLAG, r.outcome("claims").status());
    }

    @Test
    void failureLeavesNoMarkAndNextStepsGoOn() throws IOException {
        flag("{\"v\":1,\"steps\":[\"teams\",\"quests\",\"claims\"]}");
        boolean[] broken = {true};
        Migrations.register(step("teams", 10));
        Migrations.register(step("quests", 20, c -> true, broken));
        Migrations.register(step("claims", 30, c -> !c.done("quests") || true, null));
        Migrations.Result r = run();
        assertEquals(List.of("teams", "quests", "claims"), ran);
        assertEquals(Migrations.Status.FAILED, r.outcome("quests").status());
        assertTrue(r.outcome("quests").message().contains("сломано"));
        assertEquals(Migrations.Status.DONE, r.outcome("claims").status());

        // второй запуск: повторяется только сбойный шаг
        ran.clear();
        broken[0] = false;
        r = run();
        assertEquals(List.of("quests"), ran);
        assertEquals(Migrations.Status.ALREADY_DONE, r.outcome("teams").status());
        assertEquals(Migrations.Status.DONE, r.outcome("quests").status());
    }

    @Test
    void repeatDoesNotRedo() throws IOException {
        flag("{\"v\":1,\"steps\":[\"teams\",\"claims\"]}");
        Migrations.register(step("teams", 10));
        Migrations.register(step("claims", 30));
        run();
        String state = Files.readString(world.resolve("rys/migrate.json"));
        ran.clear();
        Migrations.Result second = run();
        Migrations.Result third = run();
        assertTrue(ran.isEmpty());
        assertFalse(second.ran());
        assertFalse(third.ran());
        assertEquals(state, Files.readString(world.resolve("rys/migrate.json")));
    }

    @Test
    void notNeededLeavesNoMark() throws IOException {
        flag("{\"v\":1,\"steps\":[\"claims\"]}");
        boolean[] ftbLoaded = {true};
        Migrations.register(step("claims", 30, c -> !ftbLoaded[0], null));
        assertEquals(Migrations.Status.NOT_NEEDED, run().outcome("claims").status());
        assertEquals(0, backups());
        ftbLoaded[0] = false;
        assertEquals(Migrations.Status.DONE, run().outcome("claims").status());
    }

    @Test
    void backupIsMadeOnce() throws IOException {
        flag("{\"v\":1,\"steps\":[\"teams\",\"quests\"]}");
        boolean[] broken = {true};
        Migrations.register(step("teams", 10));
        Migrations.register(step("quests", 20, c -> true, broken));
        Migrations.Result r = run();
        assertEquals(1, backups());
        Path copy = r.backup();
        assertNotNull(copy);
        assertTrue(Files.isRegularFile(copy.resolve("ftbteams/party/team.snbt")));
        assertTrue(Files.isRegularFile(copy.resolve("data/rysteams.dat")));
        assertFalse(Files.exists(copy.resolve("data/other.dat")));
        broken[0] = false;
        run();
        assertEquals(1, backups());
    }

    @Test
    void manualRunIgnoresFlagButNotMark() {
        Migrations.register(step("claims", 30));
        Migrations.Outcome o = Migrations.runManual("claims", game, world, null);
        assertEquals(Migrations.Status.DONE, o.status());
        assertEquals(Migrations.Status.ALREADY_DONE, Migrations.runManual("claims", game, world, null).status());
        assertEquals(List.of("claims"), ran);
        assertEquals(Migrations.Status.FAILED, Migrations.runManual("nope", game, world, null).status());
    }

    @Test
    void undoClearsMarkOnlyWhenStepCan() {
        Migrations.register(step("teams", 10));
        Migrations.runManual("teams", game, world, null);
        Migrations.Outcome refused = Migrations.undo("teams", game, world, null);
        assertEquals(Migrations.Status.FAILED, refused.status());
        assertEquals(Migrations.Status.ALREADY_DONE, Migrations.runManual("teams", game, world, null).status());

        Migrations.register(new Migrations.Step() {
            @Override public String id() { return "teams"; }
            @Override public int order() { return 10; }
            @Override public Migrations.Side side() { return Migrations.Side.SERVER; }
            @Override public boolean needed(Migrations.Context c) { return true; }
            @Override public Migrations.Report run(Migrations.Context c) { return Migrations.Report.create(); }
            @Override public Migrations.Report undo(Migrations.Context c) {
                assertNotNull(c.backupDir());
                return Migrations.Report.create().line("возвращено");
            }
        });
        assertEquals(Migrations.Status.UNDONE, Migrations.undo("teams", game, world, null).status());
        assertEquals(Migrations.Status.DONE, Migrations.runManual("teams", game, world, null).status());
    }

    @Test
    void partialStepIsMarkedPartial() throws IOException {
        flag("{\"v\":1,\"steps\":[\"claims\"]}");
        Migrations.register(new Migrations.Step() {
            @Override public String id() { return "claims"; }
            @Override public int order() { return 30; }
            @Override public Migrations.Side side() { return Migrations.Side.SERVER; }
            @Override public boolean needed(Migrations.Context c) { return true; }
            @Override public Migrations.Report run(Migrations.Context c) {
                return Migrations.Report.create().count("chunks", 133).partial("файл b.snbt не прочитан");
            }
        });
        assertEquals(Migrations.Status.PARTIAL, run().outcome("claims").status());
        assertEquals(Migrations.Status.ALREADY_DONE, run().outcome("claims").status());
        assertTrue(Migrations.describe(game, world).stream().anyMatch(l -> l.contains("частично")));
    }

    @Test
    void flagOptionsAndBrokenFlag() throws IOException {
        flag("{\"v\":1,\"from\":\"ftb\",\"steps\":[\"claims\"],\"ftbChunksAsMap\":true}");
        Migrations.Flag f = Migrations.flag(game);
        assertTrue(f.present());
        assertEquals("ftb", f.from());
        assertTrue(f.bool("ftbChunksAsMap", false));
        flag("{ не json");
        assertFalse(Migrations.flag(game).present());
    }

    @Test
    void clientStepKeepsProgressAndSeesValues() throws IOException {
        flag("{\"v\":1,\"steps\":[\"map\"]}");
        Path state = game.resolve("rysmap/world-key");
        boolean[] broken = {true};
        Migrations.register(new Migrations.Step() {
            @Override public String id() { return "map"; }
            @Override public int order() { return 40; }
            @Override public Migrations.Side side() { return Migrations.Side.CLIENT; }
            @Override public boolean needed(Migrations.Context c) { return !c.value("ftbId").isEmpty(); }
            @Override public Migrations.Report run(Migrations.Context c) throws IOException {
                int from = c.data().has("regions") ? c.data().get("regions").getAsInt() : 0;
                ran.add("map from " + from);
                c.data().addProperty("regions", from + 5);
                c.save();
                if (broken[0]) throw new IOException("вышел из игры");
                return Migrations.Report.create().count("regions", from + 5);
            }
        });
        Migrations.register(step("teams", 10));
        assertEquals(Migrations.Status.NOT_NEEDED, Migrations.runClientNow(game, state, Map.of()).outcome("map").status());
        Migrations.runClientNow(game, state, Map.of("ftbId", "5936"));
        broken[0] = false;
        Migrations.Result r = Migrations.runClientNow(game, state, Map.of("ftbId", "5936"));
        assertEquals(List.of("map from 0", "map from 5"), ran);
        assertEquals(Migrations.Status.DONE, r.outcome("map").status());
        assertNull(r.outcome("teams"));
        assertTrue(Files.isRegularFile(state.resolve("migrate.json")));
        assertFalse(Files.exists(state.resolve("backup")));
    }
}
