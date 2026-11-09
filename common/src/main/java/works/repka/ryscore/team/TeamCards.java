package works.repka.ryscore.team;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Карточки сводки на экране команды, только на клиенте. Мод семьи регистрирует поставщика,
 * экран спрашивает его при показе; поставщик возвращает null, если показывать нечего.
 */
public final class TeamCards {
    /** @param source источник вроде {@code rysquests:quests}, путь определяет подпись и цвет */
    public record Card(ResourceLocation source, Component title, List<Component> lines, @Nullable Component button, @Nullable Runnable action, boolean alert) {}

    /** Карточка с подписью справа от заголовка и полосой прогресса; progress меньше нуля означает «без полосы». */
    public record Detailed(Card card, @Nullable Component tag, float progress) {}

    private static final List<Supplier<Card>> PROVIDERS = new ArrayList<>();
    private static final List<Supplier<Detailed>> DETAILED = new ArrayList<>();

    private TeamCards() {}

    public static void register(Supplier<Card> provider) {
        PROVIDERS.add(provider);
    }

    public static void registerDetailed(Supplier<Detailed> provider) {
        DETAILED.add(provider);
    }

    /** Все карточки, простые и с подробностями. */
    public static List<Detailed> currentDetailed() {
        List<Detailed> out = new ArrayList<>();
        for (Supplier<Detailed> provider : DETAILED) {
            try {
                Detailed card = provider.get();
                if (card != null) out.add(card);
            } catch (Throwable t) {
                LoggerFactory.getLogger(TeamCards.class).warn("Карточка сводки не построена", t);
            }
        }
        for (Card card : current()) out.add(new Detailed(card, null, -1f));
        return out;
    }

    public static List<Card> current() {
        List<Card> out = new ArrayList<>();
        for (Supplier<Card> provider : PROVIDERS) {
            try {
                Card card = provider.get();
                if (card != null) out.add(card);
            } catch (Throwable t) {
                // чужая карточка не должна ломать экран команды
                LoggerFactory.getLogger(TeamCards.class).warn("Карточка сводки не построена", t);
            }
        }
        return out;
    }
}
