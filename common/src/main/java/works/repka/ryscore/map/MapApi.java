package works.repka.ryscore.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.Preview;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/**
 * Карта для модов семьи (1.11), только на клиенте, из главного потока. Пока RYSMap не установлен, {@link #present()} ложь,
 * {@link #open} и {@link #track} ничего не делают, {@link #drawTiles} ничего не рисует и отвечает ложью.
 *
 * <p>Слои, пункты меню и строки под миникартой хранит ядро: мод регистрирует их когда угодно, хоть до загрузки RYSMap,
 * а RYSMap читает {@link #layers()}, {@link #menus()}, {@link #statuses()}.
 *
 * <p>Метка, подпись области, пункт меню, цель и строка статуса собираются строителями, а не записями: в 1.x к ним можно
 * добавлять поля, не ломая старые моды. Записью остаётся только неизменяемое место {@link Spot}.
 *
 * <p>С 1.12 (этап Я4) есть режимы полотна ({@link #mode}), вкладки окна ({@link #tab}), подсветка биомов и высот
 * ({@link #highlight}), поиск слоя ({@link Layer#search}), правая панель чужой метки ({@link Marker#panel()}) и её правка
 * ({@link Marker#editor()}). Подписи в мире лежат отдельно, в {@link works.repka.ryscore.world.Labels}.
 */
@Preview(until = "rysmap 1.0")
public interface MapApi {
    /** Установлен ли RYSMap. Без него окно земли RYSClaims рисует тёмный фон вместо тайлов. */
    default boolean present() {
        return false;
    }

    /** Слой в списке «Слои». Тот же id заменяет прежний. */
    default void layer(Layer layer) {
        Holder.put(Holder.LAYERS, layer.id(), layer);
    }

    /** Пункт меню по правой кнопке на карте. Тот же id заменяет прежний. */
    default void menu(MenuItem item) {
        Holder.put(Holder.MENUS, item.id(), item);
    }

    /**
     * Строка под миникартой: «чья земля», «оператор», тревога. Поставщик зовётся каждый кадр, null прячет строку.
     * Меньший {@code order} стоит выше. Тот же id заменяет прежнюю.
     */
    default void status(ResourceLocation id, int order, Supplier<@Nullable Status> line) {
        Holder.put(Holder.STATUSES, id, new StatusLine(id, order, line));
    }

    /** Режим полотна рядом с «Обзор» (режим «Приват» RYSClaims). Тот же id заменяет прежний. С 1.12. */
    default void mode(Mode mode) {
        Holder.put(Holder.MODES, mode.id(), mode);
    }

    /** Вкладка окна карты рядом с «Карта» (вкладка «Земля» RYSClaims). Тот же id заменяет прежнюю. С 1.12. */
    default void tab(Tab tab) {
        Holder.put(Holder.TABS, tab.id(), tab);
    }

    /**
     * Подсветка на карте: биомы и высоты на приглушённой карте («где искать» RYScope, макет {@code 20}); null снимает.
     * Одна за раз: новая заменяет прежнюю. С 1.12.
     */
    default void highlight(@Nullable Highlight highlight) {
        Holder.highlight = highlight;
    }

    /** Открыть окно карты на месте цели. */
    default void open(Target target) {}

    /** Открыть окно карты на вкладке ({@link #tab}); неизвестная вкладка открывает карту. С 1.12. */
    default void openTab(ResourceLocation tab) {}

    /** Открыть окно карты в режиме полотна ({@link #mode}) на месте цели или на игроке (null). С 1.12. */
    default void openMode(ResourceLocation mode, @Nullable Target at) {}

    /** Вести путь к цели (стрелка на миникарте и в мире); null снимает. */
    default void track(@Nullable Target target) {}

