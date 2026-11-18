package works.repka.ryscore.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.RYSCore;

/**
 * Переезд мира с FTB (1.11): реестр шагов, флаг сборки, копия, отметка «сделано» и отчёт. Моды семьи не знают друг о
 * друге: каждый регистрирует свой шаг, порядок держит ядро. Описание в плане карты и привата, раздел 10.2.
 *
 * <p><b>Флаг сборки</b> {@code config/ryscore/migrate.json}: {@code {"v": 1, "from": "ftb", "steps": ["teams", "quests",
 * "claims", "map"]}}. Его кладёт генератор сборки. Без файла сами шаги не идут: только командой {@code /rys migrate run}.
 * Остальные поля флага шаг читает через {@link Flag#option}.
 *
 * <p><b>Когда.</b> Серверные шаги идут при запуске сервера после загрузки мира, по порядку, в потоке сервера: точка входа
 * ядра зовёт {@link #runServer(MinecraftServer)} в {@code ServerStartedEvent} с самым низким приоритетом, то есть после
 * того, как моды подняли свои хранилища, и до того, как сервер начал принимать игроков. Клиентский шаг запускает его мод
 * через {@link #runClient}, когда знает ключ мира; идёт в рабочем потоке.
 *
 * <p><b>Правила.</b>
 * <ol>
 *   <li>Шаг идёт сам, только если он назван во флаге, отметки «сделано» нет и {@link Step#needed} ответил да (данные FTB
 *       есть, мод FTB не загружен, своя база это позволяет: решает шаг).</li>
 *   <li>Перед первым серверным шагом ядро один раз копирует {@code world/ftbteams}, {@code ftbchunks}, {@code ftbquests},
 *       {@code data/rys*.dat} и пути из {@link Step#backup()} в {@code world/rys/backup/ftb-<время>/}. Не вышло скопировать —
 *       серверные шаги не идут.</li>
 *   <li>Отметка в {@code world/rys/migrate.json} (клиент: в папке, которую дал мод) ставится после успешного {@link Step#run}.</li>
 *   <li>Своё не затирать: шаг переносит только то, чего в базе мода ещё нет, и пишет пропущенное в {@link Report#skipped}.</li>
 *   <li>Сбой шага: отметки нет, причина в лог (у {@link Snbt.SyntaxError} файл и строка), следующие шаги идут дальше,
 *       при следующем запуске шаг повторится.</li>
 *   <li>Отчёт: блоком {@code [rys-migrate]} в лог и дописывается в {@code world/rys/migrate-report.txt}.</li>
 *   <li>Шаг читает и считает всё в памяти, пишет базу одним сохранением и только потом делает необратимое.</li>
 * </ol>
 */
public final class Migrations {
    /** Метка строк в логе. */
    public static final String TAG = "[rys-migrate]";
    /** Флаг сборки относительно папки игры. */
    public static final String FLAG_FILE = "config/ryscore/migrate.json";
    /** Отметки относительно папки мира. */
    public static final String STATE_DIR = "rys";
    static final String STATE_FILE = "migrate.json";
    static final String REPORT_FILE = "migrate-report.txt";
    /** Что копируется всегда, относительно папки мира. */
    static final List<String> BACKUP = List.of("ftbteams", "ftbchunks", "ftbquests");

    private static final Object LOCK = new Object();
    private static final Map<String, Step> STEPS = new LinkedHashMap<>();
    private static volatile Predicate<String> loaded = id -> false;
    private static volatile Function<String, String> versions = id -> "";
    private static volatile @Nullable Result last;

    private Migrations() {}

    public enum Side { SERVER, CLIENT }

    /** Шаг переезда одного мода. */
    public interface Step {
        /** {@code teams}, {@code quests}, {@code claims}, {@code map}: так шаг назван во флаге и в командах. */
        String id();

        /** Порядок: команды 10, квесты 20, приваты 30, карта 40. */
        int order();

        Side side();

        /** Мод шага: его версия пишется в отметку. */
        default String modId() {
            return "";
        }

        /** Пути мира, которые надо добавить в копию до первого шага (относительно папки мира). */
        default List<String> backup() {
            return List.of();
        }

        /** Нужен ли шаг сейчас: есть данные FTB, мод FTB не загружен ({@link Context#isLoaded}), своя база позволяет. */
        boolean needed(Context c) throws Exception;

