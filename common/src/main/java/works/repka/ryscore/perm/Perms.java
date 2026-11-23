package works.repka.ryscore.perm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Узлы прав сервера для модов семьи (1.14, план Essentials 5.1). Мод объявляет право в конструкторе мода
 * (до запуска сервера), не зная о RYSHelm:
 *
 * <pre>{@code
 * static final Perms.Node MANAGE = Perms.node("rysmap", "place.manage", Roles.MODERATOR, Component.translatable("rysmap.perm.place.manage"));
 * if (MANAGE.test(player)) ...
 * }</pre>
 *
 * <p>Узел становится узлом NeoForge {@code PermissionAPI} с именем {@code rysmap.place.manage}: если на сервере стоит
 * LuckPerms, решает он. Умолчание узла (когда обработчик прав его не знает или это обработчик NeoForge по умолчанию)
 * даёт таблица ролей RYSHelm ({@link #policy}), а без него роль по умолчанию узла ({@link Roles}).
 * Узел, объявленный после запуска сервера, обработчику прав не известен и решается умолчанием.
 */
public final class Perms {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*");
    private static final Map<String, Node> NODES = Collections.synchronizedMap(new LinkedHashMap<>());

    /** Таблица ролей (RYSHelm): решение по узлу или null, тогда решает роль по умолчанию узла. */
    public interface Policy {
        @Nullable
        Boolean decide(MinecraftServer server, UUID player, Node node);
    }

    /** Мост к обработчику прав загрузчика (PermissionAPI NeoForge). Ставит ядро. */
    public interface Checker {
        /** Решение обработчика прав или null, если узел ему не известен. */
        @Nullable
        Boolean check(@Nullable ServerPlayer player, UUID id, Node node);
    }

    private static volatile @Nullable Policy policy;
    private static volatile @Nullable Checker checker;

    private Perms() {}

    /** Право мода. Повторное объявление того же имени возвращает уже объявленный узел. */
    public static Node node(String modid, String name, String defaultRole) {
        return node(modid, name, defaultRole, null);
    }

    /**
     * Право мода с подписью для таблицы ролей.
     * @param name        часть имени после modid, строчные латинские, цифры, {@code _} и точки: {@code place.manage}
     * @param defaultRole {@link Roles#OPERATOR}, {@link Roles#MODERATOR} или {@link Roles#PLAYER}
     */
    public static Node node(String modid, String name, String defaultRole, @Nullable Component label) {
        String id = modid + "." + name;
        if (!NAME.matcher(id).matches()) throw new IllegalArgumentException("Permission name: lowercase latin letters, digits, _ and dots: " + id);
        return NODES.computeIfAbsent(id, k -> new Node(modid, name, defaultRole, label));
    }

    /** Объявленные узлы по порядку объявления. */
    public static List<Node> nodes() {
        synchronized (NODES) {
            return new ArrayList<>(NODES.values());
        }
    }

    public static @Nullable Node get(String id) {
        return NODES.get(id);
    }

    /** Проверка по полному имени; необъявленное имя не разрешено никому, кроме оператора. */
    public static boolean test(ServerPlayer player, String id) {
        Node n = get(id);
        return n != null ? n.test(player) : Roles.atLeast(player, Roles.OPERATOR);
    }

    /** Ставит RYSHelm: таблица ролей решает умолчание узлов. */
    public static void policy(@Nullable Policy p) {
        policy = p;
    }

    /** Ставит ядро (мост к PermissionAPI). */
    public static void checker(@Nullable Checker c) {
        checker = c;
    }

    /**
     * Умолчание узла: таблица ролей RYSHelm, иначе роль узла. Этим же отвечает узел NeoForge, когда обработчик прав
     * спрашивает умолчание.
     */
    public static boolean resolveDefault(MinecraftServer server, UUID player, Node node) {
        Policy p = policy;
        if (p != null) {
            try {
                Boolean d = p.decide(server, player, node);
                if (d != null) return d;
            } catch (RuntimeException ignored) {
                // таблица упала: решает роль узла
            }
        }
        return Roles.atLeast(server, player, node.defaultRole());
    }

    /** Право сервера. Не запись: в 1.x можно добавлять поля. */
    public static final class Node {
        private final String modid, name, defaultRole;
        private final @Nullable Component label;

        private Node(String modid, String name, String defaultRole, @Nullable Component label) {
            this.modid = modid;
            this.name = name;
            this.defaultRole = defaultRole;
            this.label = label;
        }

        /** Полное имя узла: {@code rysmap.place.manage}. */
        public String id() {
            return modid + "." + name;
        }

        public String modid() {
            return modid;
        }

        public String name() {
            return name;
        }

        public String defaultRole() {
            return defaultRole;
        }

        /** Подпись строки в таблице ролей; без неё полное имя. */
        public Component label() {
            return label != null ? label : Component.literal(id());
        }

        public boolean test(ServerPlayer player) {
            Checker c = checker;
            if (c != null) {
                Boolean b = c.check(player, player.getUUID(), this);
                if (b != null) return b;
            }
            return resolveDefault(player.server, player.getUUID(), this);
        }

        /** Проверка игрока вне сети. */
        public boolean test(MinecraftServer server, UUID player) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null) return test(online);
            Checker c = checker;
            if (c != null) {
                Boolean b = c.check(null, player, this);
                if (b != null) return b;
            }
            return resolveDefault(server, player, this);
        }

        @Override
        public String toString() {
            return id() + " (" + defaultRole + ")";
        }
    }
}