    /**
     * Фон карты в чужом окне: тайлы измерения вокруг точки в прямоугольнике {@code x, y, w, h} единиц текущей матрицы.
     * Окно земли RYSClaims рисует поверх свою сетку чанков.
     *
     * @param pixelsPerBlock сколько единиц текущей матрицы приходится на блок
     * @return ложь, если ничего не нарисовано (RYSMap нет, область не разведана): тогда окно рисует свой фон
     */
    default boolean drawTiles(GuiGraphics g, ResourceKey<Level> dimension, double centerX, double centerZ, double pixelsPerBlock,
                              int x, int y, int w, int h) {
        return false;
    }

    // ---- реестр ----

    static MapApi get() {
        return Holder.current;
    }

    /** Вызывает RYSMap при запуске клиента. */
    static void provide(MapApi implementation) {
        Holder.current = Objects.requireNonNull(implementation);
    }

    /** Слои всех модов по {@link Layer#order()}, для реализации. */
    static List<Layer> layers() {
        List<Layer> out = Holder.values(Holder.LAYERS);
        out.sort(Comparator.comparingInt(Layer::order));
        return out;
    }

    /** Пункты меню по {@link MenuItem#order()}, для реализации. */
    static List<MenuItem> menus() {
        List<MenuItem> out = Holder.values(Holder.MENUS);
        out.sort(Comparator.comparingInt(MenuItem::order));
        return out;
    }

    /** Режимы полотна по {@link Mode#order()}, для реализации. С 1.12. */
    static List<Mode> modes() {
        List<Mode> out = Holder.values(Holder.MODES);
        out.sort(Comparator.comparingInt(Mode::order));
        return out;
    }

    /** Вкладки окна по {@link Tab#order()}, для реализации. С 1.12. */
    static List<Tab> tabs() {
        List<Tab> out = Holder.values(Holder.TABS);
        out.sort(Comparator.comparingInt(Tab::order));
        return out;
    }

    /** Текущая подсветка или null, для реализации. С 1.12. */
    static @Nullable Highlight highlighted() {
        return Holder.highlight;
    }

    /** Строки под миникартой по порядку, для реализации. */
    static List<StatusLine> statuses() {
        List<StatusLine> out = Holder.values(Holder.STATUSES);
        out.sort(Comparator.comparingInt(StatusLine::order));
        return out;
    }

    // ---- слой ----

    /** Слой меток и областей. Методы зовутся из главного потока клиента и отвечают из готовых данных. */
    interface Layer {
        ResourceLocation id();

        Component name();

        Icons.Shape icon();

        int order();

        default boolean onByDefault() {
            return true;
        }

        /** Растёт при любой смене меток. Карта пересобирает метки, только когда число изменилось. */
        long revision();

        Collection<Marker> markers(ResourceKey<Level> dimension);

        /** Области по чанкам (земля, зоны) или null. */
        default @Nullable Areas areas() {
            return null;
        }

        /** Результаты поиска слоя по строке поиска окна: участки, цели квестов, игроки. С 1.12. */
        default List<Hit> search(String query) {
            return List.of();
        }
    }

    enum Kind { POINT, PLAYER, PING, GOAL, ALERT }

    /** Метка на карте и миникарте. Собирается {@link #builder(UUID)}. */
    final class Marker {
        private final UUID id;
        private final ResourceKey<Level> dimension;
        private final double x, y, z;
        private final Kind kind;
        private final @Nullable Icons.Shape icon;
        private final ItemStack item;
        private final @Nullable UUID head;
        private final int color;
        private final Component label;
        private final @Nullable Component note;
        private final float radius;
        private final boolean minimap, inWorld;
        private final @Nullable Supplier<List<Component>> tooltip;
        private final @Nullable Runnable click;
        private final @Nullable Supplier<Panel> panel;
        private final @Nullable Editor editor;

