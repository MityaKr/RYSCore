package works.repka.ryscore.team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.RYSCore;

/**
 * Команды для модов семьи. Вызывается на сервере. Пока RYSTeams не установлен, каждый игрок играет соло:
 * его команда имеет тот же UUID, что и он сам, поэтому код модов не ветвится.
 */
public interface TeamsApi {
    /** Запись ленты команды: источник (например {@code rysquests:quests}), текст и значок-предмет. */
    record Post(ResourceLocation source, Component text, @Nullable ResourceLocation icon, boolean alert) {}

    /** Строка предпросмотра слияния от одного мода. {@code question}: нужно внимание лидера. */
    record MergeLine(ResourceLocation source, Component text, boolean question) {}

    /** Что происходит с командами. Моды переносят здесь свои данные. */
    interface Listener {
        /** Игрок вступил. {@code carryProgress}: перенести его личный прогресс в команду. */
        default void onJoined(UUID team, UUID player, boolean carryProgress) {}

        /** Игрок ушёл. {@code keepCopy}: оставить ему копию прогресса команды. */
        default void onLeft(UUID team, UUID player, boolean keepCopy) {}

        default void onDisbanded(UUID team) {}

        /** Что произойдёт при слиянии: показывается лидерам до подтверждения. */
        default List<MergeLine> previewMerge(UUID from, UUID into) {
            return List.of();
        }

        /** Слияние выполнено, {@code from} больше нет. */
        default void onMerged(UUID from, UUID into) {}

        /** Слияние отменено в течение 30 секунд: вернуть то, что сделал {@link #onMerged}. */
        default void onMergeUndone(UUID from, UUID into) {}

        /** Роль игрока в команде изменилась (повышение, понижение, передача лидерства). С 1.11. */
        default void onRoleChanged(UUID team, UUID player) {}

        /** Список союзников команды изменился: союз заключён или расторгнут. С 1.11. */
        default void onAlliesChanged(UUID team) {}

        /** Имя, цвет или значок команды изменились: подписи и палитры перечитывают {@link #name} и {@link #color}. С 1.11. */
        default void onRenamed(UUID team) {}
    }

    /** Роли команды от старшей к младшей: значения {@link #roleOf} и {@link #registerPermission}. С 1.11. */
    String LEADER = "leader", OFFICER = "officer", MEMBER = "member", GUEST = "guest";

    /**
     * Право чужого мода в таблице прав команды (1.11): строка на экране команды, проверка через {@link #can}.
     * Не запись: в 1.x к ней можно добавлять поля.
     */
    final class ModPermission {
        private final String id;
        private final Component label;
        private final Set<String> defaultRoles;

        private ModPermission(String id, Component label, Set<String> defaultRoles) {
            this.id = id;
            this.label = label;
            this.defaultRoles = Set.copyOf(defaultRoles);
        }

        /** Полный id с пространством мода, например {@code rysclaims:claim}. */
        public String id() {
            return id;
        }

        /** Подпись строки в таблице прав. */
        public Component label() {
            return label;
        }

        /** Роли, которым право дано, пока лидер не менял таблицу: {@link #LEADER}, {@link #OFFICER}, {@link #MEMBER}, {@link #GUEST}. */
        public Set<String> defaultRoles() {
            return defaultRoles;
        }
    }

    /** Команда игрока. Соло: UUID самого игрока. */
    UUID teamOf(UUID player);

    /** Участники команды. Для соло только сам игрок. */
    Collection<UUID> members(UUID team);

    /** Может ли игрок выполнить действие, например {@code claim_rewards}. Соло может всё. */
    boolean can(UUID player, String permission);

    void post(UUID team, Post post);

    /** Данные мода на команду. Null, если записи нет. Без RYSTeams хранилища нет. */
    @Nullable
    default net.minecraft.nbt.CompoundTag getData(UUID team, ResourceLocation key) {
        return null;
    }

    default void putData(UUID team, ResourceLocation key, net.minecraft.nbt.CompoundTag value) {}

    /** Чем занят игрок: показывается участникам его команды, пустая строка убирает. */
    default void setStatus(UUID player, String status) {}

    /** Дело на доске команды от мода, например «принести медь ×64». */
    default void addTodo(UUID team, ResourceLocation source, String text) {}

    /** Пинг команде в мировых координатах: сюда, опасно, ресурс, помогите. */
    default void ping(UUID team, String dimension, int x, int y, int z, String kind, String by) {}

    default boolean isSolo(UUID team) {
        return members(team).size() <= 1;
    }

