package works.repka.ryscore.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

        private Label(Builder b) {
            this.id = b.id;
            this.dimension = Objects.requireNonNull(b.dimension, "Метке нужно место: pos(dimension, x, y, z)");
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

            public Label build() {
                return new Label(this);
            }
        }
    }
}