        /** Читает, считает, пишет одним сохранением. Исключение значит сбой: отметки не будет. */
        Report run(Context c) throws Exception;

        /**
         * Откат командой {@code /rys migrate undo}: вернуть данные шага из {@link Context#backupDir()} или отказать
         * исключением с причиной. После успеха ядро снимает отметку. Без переопределения откат не поддерживается.
         */
        default Report undo(Context c) throws Exception {
            throw new UnsupportedOperationException("шаг не умеет откат");
        }
    }

    /** Что шаг знает о запуске. */
    public interface Context {
        Side side();

        /** Запущен командой, а не сам при запуске. */
        boolean manual();

        /** Папка игры (у сервера его корень). */
        Path gameDir();

        default Path configDir() {
            return gameDir().resolve("config");
        }

        /** Папка мира на сервере; на клиенте null. */
        @Nullable
        Path worldDir();

        /** Где лежат отметки и отчёт. */
        Path stateDir();

        /** Копия FTB этого переезда; на клиенте и до первого серверного шага null. */
        @Nullable
        Path backupDir();

        /** Сервер или null (клиентский шаг, проверка без игры). */
        @Nullable
        MinecraftServer server();

        Flag flag();

        /** Загружен ли мод с таким id. */
        boolean isLoaded(String modId);

        /** Сделан ли другой шаг. Шаг приватов идёт и без шага команд: UUID команд у FTB и у нас одни. */
        boolean done(String stepId);

        /** Значение, которое дал мод при запуске клиентского шага (например старый id сервера FTB), или пустая строка. */
        String value(String key);

        /** Свои данные шага в файле отметок: прогресс, чтобы продолжить после выхода. Живут и после сбоя. */
        JsonObject data();

        /** Записать файл отметок сейчас (после очередной порции прогресса). */
        void save() throws IOException;

        /** Строка в лог с меткой {@link #TAG} и именем шага. */
        void log(String line);
    }

    /** Итог шага: счётчики, строки, пропущенное. Растёт добавлением, поэтому не запись. */
    public static final class Report {
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final List<String> lines = new ArrayList<>();
        private final List<String> skipped = new ArrayList<>();
        private boolean partial;

        public static Report create() {
            return new Report();
        }

        /** Прибавить к счётчику ({@code teams}, {@code chunks}, {@code forced}). */
        public Report count(String key, long n) {
            counts.merge(key, n, Long::sum);
            return this;
        }

        public Report line(String text) {
            lines.add(text);
            return this;
        }

        /** Что не перенесено и почему: «VENIK: уже есть». */
        public Report skipped(String what, String why) {
            skipped.add(what + ": " + why);
            return this;
        }

        /** Перенесено не всё (файл одной команды не прочитался): отметка ставится с пометкой «частично». */
        public Report partial(String why) {
            partial = true;
            lines.add(why);
            return this;
        }

        public Map<String, Long> counts() {
            return Collections.unmodifiableMap(counts);
        }

        public List<String> lines() {
            return Collections.unmodifiableList(lines);
        }

        public List<String> skipped() {
            return Collections.unmodifiableList(skipped);
        }

        public boolean isPartial() {
            return partial;
        }

        /** Одной строкой для лога и чата. */
        public String summary() {
            StringBuilder out = new StringBuilder();
            counts.forEach((k, v) -> out.append(out.isEmpty() ? "" : ", ").append(k).append(' ').append(v));
            for (String l : lines) out.append(out.isEmpty() ? "" : "; ").append(l);
            if (!skipped.isEmpty()) out.append(out.isEmpty() ? "" : "; ").append("пропущено: ").append(String.join(", ", skipped));
            return out.toString();
        }
    }

    /** Флаг сборки {@code config/ryscore/migrate.json}. */
    public static final class Flag {
        static final Flag NONE = new Flag(null);
        private final @Nullable JsonObject json;

        Flag(@Nullable JsonObject json) {
            this.json = json;
        }

        /** Есть ли файл флага: без него сами шаги не идут. */
        public boolean present() {
            return json != null;
        }

        public int version() {
            return Snbt.integer(json, "v", 0);
        }

