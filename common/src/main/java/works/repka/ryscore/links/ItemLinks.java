package works.repka.ryscore.links;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Сведения о предмете от модов семьи (1.10): мод рассказывает просмотрщику, где предмет встречается у него
 * (книга квестов: сделки магазина, награды, задачи). Ядро не знает слов «квест» и «магазин»: поставщик отдаёт
 * готовые карточки и упоминания, просмотрщик (RYSIndex) рисует их своим видом.
 *
 * <p>Правила контракта:
 * <ul>
 *   <li>Все методы поставщика зовутся из главного потока клиента и отвечают сразу, из готового индекса. Пока
 *       индекс строится, поставщик отдаёт пусто и поднимает {@link Provider#version()}, когда готов.</li>
 *   <li>Поставщик сам решает, что игроку можно видеть (скрытое, черновики, закрытые стадии он не отдаёт).
 *       Потребитель ничего не фильтрует.</li>
 *   <li>Тексты поставщик отдаёт готовыми {@link Component} на языке игрока, без цветов: потребитель только рисует.</li>
 *   <li>Ошибка поставщика ловится потребителем и пишется в лог один раз.</li>
 *   <li>Без поставщиков (мод не установлен) потребитель ведёт себя так, будто сведений нет.</li>
 * </ul>
 */
public final class ItemLinks {
    private static final List<Provider> PROVIDERS = new ArrayList<>();
    /** Растёт при каждой регистрации и снятии: список поставщиков тоже часть ответа. */
    private static int registrations;

    private ItemLinks() {}

    /** Мод зовёт при запуске клиента. Поставщик с тем же id заменяет прежнего. */
    public static synchronized void register(Provider provider) {
        Objects.requireNonNull(provider, "provider");
        ResourceLocation id = provider.id();
        PROVIDERS.removeIf(p -> p.id().equals(id));
        PROVIDERS.add(provider);
        registrations++;
    }

    /** Снять поставщика (автотесты, выгрузка). @return true, если он был */
    public static synchronized boolean unregister(ResourceLocation id) {
        boolean removed = PROVIDERS.removeIf(p -> p.id().equals(id));
        if (removed) registrations++;
        return removed;
    }

    /** Поставщики в порядке регистрации. */
    public static synchronized List<Provider> providers() {
        return List.copyOf(PROVIDERS);
    }

    /**
     * Общий номер версии: меняется, когда любой поставщик поднял свою версию или список поставщиков изменился.
     * Открытое окно сверяет его раз в кадр и перечитывает сведения только при изменении.
     */
    public static synchronized int version() {
        int sum = registrations * 31;
        for (Provider p : PROVIDERS) {
            try {
                sum += p.version();
            } catch (Throwable ignored) {
                // сломанный поставщик не мешает остальным; ошибку покажет потребитель при запросе сведений
            }
        }
        return sum;
    }

    /** Мод семьи, который знает что-то о предметах. Методы зовутся из главного потока клиента. */
    public interface Provider {
        /** Например {@code rysquests:book}. */
        ResourceLocation id();

        /** Растёт, когда ответы могли измениться: новая книга, прогресс, состояние магазина. */
        int version();

        /**
         * Карточки для категорий просмотрщика. {@link Role#GIVES}: как получить предмет (вкладка «Источники»),
         * {@link Role#TAKES}: где он тратится (вкладка «Использования»).
         */
        default List<Card> cards(ItemStack stack, Role role) {
            return List.of();
        }

        /** Короткие упоминания для чипа в шапке карточки предмета. Null или пустой список: чипа нет. */
        @Nullable
        default Mentions mentions(ItemStack stack) {
            return null;
        }

        /**
         * Счётчик категории словами поставщика, например «3 сделки». Null: потребитель пишет своё («3 рецепта»).
         */
        @Nullable
        default Component count(Group group, int count) {
            return null;
        }
    }

    public enum Role { GIVES, TAKES }

    /** Состояние квеста или сделки для игрока: сделан, в работе, открыт, закрыт. */
    public enum State { DONE, ACTIVE, OPEN, LOCKED }

    /**
     * Категория просмотрщика: вкладка в рейке. {@code order} сортирует категории одного поставщика между собой.
     * Значок ставится и в строку категории.
     */
    public record Group(ResourceLocation id, Component title, ItemStack icon, int order) {}

    /** Действие поставщика: открыть книгу на квесте, магазин на сделке. Подпись нужна кнопкам и ссылкам. */
    public record Action(Component label, Runnable run) {}

    /**
     * Строка карточки: предмет с количеством или валюта.
     *
     * @param stack предмет; у валюты пустой
     * @param coin  валюта: строка с монетой, без слота
     * @param label подпись; у предмета null значит его название, у валюты готовая строка («1 000 монет»)
     * @param detail приглушённое пояснение после подписи («при получении товара») или null
     * @param amount сколько нужно или дают
     * @param have  сколько уже есть или доставлено; меньше нуля: не показывать полосу и «есть / нужно»
     */
    public record Part(ItemStack stack, boolean coin, @Nullable Component label, @Nullable Component detail, long amount, long have) {
        public static Part item(ItemStack stack, long amount) {
            return new Part(stack, false, null, null, amount, -1);
        }

        public static Part item(ItemStack stack, long amount, long have) {
            return new Part(stack, false, null, null, amount, have);
        }

        public static Part coins(Component label, @Nullable Component detail, long amount) {
            return new Part(ItemStack.EMPTY, true, label, detail, amount, -1);
        }
    }

    /**
     * Карточка, которую просмотрщик рисует сам, своим видом. Сверху значок, название и подзаголовок; ниже блоки
     * «что дают» и «что нужно», строка состояния, врезка замка со ссылкой и кнопка действия.
     *
     * <p>Значок: {@link Builder#icon}; если он пуст, им становится первый предмет {@code gives}, рядом с названием
     * пишется его количество («×1»), а в блоке «что дают» этот предмет не повторяется. Блоки рисуются, только если
     * у них есть заголовок и строки.
     */
    public static final class Card {
        private final Group group;
        private final ResourceLocation id;
        private final ItemStack icon;
        private final Component title;
        @Nullable
        private final Component subtitle;
        private final State state;
        @Nullable
        private final Component givesTitle;
        private final List<Part> gives;
        @Nullable
        private final Component takesTitle;
        private final List<Part> takes;
        @Nullable
        private final Component note;
        @Nullable
        private final Component lock;
        @Nullable
        private final Action lockLink;
        @Nullable
        private final Action open;

        private Card(Builder b) {
            this.group = b.group;
            this.id = b.id;
            this.icon = b.icon;
            this.title = b.title;
            this.subtitle = b.subtitle;
            this.state = b.state;
            this.givesTitle = b.givesTitle;
            this.gives = List.copyOf(b.gives);
            this.takesTitle = b.takesTitle;
            this.takes = List.copyOf(b.takes);
            this.note = b.note;
            this.lock = b.lock;
            this.lockLink = b.lockLink;
            this.open = b.open;
        }

        public static Builder builder(Group group, ResourceLocation id, Component title) {
            return new Builder(group, id, title);
        }

        public Group group() {
            return group;
        }

        /** Сделка или квест: {@code rysquests:shop/trophy_gate_fragment}. */
        public ResourceLocation id() {
            return id;
        }

        /** Свой значок; пустой значит первый предмет {@link #gives()}. */
        public ItemStack icon() {
            return icon;
        }

        public Component title() {
            return title;
        }

        /** «закрыто», «поставка команды · 45 %», глава квеста. */
        @Nullable
        public Component subtitle() {
            return subtitle;
        }

        public State state() {
            return state;
        }

        /** Заголовок блока «что дают» («Награда») или null. */
        @Nullable
        public Component givesTitle() {
            return givesTitle;
        }

        public List<Part> gives() {
            return gives;
        }

        /** Заголовок блока «что нужно» («Нужно доставить», «Цена») или null. */
        @Nullable
        public Component takesTitle() {
            return takesTitle;
        }

        public List<Part> takes() {
            return takes;
        }

        /** Строка состояния с точкой цвета состояния: «Команда доставила 1 из 3 позиций», «осталось 2 из 5 сегодня». */
        @Nullable
        public Component note() {
            return note;
        }

        /** Почему закрыто, словами мода: врезка с замком. */
        @Nullable
        public Component lock() {
            return lock;
        }

        /** Ссылка под замком: квест, который открывает сделку. */
        @Nullable
        public Action lockLink() {
            return lockLink;
        }

        /** Главное действие карточки: «Открыть в магазине», «Открыть в книге». Null: кнопки нет. */
        @Nullable
        public Action open() {
            return open;
        }

        public static final class Builder {
            private final Group group;
            private final ResourceLocation id;
            private final Component title;
            private ItemStack icon = ItemStack.EMPTY;
            @Nullable
            private Component subtitle;
            private State state = State.OPEN;
            @Nullable
            private Component givesTitle;
            private final List<Part> gives = new ArrayList<>();
            @Nullable
            private Component takesTitle;
            private final List<Part> takes = new ArrayList<>();
            @Nullable
            private Component note;
            @Nullable
            private Component lock;
            @Nullable
            private Action lockLink;
            @Nullable
            private Action open;

            private Builder(Group group, ResourceLocation id, Component title) {
                this.group = Objects.requireNonNull(group, "group");
                this.id = Objects.requireNonNull(id, "id");
                this.title = Objects.requireNonNull(title, "title");
            }

            public Builder icon(ItemStack icon) {
                this.icon = icon == null ? ItemStack.EMPTY : icon;
                return this;
            }

            public Builder subtitle(@Nullable Component subtitle) {
                this.subtitle = subtitle;
                return this;
            }

            public Builder state(State state) {
                this.state = Objects.requireNonNull(state, "state");
                return this;
            }

            public Builder gives(@Nullable Component title, List<Part> parts) {
                this.givesTitle = title;
                this.gives.addAll(parts);
                return this;
            }

            public Builder give(Part part) {
                this.gives.add(part);
                return this;
            }

            public Builder takes(@Nullable Component title, List<Part> parts) {
                this.takesTitle = title;
                this.takes.addAll(parts);
                return this;
            }

            public Builder take(Part part) {
                this.takes.add(part);
                return this;
            }

            public Builder note(@Nullable Component note) {
                this.note = note;
                return this;
            }

            public Builder lock(@Nullable Component lock, @Nullable Action link) {
                this.lock = lock;
                this.lockLink = link;
                return this;
            }

            public Builder open(@Nullable Action open) {
                this.open = open;
                return this;
            }

            public Card build() {
                return new Card(this);
            }
        }
    }

    /**
     * Короткое упоминание для списка у чипа: строка со значком, названием и пояснением, состояние справа.
     *
     * @param section группа списка («Нужен в задачах», «Даётся в награду», «Магазин»); строки одной группы идут подряд
     * @param icon    значок квеста или сделки; это картинка, а не предмет просмотрщика. Пустой: значок книги
     * @param detail  глава и эпоха, «Поставка команды · 45 %» или null
     * @param open    что делает клик по строке; null: строка не нажимается
     */
    public record Mention(Component section, ItemStack icon, Component title, @Nullable Component detail, State state, @Nullable Action open) {}

    /**
     * Упоминания предмета от одного поставщика.
     *
     * @param label надпись чипа: «2 квеста», «награда квеста»
     * @param title заголовок раскрытого списка: «В книге квестов»
     */
    public record Mentions(Component label, Component title, List<Mention> items) {
        public Mentions {
            items = List.copyOf(items);
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }
}
