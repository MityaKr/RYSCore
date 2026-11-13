package works.repka.ryscore.dock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Реестр кнопок семьи для панели кнопок в инвентаре ({@link Dock}). Мод регистрирует кнопку на клиенте один раз,
 * при запуске; повторная регистрация с тем же ключом заменяет прежнюю. Только с потока отрисовки.
 */
public final class DockButtons {
    private static final Map<String, DockButton> BUTTONS = new LinkedHashMap<>();
    private static final Set<String> HIDDEN_FTB = new HashSet<>();
    private static final Map<String, FtbStyle> FTB_STYLES = new LinkedHashMap<>();
    private static int version;

    /** Свой вид кнопки FTB на панели семьи: белая текстура 42×42 на цветной плитке и место. */
    record FtbStyle(ResourceLocation texture, int tile, int order) {}

    private DockButtons() {}

    public static void register(DockButton button) {
        BUTTONS.put(button.id(), button);
        version++;
    }

    public static void unregister(String id) {
        if (BUTTONS.remove(id) != null) version++;
    }

    /** Все зарегистрированные кнопки по порядку ({@link DockButton#order}), включая скрытые сейчас. */
    public static List<DockButton> all() {
        List<DockButton> out = new ArrayList<>(BUTTONS.values());
        out.sort(Comparator.comparingInt(DockButton::order));
        return out;
    }

    @Nullable
    public static DockButton get(String id) {
        return BUTTONS.get(id);
    }

    /** Кнопку FTB с этим ключом панель семьи не показывает (например, её заменяет мод семьи без своей кнопки в реестре). */
    public static void hideFtb(String key) {
        if (HIDDEN_FTB.add(key)) version++;
    }

    /**
     * Свой вид кнопки FTB с ключом {@code key} на панели семьи: текстура значка 42×42 (белая, окрашивается),
     * цвет плитки, место. Для кнопок модов, у которых нет своей кнопки в реестре, но есть кнопка в панели FTB.
     */
    public static void styleFtb(String key, ResourceLocation texture, int tile, int order) {
        FTB_STYLES.put(key, new FtbStyle(texture, tile, order));
        version++;
    }

    static boolean ftbHidden(String key) {
        if (HIDDEN_FTB.contains(key)) return true;
        for (DockButton b : BUTTONS.values()) {
            if (b.id().equals(key) || b.replacesFtb().contains(key)) return true;
        }
        return false;
    }

    @Nullable
    static FtbStyle ftbStyle(String key) {
        return FTB_STYLES.get(key);
    }

    static int version() {
        return version;
    }
}