    /**
     * Имя команды для игрока (1.11): блоки магазина, подписи участков. Соло: ник игрока (в сети или из кэша профилей),
     * без него первые восемь знаков UUID.
     */
    default Component name(UUID team) {
        return Component.literal(Holder.playerName(team));
    }

    /** Цвет команды, RGB без альфы (1.11). Соло: один из двенадцати цветов семьи по UUID, у игрока всегда один и тот же. */
    default int color(UUID team) {
        return Holder.COLORS[Math.floorMod(team.hashCode(), Holder.COLORS.length)];
    }

    /** Роль игрока в его команде (1.11): {@link #LEADER}, {@link #OFFICER}, {@link #MEMBER}, {@link #GUEST}. Соло: лидер. */
    default String roleOf(UUID player) {
        return LEADER;
    }

    /** Команды-союзники (1.11). Союз бывает только между командами. Соло: никого. */
    default Collection<UUID> allies(UUID team) {
        return List.of();
    }

    /** Лидер команды (1.11), null, если команды нет. Соло: сам игрок. */
    default @Nullable UUID leader(UUID team) {
        return team;
    }

    /** Все команды мира (1.11): сверка переезда, «команда без пары». Без RYSTeams пусто: соло-команды не перечисляются. */
    default Collection<UUID> teams() {
        return List.of();
    }

    /**
     * Регистрирует право чужого мода в таблице прав команды (1.11). Звать в конструкторе мода на обеих сторонах: сервер
     * проверяет право через {@link #can}, клиент берёт отсюда подпись строки на экране команды. Тот же id заменяет прежнее.
     * Реестр живёт в ядре, поэтому порядок загрузки модов не важен. Без RYSTeams право есть у всех.
     *
     * @param id полный id с пространством мода, например {@code rysclaims:claim}
     */
    default void registerPermission(String id, Component label, Set<String> defaultRoles) {
        Holder.register(id, label, defaultRoles);
    }

    /** Права модов в порядке регистрации, для реализации и экрана команды (1.11). */
    static List<ModPermission> permissions() {
        synchronized (Holder.PERMISSIONS) {
            return List.copyOf(Holder.PERMISSIONS.values());
        }
    }

    /** Право мода по id или null (1.11). */
    static @Nullable ModPermission permission(String id) {
        synchronized (Holder.PERMISSIONS) {
            return Holder.PERMISSIONS.get(id);
        }
    }

    /** Текущая реализация: соло или установленный RYSTeams. */
    static TeamsApi get() {
        return Holder.current;
    }

    /** Вызывает RYSTeams при запуске. */
    static void provide(TeamsApi implementation) {
        Holder.current = implementation;
    }

    static void listen(Listener listener) {
        Holder.LISTENERS.add(listener);
    }

    /** Подписчики, для реализации API. */
    static List<Listener> listeners() {
        return List.copyOf(Holder.LISTENERS);
    }

    final class Holder {
        private static final List<Listener> LISTENERS = new ArrayList<>();
        private static final Map<String, ModPermission> PERMISSIONS = new LinkedHashMap<>();
        /** Двенадцать цветов команды, различимых по яркости: те же, что у RYSTeams. */
        private static final int[] COLORS = {0x2E8B57, 0x3A7BD5, 0x7A4FD1, 0xC0392B, 0xD47C3E, 0xE4B23C,
            0x5BE3E0, 0x8B93A5, 0xE86FA8, 0x1FA8A0, 0x7F8F3A, 0x4B4FB5};
        private static volatile TeamsApi current = new TeamsApi() {
            @Override
            public UUID teamOf(UUID player) {
                return player;
            }

            @Override
            public Collection<UUID> members(UUID team) {
                return List.of(team);
            }

            @Override
            public boolean can(UUID player, String permission) {
                return true;
            }

            @Override
            public void post(UUID team, Post post) {
                // без RYSTeams ленты нет
            }
        };

        private Holder() {}

        private static void register(String id, Component label, Set<String> defaultRoles) {
            Objects.requireNonNull(id, "id");
            if (id.indexOf(':') <= 0) throw new IllegalArgumentException("A mod permission needs a namespace: " + id);
            synchronized (PERMISSIONS) {
                PERMISSIONS.put(id, new ModPermission(id, Objects.requireNonNull(label, "label"), defaultRoles));
            }
        }

        private static String playerName(UUID id) {
            MinecraftServer server = RYSCore.server();
            if (server != null) {
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online != null) return online.getGameProfile().getName();
                var cache = server.getProfileCache();
                if (cache != null) {
                    var profile = cache.get(id);
                    if (profile.isPresent()) return profile.get().getName();
                }
            }
            return id.toString().substring(0, 8);
        }
    }
}
