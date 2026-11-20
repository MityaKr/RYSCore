package works.repka.ryscore.action;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;

/**
 * Действие мода семьи (1.14): то, что игрок может положить в колесо быстрых действий или повесить на свою клавишу.
 * Мод отдаёт его в {@link Actions} при запуске клиента. Состояние (готово, пауза, недоступно) считает мод, ядро только
 * показывает. Не запись: в 1.x к строителю можно добавлять поля.
 *
 * <pre>{@code
 * Actions.register(Action.builder(ResourceLocation.fromNamespaceAndPath("rysmap", "home"))
 *     .label(Component.translatable("rysmap.action.home")).detail(() -> "База · 24 м").icon(Icons.Shape.HOME)
 *     .group(Action.Group.PLACES).state(() -> Action.State.pause(24)).run(() -> ...).build());
 * }</pre>
 */
public final class Action {
    /** Группа в окне «Клавиши»: вкладка-фильтр и заголовок списка. */
    public static final class Group {
        public static final Group PLACES = new Group("places", () -> Component.translatable("ryscore.action.group.places"), 0);
        public static final Group TEAM = new Group("team", () -> Component.translatable("ryscore.action.group.team"), 10);
        public static final Group SERVER = new Group("server", () -> Component.translatable("ryscore.action.group.server"), 20);
        public static final Group MODERATION = new Group("moderation", () -> Component.translatable("ryscore.action.group.moderation"), 30);
        public static final Group OPERATOR = new Group("operator", () -> Component.translatable("ryscore.action.group.operator"), 40);
        public static final Group WINDOWS = new Group("windows", () -> Component.translatable("ryscore.action.group.windows"), 50);

        private final String id;
        private final Supplier<Component> label;
        private final int order;

        private Group(String id, Supplier<Component> label, int order) {
            this.id = id;
            this.label = label;
            this.order = order;
        }

        /** Своя группа мода; группы с одним id считаются одной. */
        public static Group of(String id, Supplier<Component> label, int order) {
            return new Group(id, label, order);
        }

        public String id() {
            return id;
        }

        public Component label() {
            return label.get();
        }

