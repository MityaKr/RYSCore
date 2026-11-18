package works.repka.ryscore;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Общая основа модов семьи RYS: темы, рисование, виджеты. Правила и контракт в CLAUDE.md этого проекта. */
public final class RYSCore {
    public static final String MOD_ID = "ryscore";
    public static final Logger LOG = LoggerFactory.getLogger("RYSCore");

    private static volatile @Nullable MinecraftServer server;

    private RYSCore() {}

    /** Запущенный сервер (выделенный или встроенный), null между мирами и на клиенте без мира. С 1.11. */
    public static @Nullable MinecraftServer server() {
        return server;
    }

    /** Ставит точка входа ядра при запуске и остановке сервера. Модам не вызывать. */
    public static void server(@Nullable MinecraftServer current) {
        server = current;
    }
}
