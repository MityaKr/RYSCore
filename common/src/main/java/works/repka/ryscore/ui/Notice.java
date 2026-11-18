package works.repka.ryscore.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Одно уведомление семьи (1.9.2): что мод отдаёт в {@link Notices}. Ядро само ставит его в колонку тостов игры справа вверху,
 * рисует по теме, считает время, ставит на паузу при наведении и играет звук. Собирается строителем:
 *
 * <pre>{@code
 * Notice.card(Notice.QUESTS, "done:" + id).item(icon).check().title(name)
 *     .lines("Награда в книге: 25 опыта", "В книге: 25 опыта", "Награда ждёт в книге")
 *     .key(() -> "K").action(() -> openBook(id)).sound(Notice.Sound.QUEST).summary(n -> ..., "Награды ждут в книге").show();
 * }</pre>
 *
 * Источник и id вместе — ключ: новое уведомление с тем же ключом заменяет видимое на месте и заново заводит его время.
 */
public final class Notice {
    /** Источники семьи: от них зависят плашка строки ({@code src.<источник>.bg}, {@code .fg}) и предел мест. */
    public static final String QUESTS = "quests", TEAMS = "teams", SCOPE = "scope", PULSE = "pulse";
    /** Источники карты и привата (1.11). */
    public static final String MAP = "map", CLAIMS = "claims";
    /** Источник окна «Сервер» (RYSHelm) и самого ядра: колесо, свои клавиши (1.14). */
    public static final String HELM = "helm", CORE = "core";

    /** Вид карточки: обычная (квест), крупная (веха, глава, эпоха), строка (подтверждение), с кнопками (приглашение). */
    public enum Kind { CARD, BIG, ROW, BUTTONS, SUMMARY }

    /** Ранг крупной карточки: цвет линии сверху, ярлыка, рамки значка и галочки. */
    public enum Rank { NORMAL, GOLD, GREEN }

    /** Звуки из набора ядра. Громкость общая, по умолчанию обычная; выключает их одна настройка семьи. */
    public enum Sound { NONE, QUEST, MILESTONE, CHAPTER, EPOCH, INVITE }

    /** Плашка награды крупной карточки. */
    public record Chip(ChipKind kind, ItemStack item, String text) {
        public static Chip xp(String text) {
            return new Chip(ChipKind.XP, ItemStack.EMPTY, text);
        }

        public static Chip coins(String text) {
            return new Chip(ChipKind.COIN, ItemStack.EMPTY, text);
        }

        /** Предмет; {@code text} — «×N» или пусто. */
        public static Chip item(ItemStack item, String text) {
            return new Chip(ChipKind.ITEM, item, text);
        }

        public static Chip random() {
            return new Chip(ChipKind.RANDOM, ItemStack.EMPTY, "");
        }

        /** Награда на выбор: «1 из 4». */
        public static Chip choice(String text) {
            return new Chip(ChipKind.CHOICE, ItemStack.EMPTY, text);
        }
    }

    public enum ChipKind { XP, COIN, ITEM, RANDOM, CHOICE }

    /** Кнопка карточки с кнопками: подпись (с клавишей, если она есть), главная ли, действие. */
    public record Button(String label, boolean primary, Runnable action) {}

    final String source, id;
    final Kind kind;
    ItemStack item = ItemStack.EMPTY;
    @Nullable Icons.Shape shape;
    @Nullable UUID head;
    boolean check;
    @Nullable UUID markHead;
    Rank rank = Rank.NORMAL;
    String label = "", title = "", note = "";
    float titlePx = 14;
    List<String> lines = List.of();
    boolean lineBright, bad;
    final List<Chip> chips = new ArrayList<>();
    @Nullable Supplier<String> key;
    @Nullable Runnable action;
    final List<Button> buttons = new ArrayList<>();
    @Nullable Button accept, decline;
    @Nullable DoubleSupplier progress;
    @Nullable Supplier<String> timer;
    long millis;
    @Nullable BooleanSupplier alive, hidden;
    Sound sound = Sound.NONE;
    @Nullable IntFunction<String> summaryTitle;
    String summaryLine = "";

    private Notice(String source, String id, Kind kind) {
        this.source = source;
        this.id = id;
        this.kind = kind;
        this.millis = switch (kind) {
            case CARD -> 4500;
            case BIG -> 8000;
            case ROW -> 2000;
            case BUTTONS -> 60_000;
            case SUMMARY -> 6000;
        };
    }

    /** Обычная карточка: значок, название в одну-две строки, строка о награде. 4,5 с. */
    public static Notice card(String source, String id) {
        return new Notice(source, id, Kind.CARD);
    }

    /** Крупная карточка: линия ранга сверху, ярлык, название, строка, плашки наград или строка под шапкой. 8 с. */
    public static Notice big(String source, String id) {
        return new Notice(source, id, Kind.BIG);
    }

    /** Строка: значок на плашке источника и короткий текст. 2 с, без звука, без клика, только крестик. */
    public static Notice row(String source, String id) {
        return new Notice(source, id, Kind.ROW);
    }

