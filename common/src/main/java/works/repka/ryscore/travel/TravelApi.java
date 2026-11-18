package works.repka.ryscore.travel;

import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.Preview;

/**
 * Перемещения игроков для модов семьи (1.14, план Essentials 4.5), только сервер, главный поток. Всё, что телепортирует
 * игрока в семье, идёт через этот контракт: пауза по причине, разогрев, безопасная посадка, история «назад», проверка
 * привата. Реализует RYSMap ({@link #provide}); без него ядро переносит игрока простым телепортом без пауз и истории.
 *
 * <pre>{@code
 * TravelApi.Result r = TravelApi.get().teleport(player, TravelApi.Trip.to(TravelApi.Spot.of(level, x, y, z))
 *     .reason(TravelApi.Reason.STAFF).skipCooldown().build());
 * if (r.status() == TravelApi.Status.DENIED) player.sendSystemMessage(r.message());
 * }</pre>
 *
 * <p>Помечен {@link Preview}: до 1.0 RYSMap контракт можно менять с записью в CHANGELOG.
 */
@Preview
public interface TravelApi {
    /** Итог перемещения. */
    enum Status {
        /** Игрок перенесён. */
        DONE,
        /** Пошёл разогрев: игрок будет перенесён через {@link Result#seconds()}, если урон не сорвёт. */
        WARMUP,
        /** Пауза ещё {@link Result#seconds()} секунд, никуда не перенесён. */
        PAUSE,
        /** Запрещено, причина в {@link Result#message()}. */
        DENIED
    }

    /**
     * Причина перемещения: от неё зависят пауза, разогрев и правило «кто может». Не перечисление: моды заводят свои
     * ({@link #of}).
     */
    final class Reason {
        public static final Reason HOME = new Reason("home"), TEAM_HOME = new Reason("team_home"), PLACE = new Reason("place"),
            BACK = new Reason("back"), DEATH = new Reason("death"), SPAWN = new Reason("spawn"), BED = new Reason("bed"),
            RANDOM = new Reason("random"), REQUEST = new Reason("request"), MAP = new Reason("map"), STAFF = new Reason("staff"),
            OTHER = new Reason("other");

        private final String id;

        private Reason(String id) {
            this.id = id;
        }

        public static Reason of(String id) {
            return new Reason(Objects.requireNonNull(id));
        }

        public String id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Reason r && r.id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** Место: измерение, точка, взгляд. Измерение может быть ещё не загружено: это проверит телепорт в момент прыжка. */
    final class Spot {
        private final ResourceKey<Level> dimension;
        private final double x, y, z;
        private final float yRot, xRot;
        private final boolean facing;

        private Spot(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot, boolean facing) {
            this.dimension = Objects.requireNonNull(dimension);
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.facing = facing;
        }

        public static Spot of(ResourceKey<Level> dimension, double x, double y, double z) {
            return new Spot(dimension, x, y, z, 0, 0, false);
        }

        public static Spot of(ServerLevel level, double x, double y, double z) {
            return of(level.dimension(), x, y, z);
        }

        public static Spot of(ResourceLocation dimension, double x, double y, double z) {
            return of(ResourceKey.create(Registries.DIMENSION, dimension), x, y, z);
        }

        /** Где стоит игрок сейчас, со взглядом. */
        public static Spot of(ServerPlayer player) {
            return new Spot(player.level().dimension(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), true);
        }

        /** То же место со взглядом. */
        public Spot facing(float yRot, float xRot) {
            return new Spot(dimension, x, y, z, yRot, xRot, true);
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public float yRot() {
            return yRot;
        }

        public float xRot() {
            return xRot;
        }

        /** Задан ли взгляд; если нет, игрок смотрит, куда смотрел. */
        public boolean hasFacing() {
            return facing;
        }
    }

    /** Поездка: куда, зачем и что пропустить (для персонала). Строитель, в 1.x можно добавлять поля. */
    final class Trip {
        private final Spot spot;
        private final Reason reason;
        private final boolean skipCooldown, skipWarmup, skipClaims, skipSafeSpot, recordBack;
        private final @Nullable Component label;

        private Trip(Builder b) {
            this.spot = b.spot;
            this.reason = b.reason;
            this.skipCooldown = b.skipCooldown;
            this.skipWarmup = b.skipWarmup;
            this.skipClaims = b.skipClaims;
            this.skipSafeSpot = b.skipSafeSpot;
            this.recordBack = b.recordBack;
            this.label = b.label;
        }

        public static Builder to(Spot spot) {
            return new Builder(spot);
        }

        public Spot spot() {
            return spot;
        }

        public Reason reason() {
            return reason;
        }

        public boolean skipCooldown() {
            return skipCooldown;
        }

