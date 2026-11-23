package works.repka.ryscore.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.Preview;
import works.repka.ryscore.ui.Icons;

/**
 * Подписи в мире для модов семьи (1.12, план карты 9.4 пункт 6), только клиент, главный поток: значок, имя, расстояние,
 * сквозь стены. Мод отдаёт источник меток ({@link #source}), рисует их одна реализация (RYSMap) своим слоем HUD:
 * разводит наложения, раскрывает метку у прицела, слушает клавишу «показать точки» и режим игрока («Цель и гибель»,
 * «Все рядом», «Все»).
 *
 * <p>Пока реализации нет ({@link #present()} ложь), мод рисует свои метки сам, как раньше (RYSTeams:
 * {@code Presence.markLabel}). Реализация ставит флаг вызовом {@link #provide()}.
 *
 * <p>С 1.15 ядро держит общий для всех модов лимит ближних подписей ({@link #limit()}) и одну отсечку по расстоянию
 * ({@link #cutoff()}); реализация берёт готовый отбор {@link #visible}. Пинг любой мод отдаёт через {@link #ping}:
 * ядро хранит его до срока жизни и отдаёт своим источником {@link #PINGS}, отдельный слой мод не заводит.
 */
@Preview(until = "rysmap 1.0")
public final class Labels {
    /** Вид метки: пинг и тревога видны в любом режиме игрока, остальное по режиму. */
    public enum Kind { POINT, PLAYER, PING, GOAL, ALERT }

    /** Источник меток мода. Зовётся каждый кадр, отвечает из готовых данных. */
    public interface Source {
        ResourceLocation id();

        Collection<Label> labels(ResourceKey<Level> dimension);
    }

    private static final Map<ResourceLocation, Source> SOURCES = new LinkedHashMap<>();
    private static volatile boolean present;
    /** Сколько подписей видно сразу, пинги и тревоги идут первыми. С 1.15. */
    public static final int LIMIT_DEFAULT = 24;
    /** Дальше этого, в блоках, подписи не рисуются ни у одного мода. С 1.15. */
    public static final double CUTOFF_DEFAULT = 512;
    /** Срок жизни пинга без своего срока, в секундах. С 1.15. */
    public static final double PING_SECONDS_DEFAULT = 10;
    /** Источник пингов ядра (1.15). */
    public static final ResourceLocation PINGS = ResourceLocation.fromNamespaceAndPath("ryscore", "pings");
    private static volatile int limit = LIMIT_DEFAULT;
    private static volatile double cutoff = CUTOFF_DEFAULT;
    private static final Map<UUID, Label> PING_LIST = new LinkedHashMap<>();

    static {
        source(new Source() {
            @Override
            public ResourceLocation id() {
                return PINGS;
            }

            @Override
            public Collection<Label> labels(ResourceKey<Level> dimension) {
                long now = System.currentTimeMillis();
                List<Label> out = new ArrayList<>();
                synchronized (PING_LIST) {
                    PING_LIST.values().removeIf(l -> !l.alive(now));
                    for (Label l : PING_LIST.values()) if (l.dimension().equals(dimension)) out.add(l);
                }
                return out;
            }
        });
    }

    private Labels() {}

    /** Источник меток. Тот же id заменяет прежний. */
    public static void source(Source source) {
        synchronized (SOURCES) {
            SOURCES.put(Objects.requireNonNull(source.id()), source);
        }
    }

    public static void unregister(ResourceLocation id) {
        synchronized (SOURCES) {
            SOURCES.remove(id);
        }
    }

    /** Источники всех модов, для реализации. */
    public static List<Source> sources() {
        synchronized (SOURCES) {
            return new ArrayList<>(SOURCES.values());
        }
    }

    /** Есть ли реализация, которая рисует метки источников. */
    public static boolean present() {
        return present;
    }

    /** Вызывает реализация (RYSMap) при запуске клиента. */
    public static void provide() {
        present = true;
    }

