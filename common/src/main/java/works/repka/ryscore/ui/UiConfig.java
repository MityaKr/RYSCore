package works.repka.ryscore.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

/** Настройки вида, общие для семьи: {@code config/ryscore/ui.json}. */
final class UiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static UiConfig instance;

    /** Размер интерфейса: 0 это авто, иначе проценты. */
    int scale = UiScale.AUTO;
    /** Угол, занятый картой, и высота зоны в процентах от высоты экрана: мини-карты растут вместе с окном. */
    Anchors.Corner mapCorner = Anchors.Corner.TOP_RIGHT;
    int mapPercent = 25;
    Map<Anchors.Element, Anchors.Corner> corners = new EnumMap<>(Anchors.Element.class);
    /** Звук уведомлений семьи и его громкость в процентах: по умолчанию включён и обычной громкости. */
    boolean noticeSound = true;
    int noticeVolume = 100;
    /** Где стоят уведомления семьи (1.17): справа под зоной карты, по центру, слева. */
    Notices.Place noticePlace = Notices.Place.RIGHT;
    /** Разовые подсказки, которые игрок уже видел ({@link Onboarding}), с 1.15. */
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();

    private UiConfig() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("ryscore").resolve("ui.json");
    }

    static UiConfig get() {
        if (instance == null) {
            Path file = file();
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    instance = GSON.fromJson(reader, UiConfig.class);
                } catch (IOException | RuntimeException e) {
                    LoggerFactory.getLogger(UiConfig.class).warn("Настройки вида не прочитаны: {}", e.toString());
                }
            }
            if (instance == null) instance = new UiConfig();
            if (instance.corners == null) instance.corners = new EnumMap<>(Anchors.Element.class);
        }
        return instance;
    }

    static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(get(), writer);
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(UiConfig.class).warn("Настройки вида не сохранены: {}", e.toString());
        }
    }
}
