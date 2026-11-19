package works.repka.ryscore.perm;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Роли сервера для модов семьи (1.14, план Essentials 5.1): оператор, модератор, игрок. Кто модератор, решает RYSHelm
 * ({@link #provide}); без него оператор игры (уровень 2 и выше) это оператор, остальные игроки, модераторов нет.
 * Это роли сервера, а не роли команды ({@code TeamsApi.LEADER} и другие).
 */
public final class Roles {
    public static final String OPERATOR = "operator", MODERATOR = "moderator", PLAYER = "player";
    /** Уровень оператора игры, с которого игрок считается оператором. */
    public static final int OP_LEVEL = 2;

    /** Кто есть кто на сервере: ставит RYSHelm. Null — не знаю, решает уровень оператора игры. */
    public interface Provider {
        @Nullable
        String roleOf(MinecraftServer server, UUID player);
    }

    private static volatile @Nullable Provider provider;
    private static volatile @Nullable String clientRole;
    private static volatile boolean moderationActive;

    private Roles() {}

    /** Ставит RYSHelm при запуске. */
    public static void provide(@Nullable Provider p) {
        provider = p;
    }

    /** Старшинство роли: оператор 2, модератор 1, игрок и неизвестная 0. */
    public static int rank(@Nullable String role) {
        if (OPERATOR.equals(role)) return 2;
        if (MODERATOR.equals(role)) return 1;
        return 0;
    }

    public static String of(ServerPlayer player) {
        return of(player.server, player.getUUID(), player.getGameProfile());
    }

    /** Роль игрока, в том числе вне сети. */
    public static String of(MinecraftServer server, UUID player) {
        return of(server, player, null);
    }

    private static String of(MinecraftServer server, UUID id, @Nullable GameProfile profile) {
        Provider p = provider;
        if (p != null) {
            try {
                String role = p.roleOf(server, id);
                if (role != null) return role;
            } catch (RuntimeException ignored) {
                // упавший поставщик не должен отнимать у оператора его права
            }
        }
        return opLevel(server, id, profile) >= OP_LEVEL ? OPERATOR : PLAYER;
    }

    /** Уровень оператора игры: список операторов и хозяин одиночного мира с читами. */
    public static int opLevel(MinecraftServer server, UUID id, @Nullable GameProfile profile) {
        GameProfile gp = profile != null ? profile : new GameProfile(id, "");
        return server.getProfilePermissions(gp);
    }

    public static boolean atLeast(ServerPlayer player, String role) {
        return rank(of(player)) >= rank(role);
    }

    public static boolean atLeast(MinecraftServer server, UUID player, String role) {
        return rank(of(server, player)) >= rank(role);
    }

    // --- клиент ---

    /** Роль своего игрока на клиенте: её сообщает мод сервера (RYSHelm); иначе по уровню оператора, который игра знает. */
    public static String client() {
        String r = clientRole;
        if (r != null) return r;
        return ClientOp.op() ? OPERATOR : PLAYER;
    }

    /** RYSHelm на клиенте сообщает роль, пришедшую с сервера. Null — вернуть правило по уровню оператора (и снять признак модерации). */
    public static void setClient(@Nullable String role) {
        clientRole = role;
        if (role == null) moderationActive = false;
    }

    /**
     * Признак с сервера (1.15): на сервере назначен хоть один модератор или есть открытые жалобы. Сообщает RYSHelm вместе
     * с ролью. Пока его нет, оператору кольцо «Модерация» не показывается: в кооперативе друзей все операторы, жалоб и
     * модераторов нет.
     */
    public static void setModerationActive(boolean active) {
        moderationActive = active;
    }

    public static boolean moderationActive() {
        return moderationActive;
    }

    /** Нужно ли своему игроку кольцо «Модерация»: он модератор, или он оператор и модерация на сервере идёт. С 1.15. */
    public static boolean moderationRing() {
        String role = client();
        return MODERATOR.equals(role) || rank(role) >= rank(MODERATOR) && moderationActive;
    }

    public static boolean atLeastClient(String role) {
        return rank(client()) >= rank(role);
    }

    /** Отдельный класс: на выделенном сервере классы клиента не грузятся. */
    private static final class ClientOp {
        static boolean op() {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            return player != null && player.hasPermissions(OP_LEVEL);
        }
    }
}