        private Marker(Builder b) {
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
            this.label = b.label;
            this.note = b.note;
            this.radius = b.radius;
            this.minimap = b.minimap;
            this.inWorld = b.inWorld;
            this.tooltip = b.tooltip;
            this.click = b.click;
            this.panel = b.panel;
            this.editor = b.editor;
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
        /** Значок метки. Вид выбирается так: голова, если задана, потом предмет, потом значок. */
        public @Nullable Icons.Shape icon() { return icon; }
        /** Предмет вместо значка; пустой, если не задан. Ведёт себя как предмет просмотрщика: подсказка, R и U. */
        public ItemStack item() { return item; }
        /** Голова игрока вместо значка. */
        public @Nullable UUID head() { return head; }
        /** Цвет ARGB. */
        public int color() { return color; }
        public Component label() { return label; }
        public @Nullable Component note() { return note; }
        /** Радиус области вокруг метки в блоках, 0 без области. */
        public float radius() { return radius; }
        public boolean minimap() { return minimap; }
        /** Подпись в мире. В первом выпуске карта рисует её только у цели пути. */
        public boolean inWorld() { return inWorld; }
        public @Nullable Supplier<List<Component>> tooltip() { return tooltip; }
        public @Nullable Runnable click() { return click; }
        /** Правая панель по клику (цель квеста, участник), null: клик зовёт {@link #click()}. С 1.12. */
        public @Nullable Supplier<Panel> panel() { return panel; }
        /** Правка метки из окна карты («кто видит», имя, место, удаление), null: правки нет. С 1.12. */
        public @Nullable Editor editor() { return editor; }

        public static final class Builder {
            private final UUID id;
            private @Nullable ResourceKey<Level> dimension;
            private double x, y, z;
            private Kind kind = Kind.POINT;
            private @Nullable Icons.Shape icon;
            private ItemStack item = ItemStack.EMPTY;
            private @Nullable UUID head;
            private int color = 0xFFF7F8FB;
            private Component label = Component.empty();
            private @Nullable Component note;
            private float radius;
            private boolean minimap = true, inWorld;
            private @Nullable Supplier<List<Component>> tooltip;
            private @Nullable Runnable click;
            private @Nullable Supplier<Panel> panel;
            private @Nullable Editor editor;

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
            public Builder label(Component label) { this.label = Objects.requireNonNull(label); return this; }
            public Builder note(@Nullable Component note) { this.note = note; return this; }
            public Builder radius(float blocks) { this.radius = Math.max(0, blocks); return this; }
            public Builder minimap(boolean shown) { this.minimap = shown; return this; }
            public Builder inWorld(boolean shown) { this.inWorld = shown; return this; }
            public Builder tooltip(@Nullable Supplier<List<Component>> lines) { this.tooltip = lines; return this; }
            public Builder click(@Nullable Runnable action) { this.click = action; return this; }
            public Builder panel(@Nullable Supplier<Panel> panel) { this.panel = panel; return this; }
            public Builder editor(@Nullable Editor editor) { this.editor = editor; return this; }

            public Marker build() {
                return new Marker(this);
            }
        }
    }

    // ---- области по чанкам: земля, зоны ----

    interface Areas {
        /** Растёт, когда регион 32×32 чанка нужно перерисовать. */
        long revision(ResourceKey<Level> dimension, int regionX, int regionZ);

        /** Заполняет регион 32×32 чанка. */
        void fill(ResourceKey<Level> dimension, int regionX, int regionZ, AreaSink sink);

        /** Подсказка чанка под курсором или null. */
        default @Nullable List<Component> tooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            return null;
        }

        /** Подписи областей: «Репка · База» у угла участка. */
        default Collection<AreaLabel> labels(ResourceKey<Level> dimension) {
            return List.of();
        }
    }

    interface AreaSink {
        /**
         * @param group чанки одной группы обводятся общим контуром
         * @param color ARGB
         * @param badge значок на чанке (прогрузка)
         */
        void chunk(int chunkX, int chunkZ, int group, int color, AreaStyle style, boolean badge);
    }

    /** FILL заливка с обводкой, HATCH штриховка, DASHED пунктирная обводка. */
    enum AreaStyle { FILL, HATCH, DASHED }