    /** Карточка с кнопками (приглашение): живёт, пока {@link #alive} отвечает да. */
    public static Notice buttons(String source, String id) {
        return new Notice(source, id, Kind.BUTTONS);
    }

    static Notice summary(String source, String id) {
        return new Notice(source, id, Kind.SUMMARY);
    }

    public String source() {
        return source;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    /** Значок — предмет. */
    public Notice item(ItemStack stack) {
        this.item = stack == null ? ItemStack.EMPTY : stack;
        return this;
    }

    /** Значок — контур из {@link Icons}. */
    public Notice icon(Icons.Shape shape) {
        this.shape = shape;
        return this;
    }

    /** Значок — голова игрока. */
    public Notice head(UUID player) {
        this.head = player;
        return this;
    }

    /** Галочка на углу значка цветом ранга (обычный ранг — зелёная). */
    public Notice check() {
        this.check = true;
        return this;
    }

    /** Голова игрока на углу значка вместо галочки: квест выполнил товарищ. */
    public Notice markHead(UUID player) {
        this.markHead = player;
        return this;
    }

    public Notice rank(Rank rank) {
        this.rank = rank;
        return this;
    }

    /** Ярлык над названием крупной карточки, рисуется прописными. */
    public Notice label(String text) {
        this.label = text;
        return this;
    }

    public Notice title(String text) {
        this.title = text;
        return this;
    }

    /** Кегль названия крупной карточки в пикселях макета: веха 14, глава 15, эпоха 16. */
    public Notice titleSize(float px) {
        this.titlePx = px;
        return this;
    }

    /** Формулировки строки от длинной к короткой: ядро берёт первую, которая помещается, и не режет её многоточием без нужды. */
    public Notice lines(String... variants) {
        this.lines = List.of(variants);
        return this;
    }

    /** Строка светлее обычной ({@code text.secondary}): «Получено». */
    public Notice bright() {
        this.lineBright = true;
        return this;
    }

    /** Ошибка: текст строки красится {@code state.bad}. */
    public Notice bad() {
        this.bad = true;
        return this;
    }

    /** Вторая строка крупной карточки вместо плашек: «Дальше: Нефть и пластик». */
    public Notice note(String text) {
        this.note = text;
        return this;
    }

    /** Плашка награды крупной карточки. Видно не больше четырёх, остальное «+N». */
    public Notice chip(Chip chip) {
        this.chips.add(chip);
        return this;
    }

    /** Клавиша на карточке («K»): показывается, только если задано действие. */
    public Notice key(Supplier<String> label) {
        this.key = label;
        return this;
    }

    /** Что делают клик по карточке и клавиша мода ({@link Notices#press}). */
    public Notice action(Runnable action) {
        this.action = action;
        return this;
    }

    public Notice button(String label, boolean primary, Runnable action) {
        this.buttons.add(new Button(label, primary, action));
        return this;
    }

    /**
     * Кнопка «Принять» (1.14): главная, с клавишей ядра справа от подписи ({@code Answers.ACCEPT}, умолчание Y).
     * Клавиша отвечает на верхнее видимое уведомление с такой кнопкой, у любого мода.
     */
    public Notice accept(String label, Runnable action) {
        Button b = new Button(label, true, action);
        this.buttons.add(b);
        this.accept = b;
        return this;
    }

    /** Кнопка «Отказать» (1.14): обычная, с клавишей ядра ({@code Answers.DECLINE}, умолчание N). */
    public Notice decline(String label, Runnable action) {
        Button b = new Button(label, false, action);
        this.buttons.add(b);
        this.decline = b;
        return this;
    }

    /**
     * Полоса под названием обычной карточки (1.14): разогрев телепорта «Домой через 3 с», доля 0–1. Строка
     * ({@link #lines}) встаёт под полосой: «урон отменит». Название тогда в одну строку.
     */
    public Notice progress(DoubleSupplier part) {
        this.progress = part;
        return this;
    }

    /** Таймер справа в строке кнопок («0:42»). */
    public Notice timer(Supplier<String> text) {
        this.timer = text;
        return this;
    }

    /** Время жизни без наведения. */
    public Notice millis(long millis) {
        this.millis = millis;
        return this;
    }

    /** Пока отвечает да, карточка живёт (сверх {@link #millis}); нет — уходит. */
    public Notice alive(BooleanSupplier alive) {
        this.alive = alive;
        return this;
    }

    /** Когда отвечает да, карточка уходит: над открытой книгой уведомления квестов не нужны. */
    public Notice hiddenWhen(BooleanSupplier hidden) {
        this.hidden = hidden;
        return this;
    }

    public Notice sound(Sound sound) {
        this.sound = sound;
        return this;
    }

    /**
     * Карточку можно сложить в сводку: третья и следующие карточки источника, пришедшие, пока видны две, складываются
     * в одну «Выполнено ещё N квестов». {@code title} получает число сложенных.
     */
    public Notice summary(IntFunction<String> title, String line) {
        this.summaryTitle = title;
        this.summaryLine = line;
        return this;
    }

    /** Отдаёт уведомление ядру. Вызывать в потоке клиента. */
    public void show() {
        Notices.show(this);
    }
}
