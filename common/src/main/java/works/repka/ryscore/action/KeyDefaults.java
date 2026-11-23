package works.repka.ryscore.action;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import works.repka.ryscore.RYSCore;

/**
 * Снятые клавиши по умолчанию (1.17). Игра пишет в {@code options.txt} все клавиши, и назначенные игроком, и умолчания,
 * поэтому новая клавиша «не назначена» у мода видна только в новой установке: у всех, кто уже играл, остаётся прежняя.
 * Мод объявляет снятие ({@link #retire}), ядро один раз на клавишу (после того как игра прочитала {@code options.txt})
 * смотрит сохранённое: если там прежнее умолчание без модификатора, клавиша снимается; другое значение значит, что игрок
 * назначил её сам, и оно не трогается. Сделанные снятия помнит {@code config/ryscore/retired-keys.txt}: если игрок потом
 * сам вернёт ту же клавишу, ядро её больше не снимет.
 */
public final class KeyDefaults {
    private record Entry(String id, KeyMapping key, String oldKey) {}

    private static final List<Entry> PENDING = new ArrayList<>();

    private KeyDefaults() {}

    /**
     * Снять у {@code key} прежнее умолчание {@code oldKey} (имя клавиши игры, например {@code key.keyboard.g}), если игрок
     * его не менял. Звать при загрузке мода, до первого тика клиента.
     * @param id постоянный ключ этого снятия, например {@code rysteams.open.g}
     */
    public static synchronized void retire(String id, KeyMapping key, String oldKey) {
        PENDING.add(new Entry(id, key, oldKey));
    }

    /** Снимать ли клавишу: сохранено прежнее умолчание без модификатора. */
    static boolean stale(String saved, int mods, String oldKey) {
        return mods == 0 && saved.equals(oldKey);
    }

    /** Ядро зовёт каждый тик клиента; работа только в первый раз после объявлений. */
    public static synchronized void apply() {
        if (PENDING.isEmpty()) return;
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("ryscore").resolve("retired-keys.txt");
        Set<String> done = new LinkedHashSet<>();
        try {
            if (Files.exists(file)) for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) if (!line.isBlank()) done.add(line.trim());
        } catch (IOException e) {
            RYSCore.LOG.warn("Retired keys not read: {}", e.toString());
        }
        boolean keys = false, marks = false;
        for (Entry e : PENDING) {
            if (!done.add(e.id)) continue;
            marks = true;
            if (stale(e.key.saveString(), Actions.modsOf(e.key), e.oldKey)) {
                e.key.setKey(InputConstants.UNKNOWN);
                keys = true;
                RYSCore.LOG.info("Key {} unbound: old default {} (can be bound in Controls)", e.key.getName(), e.oldKey);
            }
        }
        PENDING.clear();
        if (keys) {
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
        }
        if (marks) {
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, done, StandardCharsets.UTF_8);
            } catch (IOException e) {
                RYSCore.LOG.warn("Retired keys not saved: {}", e.toString());
            }
        }
    }
}
