package works.repka.ryscore.ui;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Правило «окна не перекрываются»: окна стоят в ряд, и если места не хватает, второстепенное сворачивается.
 * Единицы любые, лишь бы одинаковые у всех окон и у {@code available}.
 */
public final class WindowLayout {
    /**
     * @param collapsedWidth ширина в свёрнутом виде, например полоска с кнопкой раскрытия; 0 скрывает окно
     * @param priority чем больше, тем позже окно свернётся
     */
    public record Window(String id, int width, int collapsedWidth, int priority) {}

    public record Placement(int width, boolean collapsed) {}

    private WindowLayout() {}

    public static Map<String, Placement> arrange(int available, int gap, Window... windows) {
        boolean[] collapsed = new boolean[windows.length];
        Integer[] order = new Integer[windows.length];
        Arrays.setAll(order, i -> i);
        Arrays.sort(order, (a, b) -> Integer.compare(windows[a].priority(), windows[b].priority()));
        for (int next = 0; next <= order.length; next++) {
            int total = 0, shown = 0;
            for (int i = 0; i < windows.length; i++) {
                int w = collapsed[i] ? windows[i].collapsedWidth() : windows[i].width();
                total += w;
                if (w > 0) shown++;
            }
            total += Math.max(0, shown - 1) * gap;
            // главное окно не сворачиваем никогда: последнему остаётся всё, что есть
            if (total <= available || next >= order.length - 1) break;
            collapsed[order[next]] = true;
        }
        Map<String, Placement> out = new LinkedHashMap<>();
        for (int i = 0; i < windows.length; i++) {
            out.put(windows[i].id(), new Placement(collapsed[i] ? windows[i].collapsedWidth() : windows[i].width(), collapsed[i]));
        }
        return out;
    }
}