    /** Подпись области у её чанка. Собирается {@link #builder(int, int, Component)}. */
    final class AreaLabel {
        private final int chunkX, chunkZ, color;
        private final Component title;
        private final @Nullable Component note;
        private final @Nullable Icons.Shape mark;

        private AreaLabel(Builder b) {
            this.chunkX = b.chunkX;
            this.chunkZ = b.chunkZ;
            this.color = b.color;
            this.title = b.title;
            this.note = b.note;
            this.mark = b.mark;
        }

        public static Builder builder(int chunkX, int chunkZ, Component title) {
            return new Builder(chunkX, chunkZ, Objects.requireNonNull(title));
        }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public int color() { return color; }
        public Component title() { return title; }
        public @Nullable Component note() { return note; }
        public @Nullable Icons.Shape mark() { return mark; }

        public static final class Builder {
            private final int chunkX, chunkZ;
            private final Component title;
            private int color = 0xFFF7F8FB;
            private @Nullable Component note;
            private @Nullable Icons.Shape mark;

            private Builder(int chunkX, int chunkZ, Component title) {
                this.chunkX = chunkX;
                this.chunkZ = chunkZ;
                this.title = title;
            }

            public Builder color(int argb) { this.color = argb; return this; }
            public Builder note(@Nullable Component note) { this.note = note; return this; }
            public Builder mark(@Nullable Icons.Shape mark) { this.mark = mark; return this; }

            public AreaLabel build() {
                return new AreaLabel(this);
            }
        }
    }

    // ---- меню ----

    /** Место на карте, по которому открыто меню. */
    record Spot(ResourceKey<Level> dimension, int x, int y, int z, int chunkX, int chunkZ) {}

    /** Пункт меню по правой кнопке. Собирается {@link #builder(ResourceLocation, Component)}. */
    final class MenuItem {
        private final ResourceLocation id;
        private final int order;
        private final Component label;
        private final @Nullable Icons.Shape icon;
        private final Predicate<Spot> visible, enabled;
        private final Consumer<Spot> run;

        private MenuItem(Builder b) {
            this.id = b.id;
            this.order = b.order;
            this.label = b.label;
            this.icon = b.icon;
            this.visible = b.visible;
            this.enabled = b.enabled;
            this.run = Objects.requireNonNull(b.run, "Пункту меню нужно действие: run(...)");
        }

        public static Builder builder(ResourceLocation id, Component label) {
            return new Builder(Objects.requireNonNull(id), Objects.requireNonNull(label));
        }

        public ResourceLocation id() { return id; }
        public int order() { return order; }
        public Component label() { return label; }
        public @Nullable Icons.Shape icon() { return icon; }
        public boolean visible(Spot spot) { return visible.test(spot); }
        public boolean enabled(Spot spot) { return enabled.test(spot); }
        public void run(Spot spot) { run.accept(spot); }

        public static final class Builder {
            private final ResourceLocation id;
            private final Component label;
            private int order;
            private @Nullable Icons.Shape icon;
            private Predicate<Spot> visible = s -> true, enabled = s -> true;
            private @Nullable Consumer<Spot> run;

            private Builder(ResourceLocation id, Component label) {
                this.id = id;
                this.label = label;
            }

            public Builder order(int order) { this.order = order; return this; }
            public Builder icon(@Nullable Icons.Shape icon) { this.icon = icon; return this; }
            public Builder visible(Predicate<Spot> when) { this.visible = Objects.requireNonNull(when); return this; }
            /** Пункт виден, но серый, если ложь. */
            public Builder enabled(Predicate<Spot> when) { this.enabled = Objects.requireNonNull(when); return this; }
            public Builder run(Consumer<Spot> action) { this.run = Objects.requireNonNull(action); return this; }

            public MenuItem build() {
                return new MenuItem(this);
            }
        }
    }

    // ---- панель и правка чужой метки (1.12) ----

    /** Правка чужой метки из окна карты. Без права ({@link #canEdit()} ложь) окно показывает панель только для чтения. */
    interface Editor {
        boolean canEdit();

