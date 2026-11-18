package works.repka.ryscore.claims;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.Preview;

/**
 * Земля для модов семьи (1.11). Вызывается на сервере, из потока сервера. Пока RYSClaims не установлен, земли нет и всё
 * разрешено: {@link #ownerAt} отвечает null, {@link #can} истину. Клиентской части у контракта нет: моды семьи на клиенте о
 * земле не спрашивают, клиент привата получает своё пакетами RYSClaims.
 *
 * <p>Команда здесь это UUID из {@code TeamsApi.teamOf}: без RYSTeams команда игрока это он сам.
 */
@Preview(until = "rysclaims 1.0")
public interface ClaimsApi {
    /** Что игрок или машина делает на земле. */
    enum Action {
        /** Ставить и ломать блоки. */
        BUILD,
        /** Открывать контейнеры. */
        OPEN,
        /** Двери, рычаги, кнопки и прочий правый клик по блоку. */
        USE,
        /** Животные, рамки, стойки и прочие сущности. */
        ENTITY
    }

    /** Установлен ли RYSClaims. Без него награды «+N к лимиту» не показываются. */
    default boolean present() {
        return false;
    }

    /** Чья земля в чанке: UUID команды или null, если чанк ничей. */
    @Nullable
    UUID ownerAt(ResourceKey<Level> dimension, int chunkX, int chunkZ);

    /** Может ли игрок сделать действие в этой точке. */
    boolean can(UUID player, ResourceKey<Level> dimension, BlockPos pos, Action action);

    /**
     * Может ли машина мода действовать: откуда она действует, чей у неё профиль (владелец блока, фейковый игрок или null,
     * если владельца нет) и куда действует. Машина на своей земле внутри своей земли может всё.
     */
    boolean canMachine(ResourceKey<Level> dimension, BlockPos from, @Nullable UUID profile, BlockPos target, Action action);

    /** Прогружен ли чанк приватом. */
    boolean forceLoaded(ResourceKey<Level> dimension, int chunkX, int chunkZ);

    /** Сколько чанков у команды. */
    int claimed(UUID team);

    /** Лимит чанков команды с добавками. Без RYSClaims 0. */
    int limit(UUID team);

    /** Добавка команде: клеймы и прогрузка, можно отрицательные. Остаётся у команды. */
    void addBonus(UUID team, int claims, int forceLoaded);

    /** Личная добавка: уходит с игроком в другую команду. Награда квеста «+N к лимиту» зовёт этот метод. */
    void addPlayerBonus(UUID player, int claims, int forceLoaded);

    /** Что происходит с землёй. Вызывается в потоке сервера после записи. */
    interface Listener {
        /** Команда заняла чанки; {@code chunks} это упакованные {@code ChunkPos.asLong}. */
        default void onClaimed(UUID team, ResourceKey<Level> dimension, LongSet chunks) {}

        /** Чанки освобождены. */
        default void onReleased(UUID team, ResourceKey<Level> dimension, LongSet chunks) {}
    }

    /** Текущая реализация: «земли нет» или установленный RYSClaims. */
    static ClaimsApi get() {
        return Holder.current;
    }

    /** Вызывает RYSClaims при запуске. */
    static void provide(ClaimsApi implementation) {
        Holder.current = implementation;
    }

    static void listen(Listener listener) {
        synchronized (Holder.LISTENERS) {
            Holder.LISTENERS.add(listener);
        }
    }

    /** Подписчики, для реализации API. */
    static List<Listener> listeners() {
        synchronized (Holder.LISTENERS) {
            return List.copyOf(Holder.LISTENERS);
        }
    }

    final class Holder {
        private static final List<Listener> LISTENERS = new ArrayList<>();
        private static volatile ClaimsApi current = new ClaimsApi() {
            @Override
            public @Nullable UUID ownerAt(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
                return null;
            }

            @Override
            public boolean can(UUID player, ResourceKey<Level> dimension, BlockPos pos, Action action) {
                return true;
            }

            @Override
            public boolean canMachine(ResourceKey<Level> dimension, BlockPos from, @Nullable UUID profile, BlockPos target, Action action) {
                return true;
            }

            @Override
            public boolean forceLoaded(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
                return false;
            }

            @Override
            public int claimed(UUID team) {
                return 0;
            }

            @Override
            public int limit(UUID team) {
                return 0;
            }

            @Override
            public void addBonus(UUID team, int claims, int forceLoaded) {
                // без RYSClaims лимитов нет
            }

            @Override
            public void addPlayerBonus(UUID player, int claims, int forceLoaded) {
                // без RYSClaims лимитов нет
            }
        };

        private Holder() {}
    }
}
