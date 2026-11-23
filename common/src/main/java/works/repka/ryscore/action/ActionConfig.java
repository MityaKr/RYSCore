package works.repka.ryscore.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Клавиши и колесо игрока (1.14): {@code config/ryscore/keys.json}. Хранится у игрока, а не в мире: те же клавиши
 * на любом сервере. Сектор с пустой строкой игрок очистил сам, кольца без записи берут умолчания.
 */
final class ActionConfig {
    static final String HOLD = "hold", CLICK = "click";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    int version = 1;
    /** id действия → сочетание ({@link Binding#serialize}). */
    Map<String, String> bindings = new LinkedHashMap<>();
    /** id кольца → восемь id действий, пустая строка — пустой сектор. */
    Map<String, List<String>> rings = new LinkedHashMap<>();
    /** «Держать и отпустить» (короткое нажатие оставляет колесо открытым) или «Только клик». */
    String wheelMode = HOLD;
    /** Последнее открытое кольцо. */
    String lastRing = "";

    static ActionConfig load(Path file) {
        ActionConfig config = null;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, ActionConfig.class);
            } catch (IOException | RuntimeException e) {
                LoggerFactory.getLogger(ActionConfig.class).warn("Family keys not read: {}", e.toString());
            }
        }
        if (config == null) config = new ActionConfig();
        if (config.bindings == null) config.bindings = new LinkedHashMap<>();
        if (config.rings == null) config.rings = new LinkedHashMap<>();
        if (config.wheelMode == null) config.wheelMode = HOLD;
        if (config.lastRing == null) config.lastRing = "";
        return config;
    }

    void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(ActionConfig.class).warn("Family keys not saved: {}", e.toString());
        }
    }

    /** Сохранённые сектора кольца (ровно восемь) или null, если игрок кольцо не трогал. */
    @Nullable
    List<String> ring(String id) {
        List<String> saved = rings.get(id);
        if (saved == null) return null;
        List<String> out = new ArrayList<>(saved);
        while (out.size() < Ring.SECTORS) out.add("");
        return out.subList(0, Ring.SECTORS);
    }
}