        public int order() {
            return order;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Group g && g.id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    /** Что сейчас с действием. Ядро красит сектор и строку: пауза показывает «0:24», недоступное серое с причиной. */
    public static final class State {
        public enum Kind { READY, ACTIVE, PAUSE, OFF }

        private static final State READY_STATE = new State(Kind.READY, 0, "");
        private static final State ACTIVE_STATE = new State(Kind.ACTIVE, 0, "");

        private final Kind kind;
        private final int seconds;
        private final String reason;

        private State(Kind kind, int seconds, String reason) {
            this.kind = kind;
            this.seconds = seconds;
            this.reason = reason;
        }

        public static State ready() {
            return READY_STATE;
        }

        /** Переключатель включён (полёт, обход привата): сектор отмечен. */
        public static State active() {
            return ACTIVE_STATE;
        }

        /** Пауза ещё {@code seconds} секунд. */
        public static State pause(int seconds) {
            return seconds <= 0 ? READY_STATE : new State(Kind.PAUSE, seconds, "");
        }

        /** Недоступно, причина видна игроку: «Дом не поставлен». */
        public static State off(String reason) {
            return new State(Kind.OFF, 0, reason == null ? "" : reason);
        }

        public Kind kind() {
            return kind;
        }

        public int seconds() {
            return seconds;
        }

        public String reason() {
            return reason;
        }

        /** Можно ли выполнить сейчас. */
        public boolean usable() {
            return kind == Kind.READY || kind == Kind.ACTIVE;
        }

        /** Пауза как на кнопке: «0:24», «1:05:00». */
        public String clock() {
            int s = Math.max(0, seconds);
            if (s >= 3600) return s / 3600 + ":" + String.format("%02d:%02d", s / 60 % 60, s % 60);
            return s / 60 + ":" + String.format("%02d", s % 60);
        }
    }

    private final ResourceLocation id;
    private final Supplier<Component> label;
    private final @Nullable Supplier<Component> shortLabel;
    private final Supplier<String> detail;
    private final String section;
    private final String right;
    private final @Nullable Icons.Shape shape;
    private final ItemStack item;
    private final @Nullable ResourceLocation texture;
    private final int color;
    private final Group group;
    private final Supplier<State> state;
    private final @Nullable IntSupplier badge;
    private final @Nullable Runnable run;
    private final @Nullable Supplier<List<Action>> list;
    private final @Nullable Supplier<List<Action>> nested;
    private final int order;
    private final boolean bindable;

    private Action(Builder b) {
        this.id = b.id;
        this.label = b.label;
        this.shortLabel = b.shortLabel;
        this.detail = b.detail;
        this.section = b.section;
        this.right = b.right;
        this.shape = b.shape;
        this.item = b.item;
        this.texture = b.texture;
        this.color = b.color;
        this.group = b.group;
        this.state = b.state;
        this.badge = b.badge;
        this.run = b.run;
        this.list = b.list;
        this.nested = b.nested;
        this.order = b.order;
        this.bindable = b.bindable;
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

    /**
     * Короткое имя для сектора колеса, когда полное не помещается даже чуть мельче (1.16): «Пинг» вместо «Пинг команде».
     * Без своего ({@link Builder#shortLabel}) берётся перевод с ключом подписи и суффиксом {@code .short}
     * ({@code rysteams.action.ping.short}), если он есть, иначе полное имя.
     */
    public Component shortLabel() {
        if (shortLabel != null) {
            Component c = shortLabel.get();
            if (c != null) return c;
        }
        Component full = label();
        if (full.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey() + ".short";
            if (Language.getInstance().has(key)) return Component.translatable(key, tc.getArgs());
        }
        return full;
    }

    /** Есть ли у действия подменю рядом с колесом: список ({@link #opensList}) или набор действий ({@link #opensNested}). С 1.16. */
    public boolean opensSub() {
        return list != null || nested != null;
    }

    /** Строки подменю: список или набор действий. Пусто, если подменю нет. С 1.16. */
    public List<Action> sub() {
        return list != null ? list() : nested();
    }

    /** Вторая строка: в середине колеса при наведении (с 1.16 в секторе её нет) и в строке окна клавиш («База · 24 м»). Пусто, если нечего сказать. */
    public String detail() {
        String d = detail.get();
        return d == null ? "" : d;
    }

    /** Заголовок части списка, в которой стоит действие (измерение места). Только для списков рядом с колесом. */
    public String section() {
        return section;
    }

    /** Короткий текст справа в строке списка: расстояние «117 м». */
    public String right() {
        return right;
    }

    public @Nullable Icons.Shape shape() {
        return shape;
    }

    public ItemStack item() {
        return item;
    }

    /** Белый значок 42×42, как у кнопки панели семьи; красится цветом текста. */
    public @Nullable ResourceLocation texture() {
        return texture;
    }

    /** Цвет значка в списке (цвет места или измерения), 0: цвет текста. */
    public int color() {
        return color;
    }

    public Group group() {
        return group;
    }

    public State state() {
        State s = state.get();
        return s == null ? State.ready() : s;
    }

    /** Число на углу значка («21 место», «3 жалобы»), меньше единицы: не показывать. */
    public int badge() {
        return badge == null ? 0 : badge.getAsInt();
    }

    /** Открывает ли действие список рядом с колесом («Места сервера…»). */
    public boolean opensList() {
        return list != null;
    }

    /** Действия списка: каждое место отдельно. Пусто, если действие не список. */
    public List<Action> list() {
        if (list == null) return List.of();
        List<Action> out = list.get();
        return out == null ? List.of() : out;
    }

    /** Открывает ли действие набор действий («Перенести…»), с 1.15; с 1.16 он встаёт подменю рядом с колесом, как список. */
    public boolean opensNested() {
        return nested != null;
    }

    /** Действия набора «Перенести…». Пусто, если действие не набор. С 1.15. */
    public List<Action> nested() {
        if (nested == null) return List.of();
        List<Action> out = nested.get();
        return out == null ? List.of() : out;
    }

    public int order() {
        return order;
    }

    /** Можно ли повесить своё сочетание клавиш. */
    public boolean bindable() {
        return bindable;
    }

    /** Выполняет действие как есть, без проверки состояния. Обычно зовут {@link Actions#run(Action)}. */
    public void perform() {
        if (run != null) run.run();
    }

    boolean hasRun() {
        return run != null;
    }

    public static final class Builder {
        private final ResourceLocation id;
        private Supplier<Component> label;
        private @Nullable Supplier<Component> shortLabel;
        private Supplier<String> detail = () -> "";
        private String section = "", right = "";
        private @Nullable Icons.Shape shape;
        private ItemStack item = ItemStack.EMPTY;
        private @Nullable ResourceLocation texture;
        private int color;
        private Group group = Group.WINDOWS;
        private Supplier<State> state = State::ready;
        private @Nullable IntSupplier badge;
        private @Nullable Runnable run;
        private @Nullable Supplier<List<Action>> list;
        private @Nullable Supplier<List<Action>> nested;
        private int order;
        private boolean bindable = true;

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id);
            Component fallback = Component.literal(id.getPath());
            this.label = () -> fallback;
        }

        public Builder label(Component label) {
            this.label = () -> label;
            return this;
        }

        public Builder label(Supplier<Component> label) {
            this.label = label;
            return this;
        }

        /** Короткое имя для тесного сектора колеса (1.16); без него ищется перевод {@code <ключ подписи>.short}. */
        public Builder shortLabel(Component shortLabel) {
            this.shortLabel = () -> shortLabel;
            return this;
        }

        public Builder shortLabel(Supplier<Component> shortLabel) {
            this.shortLabel = shortLabel;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = () -> detail;
            return this;
        }

        public Builder detail(Supplier<String> detail) {
            this.detail = detail;
            return this;
        }

        public Builder section(String section) {
            this.section = section == null ? "" : section;
            return this;
        }

        public Builder right(String right) {
            this.right = right == null ? "" : right;
            return this;
        }

        public Builder icon(Icons.Shape shape) {
            this.shape = shape;
            return this;
        }

        public Builder item(ItemStack item) {
            this.item = item == null ? ItemStack.EMPTY : item;
            return this;
        }

        public Builder texture(ResourceLocation texture) {
            this.texture = texture;
            return this;
        }

        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        public Builder group(Group group) {
            this.group = Objects.requireNonNull(group);
            return this;
        }

        public Builder state(Supplier<State> state) {
            this.state = Objects.requireNonNull(state);
            return this;
        }

        public Builder badge(IntSupplier badge) {
            this.badge = badge;
            return this;
        }

        public Builder run(Runnable run) {
            this.run = run;
            return this;
        }

        /** Действие открывает список рядом с колесом (подменю с поиском): на клавише и в секторе тоже открывается список. */
        public Builder list(Supplier<List<Action>> list) {
            this.list = list;
            return this;
        }

        /**
         * Действие открывает набор действий (1.15): «Перенести…» со спавном, местами, домом команды. С 1.16 это подменю
         * рядом с колесом, колесо остаётся на месте; строка-список внутри набора раскрывается в том же подменю.
         * Esc и «Назад» закрывают подменю. На своей клавише открывает колесо сразу с этим подменю.
         */
        public Builder nested(Supplier<List<Action>> nested) {
            this.nested = nested;
            return this;
        }

        /** Место в группе: меньше — выше. */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /** Без своей клавиши (только колесо). */
        public Builder notBindable() {
            this.bindable = false;
            return this;
        }

        public Action build() {
            return new Action(this);
        }

        /** Строит и сразу регистрирует. */
        public Action register() {
            Action a = build();
            Actions.register(a);
            return a;
        }
    }
}