        default void rename(String name) {}

        default void move(int x, int y, int z) {}

        default void remove() {}

        /** Кто видит метку: «Только я», «Команда», «Союз» (макет {@code 15}); пустой список: выбора нет. */
        default List<Component> audiences() {
            return List.of();
        }

        default int audience() {
            return 0;
        }

        default void audience(int index) {}
    }

    /** Строка панели «ключ: значение». */
    record Row(Component key, Component value) {}

    /** Кнопка панели. Собирается {@link #builder(Component, Runnable)}. */
    final class Action {
        private final Component label;
        private final @Nullable Icons.Shape icon;
        private final boolean primary;
        private final Runnable run;

        private Action(Builder b) {
            this.label = b.label;
            this.icon = b.icon;
            this.primary = b.primary;
            this.run = b.run;
        }

        public static Builder builder(Component label, Runnable run) {
            return new Builder(Objects.requireNonNull(label), Objects.requireNonNull(run));
        }

        public Component label() { return label; }
        public @Nullable Icons.Shape icon() { return icon; }
        /** Главная кнопка: широкая, цвета акцента. */
        public boolean primary() { return primary; }
        public void run() { run.run(); }

        public static final class Builder {
            private final Component label;
            private final Runnable run;
            private @Nullable Icons.Shape icon;
            private boolean primary;

            private Builder(Component label, Runnable run) {
                this.label = label;
                this.run = run;
            }

            public Builder icon(@Nullable Icons.Shape icon) { this.icon = icon; return this; }
            public Builder primary(boolean primary) { this.primary = primary; return this; }

            public Action build() {
                return new Action(this);
            }
        }
    }

    /**
     * Правая панель 180 единиц для чужой метки (макет {@code 19}): заголовок, подзаголовок, текст, строки, врезка, до трёх
     * кнопок. Рисует карта в своём стиле. Собирается {@link #builder(Component)}.
     */
    final class Panel {
        private final Component title, subtitle;
        private final @Nullable Component text, note;
        private final List<Row> rows;
        private final List<Action> actions;

        private Panel(Builder b) {
            this.title = b.title;
            this.subtitle = b.subtitle;
            this.text = b.text;
            this.note = b.note;
            this.rows = List.copyOf(b.rows);
            this.actions = List.copyOf(b.actions);
        }

        public static Builder builder(Component title) {
            return new Builder(Objects.requireNonNull(title));
        }

        public Component title() { return title; }
        public Component subtitle() { return subtitle; }
        public @Nullable Component text() { return text; }
        public List<Row> rows() { return rows; }
        /** Врезка под строками. */
        public @Nullable Component note() { return note; }
        public List<Action> actions() { return actions; }

        public static final class Builder {
            private final Component title;
            private Component subtitle = Component.empty();
            private @Nullable Component text, note;
            private final List<Row> rows = new ArrayList<>();
            private final List<Action> actions = new ArrayList<>();

            private Builder(Component title) {
                this.title = title;
            }

            public Builder subtitle(Component subtitle) { this.subtitle = Objects.requireNonNull(subtitle); return this; }
            public Builder text(@Nullable Component text) { this.text = text; return this; }
            public Builder row(Component key, Component value) { rows.add(new Row(key, value)); return this; }
            public Builder note(@Nullable Component note) { this.note = note; return this; }
            /** До трёх кнопок: лишние не показываются. */
            public Builder action(Action action) { actions.add(Objects.requireNonNull(action)); return this; }

            public Panel build() {
                return new Panel(this);
            }
        }
    }

    // ---- поиск (1.12) ----

    /** Результат поиска: вид как у метки, заголовок, пояснение, куда вести. Собирается {@link #builder(Component, Target)}. */
    final class Hit {
        private final Component title;
        private final Component note;
        private final Target target;
        private final @Nullable Icons.Shape icon;
        private final ItemStack item;
        private final @Nullable UUID head;
        private final int color;