        public boolean skipWarmup() {
            return skipWarmup;
        }

        public boolean skipClaims() {
            return skipClaims;
        }

        public boolean skipSafeSpot() {
            return skipSafeSpot;
        }

        /** Писать ли место отправления в историю «назад» (по умолчанию да). */
        public boolean recordBack() {
            return recordBack;
        }

        /** Куда, для строки разогрева и отказа: «Домой», «oil». */
        public @Nullable Component label() {
            return label;
        }

        public static final class Builder {
            private final Spot spot;
            private Reason reason = Reason.OTHER;
            private boolean skipCooldown, skipWarmup, skipClaims, skipSafeSpot, recordBack = true;
            private @Nullable Component label;

            private Builder(Spot spot) {
                this.spot = Objects.requireNonNull(spot);
            }

            public Builder reason(Reason reason) {
                this.reason = Objects.requireNonNull(reason);
                return this;
            }

            /** Без паузы (персонал, право «без паузы» решает реализация и само). */
            public Builder skipCooldown() {
                this.skipCooldown = true;
                return this;
            }

            public Builder skipWarmup() {
                this.skipWarmup = true;
                return this;
            }

            /** Без проверки привата: «К игроку» у модератора. */
            public Builder skipClaims() {
                this.skipClaims = true;
                return this;
            }

            /** Точно в точку, без поиска безопасной посадки. */
            public Builder skipSafeSpot() {
                this.skipSafeSpot = true;
                return this;
            }

            /** Не писать в историю «назад». */
            public Builder noBack() {
                this.recordBack = false;
                return this;
            }

            public Builder label(Component label) {
                this.label = label;
                return this;
            }

            public Trip build() {
                return new Trip(this);
            }
        }
    }

    /** Итог: статус, секунды (паузы или разогрева), сообщение для игрока. */
    final class Result {
        private static final Result DONE_RESULT = new Result(Status.DONE, 0, Component.empty());

        private final Status status;
        private final int seconds;
        private final Component message;

        private Result(Status status, int seconds, Component message) {
            this.status = status;
            this.seconds = seconds;
            this.message = message;
        }

        public static Result done() {
            return DONE_RESULT;
        }

        public static Result warmup(int seconds) {
            return new Result(Status.WARMUP, seconds, Component.empty());
        }

        public static Result pause(int seconds) {
            return new Result(Status.PAUSE, seconds, Component.empty());
        }

        public static Result denied(Component why) {
            return new Result(Status.DENIED, 0, why == null ? Component.empty() : why);
        }

        public Status status() {
            return status;
        }

        public int seconds() {
            return seconds;
        }

        public Component message() {
            return message;
        }

        /** Перенесён или будет перенесён после разогрева. */
        public boolean ok() {
            return status == Status.DONE || status == Status.WARMUP;
        }
    }

    /** Переносит игрока. */
    Result teleport(ServerPlayer player, Trip trip);

    /** Короткая форма: место и причина, всё по правилам. */
    default Result teleport(ServerPlayer player, Spot spot, Reason reason) {
        return teleport(player, Trip.to(spot).reason(reason).build());
    }

    /** Сколько секунд паузы осталось у игрока по причине, 0 — можно. */
    default int cooldown(ServerPlayer player, Reason reason) {
        return 0;
    }

    /** Записать место в историю «назад» без перемещения (чужой телепорт, смерть). Без RYSMap ничего не делает. */
    default void rememberBack(ServerPlayer player, Spot from, Reason reason) {}

    /** Отменить разогрев игрока, если он идёт. */
    default void cancelWarmup(ServerPlayer player) {}

    /** Реализован ли контракт модом (RYSMap), а не простым телепортом ядра. */
    default boolean present() {
        return true;
    }

    static TravelApi get() {
        return Holder.current;
    }

    /** Ставит RYSMap при запуске. */
    static void provide(TravelApi implementation) {
        Holder.current = Objects.requireNonNull(implementation);
    }

    final class Holder {
        private Holder() {}

        private static volatile TravelApi current = new TravelApi() {
            @Override
            public Result teleport(ServerPlayer player, Trip trip) {
                Spot s = trip.spot();
                ServerLevel level = player.server.getLevel(s.dimension());
                if (level == null) return Result.denied(Component.translatable("ryscore.travel.no_dimension", s.dimension().location().toString()));
                float yRot = s.hasFacing() ? s.yRot() : player.getYRot(), xRot = s.hasFacing() ? s.xRot() : player.getXRot();
                player.teleportTo(level, s.x(), s.y(), s.z(), yRot, xRot);
                return Result.done();
            }

            @Override
            public boolean present() {
                return false;
            }
        };
    }
}