        /** Откуда переезд: {@code ftb}. */
        public String from() {
            return Snbt.string(json, "from", "");
        }

        public List<String> steps() {
            List<String> out = new ArrayList<>();
            for (JsonElement e : Snbt.array(json, "steps")) if (e.isJsonPrimitive()) out.add(e.getAsString());
            return out;
        }

        public boolean has(String step) {
            return steps().contains(step);
        }

        /** Поле флага для шага, например {@code ftbChunksAsMap}; null, если нет. */
        public @Nullable JsonElement option(String key) {
            return json == null ? null : json.get(key);
        }

        public boolean bool(String key, boolean fallback) {
            return Snbt.bool(json, key, fallback);
        }
    }

    public enum Status {
        /** Шаг прошёл, отметка стоит. */
        DONE,
        /** Шаг прошёл не целиком, отметка стоит с пометкой «частично». */
        PARTIAL,
        /** Отметка уже была. */
        ALREADY_DONE,
        /** Шага нет во флаге сборки или флага нет. */
        NOT_IN_FLAG,
        /** {@link Step#needed} ответил нет. */
        NOT_NEEDED,
        /** Сбой: отметки нет, повторится при следующем запуске. */
        FAILED,
        /** Откат прошёл, отметка снята. */
        UNDONE
    }

    /** Что случилось с одним шагом. */
    public static final class Outcome {
        private final String id;
        private final Status status;
        private final String message;
        private final @Nullable Report report;

        Outcome(String id, Status status, String message, @Nullable Report report) {
            this.id = id;
            this.status = status;
            this.message = message;
            this.report = report;
        }

        public String id() {
            return id;
        }

        public Status status() {
            return status;
        }

        public String message() {
            return message;
        }

        public @Nullable Report report() {
            return report;
        }

        public boolean ran() {
            return status == Status.DONE || status == Status.PARTIAL;
        }

        @Override
        public String toString() {
            return id + ": " + text(this);
        }
    }

    /** Итог запуска. */
    public static final class Result {
        private final Side side;
        private final List<Outcome> outcomes;
        private final @Nullable Path backup;

        Result(Side side, List<Outcome> outcomes, @Nullable Path backup) {
            this.side = side;
            this.outcomes = List.copyOf(outcomes);
            this.backup = backup;
        }

        public Side side() {
            return side;
        }

        public List<Outcome> outcomes() {
            return outcomes;
        }

        public @Nullable Outcome outcome(String id) {
            for (Outcome o : outcomes) if (o.id.equals(id)) return o;
            return null;
        }

        /** Прошёл ли хоть один шаг: повод сказать оператору «Мир перенесён с FTB». */
        public boolean ran() {
            return outcomes.stream().anyMatch(Outcome::ran);
        }

        public boolean failed() {
            return outcomes.stream().anyMatch(o -> o.status == Status.FAILED);
        }

        public @Nullable Path backup() {
            return backup;
        }
    }

    // ---- реестр ----

    /** Регистрирует шаг. Тот же id заменяет прежний. Звать при запуске мода. */
    public static void register(Step step) {
        Objects.requireNonNull(step.id(), "id");
        synchronized (LOCK) {
            STEPS.put(step.id(), step);
        }
    }

    /** Шаги по порядку. */
    public static List<Step> steps() {
        synchronized (LOCK) {
            List<Step> out = new ArrayList<>(STEPS.values());
            out.sort(Comparator.comparingInt(Step::order).thenComparing(Step::id));
            return out;
        }
    }

    public static @Nullable Step step(String id) {
        synchronized (LOCK) {
            return STEPS.get(id);
        }
    }

    /** Ставит точка входа ядра: загружен ли мод и его версия. Модам не вызывать. */
    public static void loader(Predicate<String> isLoaded, Function<String, String> version) {
        loaded = Objects.requireNonNull(isLoaded);
        versions = Objects.requireNonNull(version);
    }

    /** Для проверок: пустой реестр и загрузчик по умолчанию. */
    static void reset() {
        synchronized (LOCK) {
            STEPS.clear();
        }
        loaded = id -> false;
        versions = id -> "";
        last = null;
    }

    /** Итог последнего запуска в этой игре или null. */
    public static @Nullable Result lastResult() {
        return last;
    }