        private Hit(Builder b) {
            this.title = b.title;
            this.note = b.note;
            this.target = b.target;
            this.icon = b.icon;
            this.item = b.item;
            this.head = b.head;
            this.color = b.color;
        }

        public static Builder builder(Component title, Target target) {
            return new Builder(Objects.requireNonNull(title), Objects.requireNonNull(target));
        }

        public Component title() { return title; }
        public Component note() { return note; }
        public Target target() { return target; }
        public @Nullable Icons.Shape icon() { return icon; }
        public ItemStack item() { return item; }
        public @Nullable UUID head() { return head; }
        public int color() { return color; }

        public static final class Builder {
            private final Component title;
            private final Target target;
            private Component note = Component.empty();
            private @Nullable Icons.Shape icon;
            private ItemStack item = ItemStack.EMPTY;
            private @Nullable UUID head;
            private int color = 0xFFF7F8FB;

            private Builder(Component title, Target target) {
                this.title = title;
                this.target = target;
            }

            public Builder note(Component note) { this.note = Objects.requireNonNull(note); return this; }
            public Builder icon(@Nullable Icons.Shape icon) { this.icon = icon; return this; }
            public Builder item(@Nullable ItemStack item) { this.item = item == null ? ItemStack.EMPTY : item; return this; }
            public Builder head(@Nullable UUID player) { this.head = player; return this; }
            public Builder color(int argb) { this.color = argb; return this; }

            public Hit build() {
                return new Hit(this);
            }
        }
    }

    // ---- режим полотна и вкладка окна (1.12) ----

    /** Нажатие, протяжка, отпускание кнопки мыши или просто движение. */
    enum MouseAction { PRESS, DRAG, RELEASE, MOVE }

    /**
     * Режим полотна рядом с «Обзор» (макеты {@code 30}–{@code 36}): пока он включён, карта рисует тайлы и (если режим не
     * против) области слоёв, а поверх рисует и слушает мышь режим.
     */
    interface Mode {
        ResourceLocation id();

        Component name();

        Icons.Shape icon();

        default int order() {
            return 0;
        }

        /** Кнопка режима видна (например, только на сервере с RYSClaims). */
        default boolean available() {
            return true;
        }

        /** Включение режима: что он рисует и слушает, пока включён. */
        ModeView open(Surface surface);
    }

    /** Режим, пока он включён. Координаты мыши в единицах полотна (как у {@link Surface#screenX}). */
    interface ModeView {
        /** Поверх тайлов и областей, под метками и игроком. */
        void render(GuiGraphics g, Theme t, Surface s, double mouseX, double mouseY);

        /** Мышь над полотном; правда: режим забрал событие (карта не тянется, меню не открывается). */
        default boolean mouse(Surface s, double x, double y, int button, MouseAction action) {
            return false;
        }

        default boolean key(int key, int scan, int modifiers) {
            return false;
        }

        /** Подсказка под курсором вместо подсказки чанка слоёв; null: подсказки слоёв. */
        default @Nullable List<Component> tooltip(Surface s, double x, double y) {
            return null;
        }

        /** Правая панель (180 единиц), рисует сам режим; null: панели нет. */
        default @Nullable PanelView panel() {
            return null;
        }

        /** Строка сообщения внизу полотна (макет {@code 24}) или null. */
        default @Nullable Component message() {
            return null;
        }

        /** Самый мелкий чанк на экране, единиц: карта не отдаляется дальше (режим привата: 12). */
        default float minChunkSize() {
            return 0;
        }

        /** Приглушить тайлы. */
        default boolean dimTiles() {
            return false;
        }

        /** Рисовать ли области слоёв (у режима привата своя сетка и заливка). */
        default boolean drawAreas() {
            return true;
        }

        /** Режим выключен: другим режимом, закрытием окна или сменой измерения. */
        default void close() {}
    }

    /** Полотно карты для режима: перевод координат, центр, масштаб. Единицы те же, что у текущей матрицы окна. */
    interface Surface {
        ResourceKey<Level> dimension();

