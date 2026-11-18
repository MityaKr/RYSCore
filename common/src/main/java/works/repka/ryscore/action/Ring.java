package works.repka.ryscore.action;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Кольцо колеса быстрых действий (1.14): восемь секторов. Ядро держит три кольца: «Моё» (всем), «Модерация»
 * (модератору) и «Оператор» (оператору); мод может добавить своё. Что лежит в секторах, выбирает игрок в окне «Клавиши»,
 * пока он ничего не менял, сектора заполняются умолчаниями кольца по порядку из тех действий, что есть.
 */
public final class Ring {
    public static final int SECTORS = 8;
    public static final ResourceLocation MINE = id("mine"), MODERATION = id("moderation"), OPERATOR = id("operator");

    private final ResourceLocation id;
    private final Supplier<Component> label;
    private final int order;
    private final BooleanSupplier available;
    private final List<ResourceLocation> defaults;

    private Ring(Builder b) {
        this.id = b.id;
        this.label = b.label;
        this.order = b.order;
        this.available = b.available;
        this.defaults = List.copyOf(b.defaults);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ryscore", path);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public ResourceLocation id() {
        return id;
    }

    public Component label() {
        return label.get();
    }

    public int order() {
        return order;
    }

    /** Видно ли кольцо игроку сейчас. */
    public boolean available() {
        return available.getAsBoolean();
    }

    /** Умолчания по порядку; отсутствующие действия пропускаются. Подсказки модов ({@link Actions#suggest}) идут после. */
    public List<ResourceLocation> defaults() {
        return defaults;
    }

    /**
     * Раскладка кольца: сохранённая игроком как есть (отсутствующее действие даёт пустой сектор), иначе умолчания кольца,
     * подсказки модов и запасные по порядку из тех, что есть. Ровно восемь элементов, null — пусто.
     */
    static List<@Nullable ResourceLocation> layout(@Nullable List<String> saved, List<ResourceLocation> defaults, List<ResourceLocation> suggested,
                                                   List<ResourceLocation> fallback, Predicate<ResourceLocation> exists) {
        List<ResourceLocation> out = new ArrayList<>(Ring.SECTORS);
        if (saved != null) {
            for (int i = 0; i < Ring.SECTORS; i++) {
                String s = i < saved.size() ? saved.get(i) : "";
                ResourceLocation id = s == null || s.isEmpty() ? null : ResourceLocation.tryParse(s);
                out.add(id != null && exists.test(id) ? id : null);
            }
            return out;
        }
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (List<ResourceLocation> source : List.of(defaults, suggested, fallback)) {
            for (ResourceLocation id : source) {
                if (seen.size() >= Ring.SECTORS) break;
                if (exists.test(id)) seen.add(id);
            }
        }
        out.addAll(seen);
        while (out.size() < Ring.SECTORS) out.add(null);
        return out;
    }

    public static final class Builder {
        private final ResourceLocation id;
        private Supplier<Component> label;
        private int order = 100;
        private BooleanSupplier available = () -> true;
        private final List<ResourceLocation> defaults = new ArrayList<>();

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id);
            Component fallback = Component.literal(id.getPath());
            this.label = () -> fallback;
        }

        public Builder label(Supplier<Component> label) {
            this.label = label;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder available(BooleanSupplier available) {
            this.available = available;
            return this;
        }

        public Builder defaults(String... actionIds) {
            for (String s : actionIds) defaults.add(ResourceLocation.parse(s));
            return this;
        }

        public Ring build() {
            return new Ring(this);
        }
    }
}
