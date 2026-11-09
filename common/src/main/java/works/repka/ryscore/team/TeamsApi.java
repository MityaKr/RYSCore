package works.repka.ryscore.team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

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
    }

    /** Команда игрока. Соло: UUID самого игрока. */
    UUID teamOf(UUID player);

    /** Участники команды. Для соло только сам игрок. */
    Collection<UUID> members(UUID team);

    /** Может ли игрок выполнить действие, например {@code claim_rewards}. Соло может всё. */
    boolean can(UUID player, String permission);

    void post(UUID team, Post post);

    default boolean isSolo(UUID team) {
        return members(team).size() <= 1;
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
    }
}