        /** Прямоугольник полотна. */
        int x();

        int y();

        int width();

        int height();

        double pixelsPerBlock();

        double screenX(double blockX);

        double screenY(double blockZ);

        double blockX(double screenX);

        double blockZ(double screenY);

        /** Упакованные координаты чанка под точкой полотна ({@code ChunkPos.asLong}). */
        default long chunkAt(double screenX, double screenY) {
            int cx = (int) Math.floor(blockX(screenX)) >> 4, cz = (int) Math.floor(blockZ(screenY)) >> 4;
            return (long) cx & 0xFFFFFFFFL | ((long) cz & 0xFFFFFFFFL) << 32;
        }

        void center(double blockX, double blockZ);

        /** Масштаб в единицах на блок; карта держит его в своих пределах. */
        void zoom(double pixelsPerBlock);
    }

    /** Правая панель режима. */
    interface PanelView {
        default int width() {
            return 180;
        }

        void render(GuiGraphics g, Theme t, int x, int y, int w, int h, Kit.Hits hits, double mouseX, double mouseY);

        default boolean scroll(double mouseX, double mouseY, double dy) {
            return false;
        }
    }

    /** Вкладка окна карты рядом с «Карта» (макеты {@code 40}–{@code 45}): вся область под шапкой. */
    interface Tab {
        ResourceLocation id();

        Component name();

        default int order() {
            return 0;
        }

        /** Вкладка видна (например, только на сервере с RYSClaims). */
        default boolean available() {
            return true;
        }

        /** {@code compact}: сжатая раскладка 480×270 (макеты {@code 80}–{@code 87}). */
        void render(GuiGraphics g, Theme t, int x, int y, int w, int h, boolean compact, Kit.Hits hits, double mouseX, double mouseY);

        default boolean mouse(double x, double y, int button, MouseAction action) {
            return false;
        }

        default boolean scroll(double x, double y, double dy) {
            return false;
        }

        default boolean key(int key, int scan, int modifiers) {
            return false;
        }

        default boolean charTyped(char c, int modifiers) {
            return false;
        }

        /** Что ищет поле шапки на этой вкладке. */
        default List<Hit> search(String query) {
            return List.of();
        }

        /** Подсказка в пустом поле поиска. */
        default Component searchHint() {
            return Component.empty();
        }

        default void opened() {}

        default void closed() {}
    }

    // ---- подсветка (1.12) ----

    /** Подсветка биомов и высот (макет {@code 20}). Собирается {@link #builder(ResourceLocation, Component)}. */
    final class Highlight {
        private final ResourceLocation id;
        private final Component title;
        private final Set<ResourceKey<Biome>> biomes;
        private final int minY, maxY, color;

        private Highlight(Builder b) {
            this.id = b.id;
            this.title = b.title;
            this.biomes = Set.copyOf(b.biomes);
            this.minY = b.minY;
            this.maxY = b.maxY;
            this.color = b.color;
        }

        public static Builder builder(ResourceLocation id, Component title) {
            return new Builder(Objects.requireNonNull(id), Objects.requireNonNull(title));
        }

        public ResourceLocation id() { return id; }
        public Component title() { return title; }
        /** Пусто: любые биомы. */
        public Set<ResourceKey<Biome>> biomes() { return biomes; }
        public int minY() { return minY; }
        public int maxY() { return maxY; }
        public int color() { return color; }

        public static final class Builder {
            private final ResourceLocation id;
            private final Component title;
            private final java.util.Set<ResourceKey<Biome>> biomes = new java.util.HashSet<>();
            private int minY = Integer.MIN_VALUE, maxY = Integer.MAX_VALUE, color = 0xFFE4B23C;

            private Builder(ResourceLocation id, Component title) {
                this.id = id;
                this.title = title;
            }