    /** Читает флаг сборки. Битый файл считается отсутствующим (в лог предупреждение). */
    public static Flag flag(Path gameDir) {
        Path file = gameDir.resolve(FLAG_FILE);
        if (!Files.isRegularFile(file)) return Flag.NONE;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (e.isJsonObject()) return new Flag(e.getAsJsonObject());
            RYSCore.LOG.warn("{} флаг сборки {} не объект, переезд только командой", TAG, file);
        } catch (Exception e) {
            RYSCore.LOG.warn("{} флаг сборки {} не прочитан ({}), переезд только командой", TAG, file, e.toString());
        }
        return Flag.NONE;
    }

    // ---- запуск ----

    /** Серверные шаги при запуске сервера. Зовёт точка входа ядра. */
    public static Result runServer(MinecraftServer server) {
        return runServer(server.getServerDirectory(), server.getWorldPath(LevelResource.ROOT).normalize(), server);
    }

    /** Серверные шаги при запуске: по флагу, без повторов. Для проверки без игры {@code server} может быть null. */
    public static Result runServer(Path gameDir, Path worldDir, @Nullable MinecraftServer server) {
        Engine e = new Engine(Side.SERVER, gameDir, worldDir, worldDir.resolve(STATE_DIR), server, Map.of(), false);
        Result r = e.auto();
        last = r;
        return r;
    }

    /**
     * Клиентские шаги в рабочем потоке. Зовёт мод клиентского шага, когда знает ключ мира.
     *
     * @param stateDir папка отметок этого мира, например {@code <папка игры>/rysmap/<ключ мира>}
     * @param values что нужно шагам ({@link Context#value}), например старый id сервера FTB
     */
    public static CompletableFuture<Result> runClient(Path gameDir, Path stateDir, Map<String, String> values) {
        return CompletableFuture.supplyAsync(() -> runClientNow(gameDir, stateDir, values), r -> {
            Thread t = new Thread(r, "RYS migrate");
            t.setDaemon(true);
            t.start();
        });
    }

    /** Клиентские шаги в текущем потоке. */
    public static Result runClientNow(Path gameDir, Path stateDir, Map<String, String> values) {
        Engine e = new Engine(Side.CLIENT, gameDir, null, stateDir, null, values, false);
        Result r = e.auto();
        last = r;
        return r;
    }

    /** Команда {@code /rys migrate run <шаг>}: без флага, но не поверх отметки и не без {@link Step#needed}. */
    public static Outcome runManual(String id, MinecraftServer server) {
        return runManual(id, server.getServerDirectory(), server.getWorldPath(LevelResource.ROOT).normalize(), server);
    }

    public static Outcome runManual(String id, Path gameDir, Path worldDir, @Nullable MinecraftServer server) {
        return new Engine(Side.SERVER, gameDir, worldDir, worldDir.resolve(STATE_DIR), server, Map.of(), true).manual(id);
    }

    /** Команда {@code /rys migrate undo <шаг>}: откат шагом и снятие отметки. */
    public static Outcome undo(String id, MinecraftServer server) {
        return undo(id, server.getServerDirectory(), server.getWorldPath(LevelResource.ROOT).normalize(), server);
    }

    public static Outcome undo(String id, Path gameDir, Path worldDir, @Nullable MinecraftServer server) {
        return new Engine(Side.SERVER, gameDir, worldDir, worldDir.resolve(STATE_DIR), server, Map.of(), true).undo(id);
    }

    /** Команда {@code /rys migrate status}: флаг, копия и отметки шагов строками для чата. */
    public static List<String> describe(Path gameDir, Path worldDir) {
        List<String> out = new ArrayList<>();
        Flag flag = flag(gameDir);
        out.add(flag.present() ? "флаг сборки: " + flag.from() + " " + flag.steps() : "флага сборки нет: переезд только командой");
        State state = State.load(worldDir.resolve(STATE_DIR));
        if (state.backup() != null) out.add("копия: " + state.backup());
        for (Step s : steps()) {
            if (s.side() != Side.SERVER) continue;
            JsonObject entry = state.entry(s.id());
            String line;
            if (entry == null) {
                line = "не запускался";
            } else if (Snbt.bool(entry, "done", false)) {
                line = (Snbt.bool(entry, "partial", false) ? "сделан частично " : "сделан ") + Snbt.string(entry, "time", "");
            } else {
                line = "сбой " + Snbt.string(entry, "time", "") + ": " + Snbt.string(entry, "error", "");
            }
            out.add(s.id() + " (" + s.order() + "): " + line);
        }
        return out;
    }

    static String text(Outcome o) {
        return switch (o.status) {
            case DONE -> "готово" + (o.message.isEmpty() ? "" : ", " + o.message);
            case PARTIAL -> "частично, " + o.message;
            case ALREADY_DONE -> "пропущен, уже сделан " + o.message;
            case NOT_IN_FLAG -> "пропущен, " + o.message;
            case NOT_NEEDED -> "не нужен" + (o.message.isEmpty() ? "" : ", " + o.message);
            case FAILED -> "сбой: " + o.message;
            case UNDONE -> "откат" + (o.message.isEmpty() ? "" : ", " + o.message);
        };
    }

    // ---- исполнение ----

    private static final class Engine {
        private final Side side;
        private final Path gameDir;
        private final @Nullable Path worldDir;
        private final Path stateDir;
        private final @Nullable MinecraftServer server;
        private final Map<String, String> values;
        private final boolean manual;
        private final Flag flag;
        private State state;
        private boolean backupFailed;

        Engine(Side side, Path gameDir, @Nullable Path worldDir, Path stateDir, @Nullable MinecraftServer server,
               Map<String, String> values, boolean manual) {
            this.side = side;
            this.gameDir = gameDir;
            this.worldDir = worldDir;
            this.stateDir = stateDir;
            this.server = server;
            this.values = Map.copyOf(values);
            this.manual = manual;
            this.flag = flag(gameDir);
        }

        Result auto() {
            synchronized (LOCK) {
                List<Step> steps = steps().stream().filter(s -> s.side() == side).toList();
                List<Outcome> out = new ArrayList<>();
                if (steps.isEmpty()) return new Result(side, out, null);
                if (!flag.present()) {
                    for (Step s : steps) out.add(new Outcome(s.id(), Status.NOT_IN_FLAG, "флага сборки нет", null));
                    RYSCore.LOG.info("{} флага сборки {} нет: переезд с FTB только командой /rys migrate run", TAG, FLAG_FILE);
                    return new Result(side, out, null);
                }
                state = State.load(stateDir);
                for (Step s : steps) out.add(attempt(s, true));
                finish(out);
                return new Result(side, out, state.backup() == null ? null : resolveBackup());
            }
        }

        Outcome manual(String id) {
            synchronized (LOCK) {
                Step s = step(id);
                if (s == null) return new Outcome(id, Status.FAILED, "такого шага нет", null);
                if (s.side() != side) return new Outcome(id, Status.FAILED, "шаг идёт на клиенте", null);
                state = State.load(stateDir);
                Outcome o = attempt(s, false);
                finish(List.of(o));
                return o;
            }
        }

        Outcome undo(String id) {
            synchronized (LOCK) {
                Step s = step(id);
                if (s == null) return new Outcome(id, Status.FAILED, "такого шага нет", null);
                state = State.load(stateDir);
                if (!state.done(id)) return new Outcome(id, Status.FAILED, "отметки нет, откатывать нечего", null);
                Outcome o;
                try {
                    Report r = s.undo(context(s));
                    state.clear(id);
                    state.save();
                    o = new Outcome(id, Status.UNDONE, r == null ? "" : r.summary(), r);
                } catch (Throwable t) {
                    o = new Outcome(id, Status.FAILED, reason(t), null);
                    if (!(t instanceof UnsupportedOperationException)) RYSCore.LOG.warn("{} {}: откат не прошёл", TAG, id, t);
                }
                finish(List.of(o));
                return o;
            }
        }

        private Outcome attempt(Step s, boolean byFlag) {
            String id = s.id();
            if (byFlag && !flag.has(id)) return new Outcome(id, Status.NOT_IN_FLAG, "нет во флаге сборки", null);
            if (state.done(id)) return new Outcome(id, Status.ALREADY_DONE, Snbt.string(state.entry(id), "time", ""), null);
            Context c = context(s);
            try {
                if (!s.needed(c)) return new Outcome(id, Status.NOT_NEEDED, "", null);
            } catch (Throwable t) {
                return fail(s, t);
            }
            if (side == Side.SERVER && !backup()) {
                return new Outcome(id, Status.FAILED, "копия FTB не сделана, шаг не запускался", null);
            }
            long start = System.nanoTime();
            try {
                Report r = s.run(c);
                if (r == null) r = Report.create();
                long ms = (System.nanoTime() - start) / 1_000_000;
                state.markDone(id, r, versions.apply(s.modId()));
                state.save();
                return new Outcome(id, r.isPartial() ? Status.PARTIAL : Status.DONE, (r.summary() + (r.summary().isEmpty() ? "" : ", ") + ms + " мс"), r);
            } catch (Throwable t) {
                return fail(s, t);
            }
        }

        private Outcome fail(Step s, Throwable t) {
            String why = reason(t);
            RYSCore.LOG.warn("{} {}: сбой, шаг повторится при следующем запуске", TAG, s.id(), t);
            try {
                state.markFailed(s.id(), why);
                state.save();
            } catch (IOException e) {
                RYSCore.LOG.warn("{} файл отметок не записан", TAG, e);
            }
            return new Outcome(s.id(), Status.FAILED, why, null);
        }

        private static String reason(Throwable t) {
            String m = t.getMessage();
            return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
        }

        /** Копия перед первым серверным шагом, один раз на мир. */
        private boolean backup() {
            if (backupFailed) return false;
            if (state.backup() != null && Files.isDirectory(resolveBackup())) return true;
            if (worldDir == null) return true;
            String name = "backup/ftb-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path target = stateDir.resolve(name);
            try {
                List<String> paths = new ArrayList<>(BACKUP);
                for (Step s : steps()) if (s.side() == Side.SERVER) paths.addAll(s.backup());
                Files.createDirectories(target);
                long files = 0;
                for (String p : paths) files += Copy.tree(worldDir.resolve(p), target.resolve(p));
                Path data = worldDir.resolve("data");
                if (Files.isDirectory(data)) {
                    try (var list = Files.list(data)) {
                        for (Path f : (Iterable<Path>) list::iterator) {
                            String n = f.getFileName().toString();
                            if (n.startsWith("rys") && n.endsWith(".dat") && Files.isRegularFile(f)) files += Copy.tree(f, target.resolve("data").resolve(n));
                        }
                    }
                }
                state.backup(name);
                state.save();
                RYSCore.LOG.info("{} копия FTB и своих данных: {} ({} файлов)", TAG, target, files);
                return true;
            } catch (IOException e) {
                backupFailed = true;
                RYSCore.LOG.error("{} копия FTB не сделана, серверные шаги не идут", TAG, e);
                return false;
            }
        }

        private Path resolveBackup() {
            return stateDir.resolve(Objects.requireNonNull(state.backup()));
        }

        private Context context(Step s) {
            String id = s.id();
            return new Context() {
                @Override public Side side() { return side; }
                @Override public boolean manual() { return manual; }
                @Override public Path gameDir() { return gameDir; }
                @Override public @Nullable Path worldDir() { return worldDir; }
                @Override public Path stateDir() { return stateDir; }
                @Override public @Nullable Path backupDir() { return state.backup() == null ? null : resolveBackup(); }
                @Override public @Nullable MinecraftServer server() { return server; }
                @Override public Flag flag() { return flag; }
                @Override public boolean isLoaded(String modId) { return loaded.test(modId); }
                @Override public boolean done(String stepId) { return state.done(stepId); }
                @Override public String value(String key) { return values.getOrDefault(key, ""); }
                @Override public JsonObject data() { return state.data(id); }
                @Override public void save() throws IOException { state.save(); }
                @Override public void log(String line) { RYSCore.LOG.info("{} {}: {}", TAG, id, line); }
            };
        }

        /** Лог блоком и отчёт в файл, если что-то шло. */
        private void finish(List<Outcome> out) {
            boolean anything = out.stream().anyMatch(o -> o.ran() || o.status == Status.FAILED || o.status == Status.UNDONE);
            String head = (side == Side.SERVER ? "сервер" : "клиент") + (manual ? ", командой" : "")
                + (flag.present() ? ", флаг " + flag.from() + " " + flag.steps() : ", без флага");
            if (anything) {
                RYSCore.LOG.info("{} {}", TAG, head);
                for (Outcome o : out) RYSCore.LOG.info("{} {}", TAG, o);
            } else {
                RYSCore.LOG.debug("{} {}: {}", TAG, head, out);
                return;
            }
            StringBuilder text = new StringBuilder();
            text.append("=== ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(", ").append(head).append(" ===\n");
            for (Outcome o : out) text.append(o).append('\n');
            text.append('\n');
            try {
                Files.createDirectories(stateDir);
                Files.writeString(stateDir.resolve(REPORT_FILE), text, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException e) {
                RYSCore.LOG.warn("{} отчёт не записан", TAG, e);
            }
        }
    }

    /** Файл отметок {@code migrate.json}. */
    static final class State {
        private final Path file;
        private final JsonObject json;

        private State(Path file, JsonObject json) {
            this.file = file;
            this.json = json;
        }

        static State load(Path dir) {
            Path file = dir.resolve(STATE_FILE);
            JsonObject json = null;
            if (Files.isRegularFile(file)) {
                try {
                    JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    if (e.isJsonObject()) json = e.getAsJsonObject();
                } catch (Exception e) {
                    // битый файл не повод переносить второй раз поверх: старый откладывается, шаги сами проверят «уже есть»
                    RYSCore.LOG.warn("{} файл отметок {} не прочитан, отложен рядом", TAG, file, e);
                    try {
                        Files.move(file, file.resolveSibling(STATE_FILE + ".broken-" + System.currentTimeMillis()));
                    } catch (IOException ignored) {
                        // останется на месте и будет перезаписан
                    }
                }
            }
            if (json == null) {
                json = new JsonObject();
                json.addProperty("v", 1);
            }
            if (!json.has("steps") || !json.get("steps").isJsonObject()) json.add("steps", new JsonObject());
            return new State(file, json);
        }

        @Nullable String backup() {
            JsonElement e = json.get("backup");
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        }

        void backup(String relative) {
            json.addProperty("backup", relative);
        }

        @Nullable JsonObject entry(String id) {
            JsonElement e = json.getAsJsonObject("steps").get(id);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        }

        private JsonObject entryOrNew(String id) {
            JsonObject e = entry(id);
            if (e == null) {
                e = new JsonObject();
                json.getAsJsonObject("steps").add(id, e);
            }
            return e;
        }

        boolean done(String id) {
            return Snbt.bool(entry(id), "done", false);
        }

        JsonObject data(String id) {
            JsonObject e = entryOrNew(id);
            if (!e.has("data") || !e.get("data").isJsonObject()) e.add("data", new JsonObject());
            return e.getAsJsonObject("data");
        }

        void markDone(String id, Report r, String version) {
            JsonObject e = entryOrNew(id);
            e.addProperty("done", true);
            e.addProperty("partial", r.isPartial());
            e.addProperty("time", java.time.Instant.now().toString());
            e.addProperty("version", version);
            e.remove("error");
            JsonObject counts = new JsonObject();
            r.counts().forEach(counts::addProperty);
            e.add("counts", counts);
            JsonArray skipped = new JsonArray();
            r.skipped().forEach(skipped::add);
            e.add("skipped", skipped);
        }

        void markFailed(String id, String why) {
            JsonObject e = entryOrNew(id);
            e.addProperty("done", false);
            e.addProperty("time", java.time.Instant.now().toString());
            e.addProperty("error", why);
        }

        void clear(String id) {
            json.getAsJsonObject("steps").remove(id);
        }

        void save() throws IOException {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(STATE_FILE + ".tmp");
            Files.writeString(tmp, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Копирование папок без зависимостей. */
    static final class Copy {
        private Copy() {}

        /** Копирует файл или папку целиком; отсутствующий источник пропускается. Возвращает число файлов. */
        static long tree(Path from, Path to) throws IOException {
            if (!Files.exists(from)) return 0;
            if (Files.isRegularFile(from)) {
                Files.createDirectories(to.getParent());
                Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                return 1;
            }
            long n = 0;
            try (var walk = Files.walk(from)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    Path t = to.resolve(from.relativize(p).toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(t);
                    } else {
                        Files.createDirectories(t.getParent());
                        Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                        n++;
                    }
                }
            }
            return n;
        }
    }
}