    // --- общий лимит и отсечка (1.15) ---

    public static int limit() {
        return limit;
    }

    /** Настройка игрока у реализации; не меньше 1. */
    public static void setLimit(int value) {
        limit = Math.max(1, value);
    }

    public static double cutoff() {
        return cutoff;
    }

    public static void setCutoff(double blocks) {
        cutoff = Math.max(16, blocks);
    }

    /**
     * Готовый отбор для реализации: метки всех источников в измерении, живые, не дальше {@link #cutoff()}, сначала
     * пинги и тревоги, потом цели, потом ближние, не больше {@link #limit()}. Упавший источник пропускается.
     */
    public static List<Label> visible(ResourceKey<Level> dimension, double x, double y, double z) {
        long now = System.currentTimeMillis();
        List<Label> all = new ArrayList<>();
        for (Source s : sources()) {
            try {
                Collection<Label> list = s.labels(dimension);
                if (list != null) for (Label l : list) if (l.alive(now) && l.dimension().equals(dimension)) all.add(l);
            } catch (RuntimeException ignored) {
                // чужой источник не должен ронять подписи остальных
            }
        }
        return pick(all, l -> {
            double dx = l.x() - x, dy = l.y() - y, dz = l.z() - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }, l -> l.kind() == Kind.PING || l.kind() == Kind.ALERT ? 0 : l.kind() == Kind.GOAL ? 1 : 2, limit, cutoff);
    }

    /** Отбор без игры: отсечка по расстоянию, порядок по старшинству вида и расстоянию, предел числа. */
    static <T> List<T> pick(List<T> all, ToDoubleFunction<T> distance, java.util.function.ToIntFunction<T> rank, int limit, double cutoff) {
        List<T> near = new ArrayList<>();
        for (T t : all) if (distance.applyAsDouble(t) <= cutoff) near.add(t);
        near.sort(Comparator.comparingInt(rank).thenComparingDouble(distance));
        return near.size() > limit ? new ArrayList<>(near.subList(0, limit)) : near;
    }

    // --- пинги (1.15) ---

    /**
     * Пинг в мире от любого мода: метка вида {@link Kind#PING} с кольцом и сроком жизни ({@link Label.Builder#lifetime},
     * без срока {@link #PING_SECONDS_DEFAULT} с). Тот же id заменяет прежний пинг. Рисует реализация (RYSMap) вместе
     * с остальными метками; пока её нет, мод рисует пинг сам.
     */
    public static void ping(Label label) {
        Label l = label.kind() == Kind.PING && label.until() > 0 ? label : label.asPing(PING_SECONDS_DEFAULT);
        synchronized (PING_LIST) {
            PING_LIST.put(l.id(), l);
        }
    }

    public static void removePing(UUID id) {
        synchronized (PING_LIST) {
            PING_LIST.remove(id);
        }
    }

    /** Убрать пинги по условию: мод снимает свои при выходе из мира. */
    public static void removePings(Predicate<Label> which) {
        synchronized (PING_LIST) {
            PING_LIST.values().removeIf(which);
        }
    }

    /** Живые пинги всех модов во всех измерениях. */
    public static List<Label> pings() {
        long now = System.currentTimeMillis();
        synchronized (PING_LIST) {
            PING_LIST.values().removeIf(l -> !l.alive(now));
            return new ArrayList<>(PING_LIST.values());
        }
    }

    /** Метка в мире. Собирается {@link #builder(UUID)}. */
    public static final class Label {
        private final UUID id;
        private final ResourceKey<Level> dimension;
        private final double x, y, z;
        private final Kind kind;
        private final @Nullable Icons.Shape icon;
        private final ItemStack item;
        private final @Nullable UUID head;
        private final int color;
        private final Component text;
        private final boolean always;
        private final boolean ring;
        private final long until;