            public Builder biome(ResourceKey<Biome> biome) { biomes.add(Objects.requireNonNull(biome)); return this; }
            public Builder biomes(Collection<ResourceKey<Biome>> list) { biomes.addAll(list); return this; }
            /** Высоты поверхности, включительно. */
            public Builder heights(int minY, int maxY) { this.minY = minY; this.maxY = maxY; return this; }
            public Builder color(int argb) { this.color = argb; return this; }

            public Highlight build() {
                return new Highlight(this);
            }
        }
    }

    // ---- цель и строка статуса ----

    /** Место, которое карта открывает или к которому ведёт путь. Собирается {@link #builder}. */
    final class Target {
        private final ResourceKey<Level> dimension;
        private final double x, y, z;
        private final float radius;
        private final Component label;

        private Target(Builder b) {
            this.dimension = b.dimension;
            this.x = b.x;
            this.y = b.y;
            this.z = b.z;
            this.radius = b.radius;
            this.label = b.label;
        }

        public static Builder builder(ResourceKey<Level> dimension, double x, double y, double z) {
            return new Builder(Objects.requireNonNull(dimension), x, y, z);
        }

        public ResourceKey<Level> dimension() { return dimension; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        /** Путь считается пройденным ближе этого радиуса, блоки. */
        public float radius() { return radius; }
        public Component label() { return label; }

        public static final class Builder {
            private final ResourceKey<Level> dimension;
            private final double x, y, z;
            private float radius = 3;
            private Component label = Component.empty();

            private Builder(ResourceKey<Level> dimension, double x, double y, double z) {
                this.dimension = dimension;
                this.x = x;
                this.y = y;
                this.z = z;
            }

            public Builder radius(float blocks) { this.radius = Math.max(0, blocks); return this; }
            public Builder label(Component label) { this.label = Objects.requireNonNull(label); return this; }

            public Target build() {
                return new Target(this);
            }
        }
    }

    /** Строка под миникартой: цвет точки или значка, значок, текст. */
    final class Status {
        private final int color;
        private final @Nullable Icons.Shape icon;
        private final Component text;

        private Status(int color, @Nullable Icons.Shape icon, Component text) {
            this.color = color;
            this.icon = icon;
            this.text = text;
        }

        /** @param color ARGB точки или значка */
        public static Status of(int color, @Nullable Icons.Shape icon, Component text) {
            return new Status(color, icon, Objects.requireNonNull(text));
        }

        public int color() { return color; }
        public @Nullable Icons.Shape icon() { return icon; }
        public Component text() { return text; }
    }

    /** Зарегистрированная строка под миникартой, для реализации. */
    final class StatusLine {
        private final ResourceLocation id;
        private final int order;
        private final Supplier<@Nullable Status> line;

        private StatusLine(ResourceLocation id, int order, Supplier<@Nullable Status> line) {
            this.id = id;
            this.order = order;
            this.line = Objects.requireNonNull(line);
        }

        public ResourceLocation id() { return id; }
        public int order() { return order; }
        /** Строка сейчас или null. */
        public @Nullable Status get() { return line.get(); }
    }

    final class Holder {
        private static final Map<ResourceLocation, Layer> LAYERS = new LinkedHashMap<>();
        private static final Map<ResourceLocation, MenuItem> MENUS = new LinkedHashMap<>();
        private static final Map<ResourceLocation, StatusLine> STATUSES = new LinkedHashMap<>();
        private static final Map<ResourceLocation, Mode> MODES = new LinkedHashMap<>();
        private static final Map<ResourceLocation, Tab> TABS = new LinkedHashMap<>();
        private static volatile @Nullable Highlight highlight;
        private static volatile MapApi current = new MapApi() {};

        private Holder() {}

        private static <T> void put(Map<ResourceLocation, T> map, ResourceLocation id, T value) {
            synchronized (map) {
                map.put(Objects.requireNonNull(id), value);
            }
        }

        private static <T> List<T> values(Map<ResourceLocation, T> map) {
            synchronized (map) {
                return new ArrayList<>(map.values());
            }
        }
    }
}