        private Label(Builder b) {
            this.id = b.id;
            this.dimension = Objects.requireNonNull(b.dimension, "A label needs a place: pos(dimension, x, y, z)");
            this.x = b.x;
            this.y = b.y;
            this.z = b.z;
            this.kind = b.kind;
            this.icon = b.icon;
            this.item = b.item;
            this.head = b.head;
            this.color = b.color;
            this.text = b.text;
            this.always = b.always;
            this.ring = b.ring != null ? b.ring : b.kind == Kind.PING;
            this.until = b.until;
        }

        /** Та же метка пингом: вид {@link Kind#PING}, кольцо, срок жизни, если его не было. */
        Label asPing(double seconds) {
            Builder b = new Builder(id).pos(dimension, x, y, z).kind(Kind.PING).icon(icon).item(item).head(head).color(color).text(text)
                .always(always).ring(true);
            b.until = until > 0 ? until : System.currentTimeMillis() + Math.round(seconds * 1000);
            return new Label(b);
        }

        public static Builder builder(UUID id) {
            return new Builder(Objects.requireNonNull(id));
        }

        public UUID id() { return id; }
        public ResourceKey<Level> dimension() { return dimension; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public Kind kind() { return kind; }
        public @Nullable Icons.Shape icon() { return icon; }
        public ItemStack item() { return item; }
        public @Nullable UUID head() { return head; }
        /** Цвет ARGB значка. */
        public int color() { return color; }
        public Component text() { return text; }
        /** Видна в любом режиме игрока (как пинг), а не только по клавише. */
        public boolean always() { return always || kind == Kind.PING || kind == Kind.ALERT; }
        /** Кольцо вокруг метки, как у пинга. С 1.15. */
        public boolean ring() { return ring; }
        /** Когда метка гаснет (мс эпохи), 0 — не гаснет. С 1.15. */
        public long until() { return until; }
        /** Жива ли метка в момент {@code now}. С 1.15. */
        public boolean alive(long now) { return until <= 0 || now < until; }

        public static final class Builder {
            private final UUID id;
            private @Nullable ResourceKey<Level> dimension;
            private double x, y, z;
            private Kind kind = Kind.POINT;
            private @Nullable Icons.Shape icon;
            private ItemStack item = ItemStack.EMPTY;
            private @Nullable UUID head;
            private int color = 0xFFF7F8FB;
            private Component text = Component.empty();
            private boolean always;
            private @Nullable Boolean ring;
            private long until;

            private Builder(UUID id) {
                this.id = id;
            }

            public Builder pos(ResourceKey<Level> dimension, double x, double y, double z) {
                this.dimension = Objects.requireNonNull(dimension);
                this.x = x;
                this.y = y;
                this.z = z;
                return this;
            }

            public Builder kind(Kind kind) { this.kind = Objects.requireNonNull(kind); return this; }
            public Builder icon(@Nullable Icons.Shape icon) { this.icon = icon; return this; }
            public Builder item(@Nullable ItemStack item) { this.item = item == null ? ItemStack.EMPTY : item; return this; }
            public Builder head(@Nullable UUID player) { this.head = player; return this; }
            public Builder color(int argb) { this.color = argb; return this; }
            public Builder text(Component text) { this.text = Objects.requireNonNull(text); return this; }
            public Builder always(boolean always) { this.always = always; return this; }
            /** Кольцо вокруг метки (у пинга по умолчанию есть). С 1.15. */
            public Builder ring(boolean ring) { this.ring = ring; return this; }
            /** Гаснет в момент {@code epochMillis}. С 1.15. */
            public Builder until(long epochMillis) { this.until = epochMillis; return this; }
            /** Гаснет через {@code seconds} от сейчас. С 1.15. */
            public Builder lifetime(double seconds) { this.until = System.currentTimeMillis() + Math.round(seconds * 1000); return this; }

            public Label build() {
                return new Label(this);
            }
        }
    }
}
