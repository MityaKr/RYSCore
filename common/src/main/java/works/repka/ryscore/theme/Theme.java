package works.repka.ryscore.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Tiles;

/**
 * Тема: только токены (цвета, размеры, эффекты). Дерево виджетов у всех тем одно,
 * тема не может менять раскладку.
 */
public final class Theme {
    /** Макеты рассчитаны на окно 1280×800 при GUI Scale 2: в одной единице GUI два пикселя макета. */
    private static final float DESIGN_SCALE = 2f;

    private static final Map<String, String> FALLBACK = new HashMap<>();

    static {
        fb("card.bg", "panel.bg");
        fb("slot.dim", "overlay");
        fb("hud.muted", "text.muted");
        fb("card.border", "panel.border");
        fb("sidebar.bg", "panel.bg");
        fb("slot.border", "slot.bg");
        fb("slot.hover", "accent");
        fb("input.bg", "slot.bg");
        fb("input.fg", "text.body");
        fb("input.border", "panel.border");
        fb("button.bg", "panel.bg");
        fb("button.border", "panel.border");
        fb("button.hover", "slot.hover");
        fb("text.body", "text.primary");
        fb("text.muted", "text.body");
        fb("text.dim", "text.muted");
        fb("accent.text", "text.primary");
        fb("state.ok", "accent");
        fb("state.warn", "accent");
        fb("state.bad", "accent");
        fb("chip.mod.bg", "button.bg");
        fb("chip.mod.fg", "text.body");
        fb("chip.fn.bg", "button.bg");
        fb("chip.fn.fg", "text.body");
        fb("chip.tag.bg", "button.bg");
        fb("chip.tag.fg", "text.body");
        fb("tooltip.bg", "panel.bg");
        fb("tooltip.border", "panel.border");
        fb("tab.active.bg", "accent");
        fb("tab.active.fg", "accent.text");
        fb("tab.inactive.bg", "button.bg");
        fb("tab.inactive.fg", "text.muted");
        fb("hud.bg", "panel.bg");
        fb("hud.border", "panel.border");
        fb("scroll.thumb", "text.dim");
        fb("panel.bevel.light", "panel.border");
        fb("panel.bevel.dark", "panel.border");
        fb("slot.bevel.light", "slot.border");
        fb("slot.bevel.dark", "slot.border");
        fb("slot.have.border", "state.ok");
        fb("hud.fg", "text.primary");
        fb("cell.bg", "slot.bg");
        fb("cell.border", "slot.border");
        fb("cell.mark.border", "cell.border");
        fb("filter.active.bg", "button.bg");
        fb("status.bg", "panel.bg");
        fb("column.bg", "sidebar.bg");
        fb("row.alt", "card.bg");
        fb("callout.bg", "status.bg");
        fb("callout.border", "state.warn");
        fb("ok.border", "state.ok");
        fb("src.quests.bg", "chip.fn.bg");
        fb("src.pulse.bg", "chip.fn.bg");
        fb("src.scope.bg", "chip.mod.bg");
        fb("src.teams.bg", "chip.tag.bg");
        fb("src.quests.border", "panel.border");
        fb("src.pulse.border", "panel.border");
        fb("src.scope.border", "panel.border");
        fb("src.teams.border", "panel.border");
        fb("status.border", "panel.border");
        fb("text.secondary", "text.body");
        fb("button.fg", "text.body");
        fb("tooltip.fg", "text.primary");
        fb("slot.miss.border", "state.warn");
        // книга квестов и карта (1.9.0). Значение с «#» это готовый цвет: им кончается цепочка
        fb("link", "state.order");
        // выбранная строка списка и строка «один из»: светлая подложка, не акцент
        fb("row.selected.bg", "tab.active.bg");
        fb("map.bg", "input.bg");
        fb("map.label.bg", "tooltip.bg");
        // полотно тёмное в любой теме: запасные взяты из slate, а не из текста окна
        fb("map.label.fg", "#f7f8fb");
        fb("map.node.done", "#6fcf8f");
        fb("map.node.open", "#f7f8fb");
        fb("map.node.locked", "#5f6678");
        fb("medal.bronze", "#c8794a");
        fb("medal.silver", "#c9cfdd");
        fb("medal.gold", "#c79a12");
        // уведомления (1.9.2): зелёный и золотой на тёмной плашке HUD, цвет значка на плашке источника
        fb("hud.ok", "#6fcf8f");
        fb("hud.gold", "#c79a12");
        fb("src.quests.fg", "accent");
        fb("src.scope.fg", "chip.mod.fg");
        fb("src.teams.fg", "chip.tag.fg");
        fb("src.pulse.fg", "state.bad");
        fb("coin", "medal.gold");
        String[] cats = {"#e4b23c", "#6fa8ff", "#6fcf8f", "#e58a6a", "#b78cf0", "#5fd0d0", "#d7d36a", "#f08fb8", "#9aa3b5", "#8fd18a", "#c9a37a", "#7f8ff0"};
        for (int i = 0; i < cats.length; i++) fb("cat." + (i + 1), cats[i]);
    }

    /** Цвета разделов по кругу: {@code cat.1} … {@code cat.12}, номер с нуля. */
    public int category(int index) {
        return c("cat." + (Math.floorMod(index, 12) + 1));
    }

    private static void fb(String key, String fallback) {
        FALLBACK.put(key, fallback);
    }

    public final String id;
    public final String name;
    private final Map<String, Integer> colors = new HashMap<>();
    private final Map<String, Float> metrics = new HashMap<>();
    private final Map<String, Integer> resolved = new HashMap<>();
    public final boolean shadow;
    public final boolean pillTabs;
    /** Вкладки разделов подчёркиваются, а не заливаются. */
    /** Боковая панель прижата к краю экрана на всю высоту. */
    public final boolean flushSidebar;
    private final boolean type;
    public final boolean reducedMotion;

    public Theme(String id, JsonObject json) {
        this.id = id;
        this.name = json.has("name") ? json.get("name").getAsString() : id;
        JsonObject c = json.has("colors") ? json.getAsJsonObject("colors") : new JsonObject();
        Map<String, Float> alphas = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : c.entrySet()) {
            JsonElement v = e.getValue();
            if (!v.isJsonPrimitive()) continue;
            if (v.getAsJsonPrimitive().isNumber()) {
                if (e.getKey().endsWith(".alpha")) {
                    alphas.put(e.getKey().substring(0, e.getKey().length() - 6), v.getAsFloat());
                }
            } else {
                Integer parsed = parseColor(v.getAsString());
                if (parsed != null) colors.put(e.getKey(), parsed);
            }
        }
        for (Map.Entry<String, Float> a : alphas.entrySet()) {
            // «panel.alpha» относится к «panel.bg», «text.muted.alpha» к «text.muted»
            String key = colors.containsKey(a.getKey()) ? a.getKey() : a.getKey() + ".bg";
            Integer base = colors.get(key);
            if (base != null) colors.put(key, Draw.alpha(base, a.getValue()));
        }
        boolean pill = false;
        if (json.has("metrics")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("metrics").entrySet()) {
                JsonElement v = e.getValue();
                if (!v.isJsonPrimitive()) continue;
                if (v.getAsJsonPrimitive().isNumber()) metrics.put(e.getKey(), v.getAsFloat());
                else if (e.getKey().equals("tab.style")) {
                    pill = v.getAsString().equals("pill");
                }
            }
        }
        this.pillTabs = pill;
        this.flushSidebar = m("sidebar.flush", 0) > 0;
        this.type = json.has("font") && json.get("font").getAsString().equals("ryscore:inter");
        JsonObject fx = json.has("effects") ? json.getAsJsonObject("effects") : new JsonObject();
        this.shadow = fx.has("shadow") && fx.get("shadow").getAsBoolean();
        this.reducedMotion = fx.has("animations") && !fx.get("animations").getAsString().equals("normal");
    }

    private static Integer parseColor(String s) {
        try {
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            if (s.length() == 8) return Integer.parseUnsignedInt(s, 16);
        } catch (NumberFormatException ignored) {
            // цвет остаётся незаданным, сработает запасной
        }
        return null;
    }

    /** Цвет токена в формате ARGB. Незаданные токены берутся по цепочке запасных. */
    public int c(String key) {
        Integer cached = resolved.get(key);
        if (cached != null) return cached;
        String k = key, last = key;
        Integer v = null;
        for (int depth = 0; depth < 6 && k != null; depth++) {
            if (k.startsWith("#")) {
                v = parseColor(k);
                break;
            }
            v = colors.get(k);
            if (v != null) break;
            last = k;
            k = FALLBACK.get(k);
        }
        if (v == null) {
            // встроенное значение по последнему ключу цепочки: slot.dim без overlay в теме берёт цвет overlay
            v = switch (last) {
                case "plate.bg" -> 0xFFC6C6C6;
                case "plate.border" -> 0xFF8B8B8B;
                case "panel.border", "card.border", "input.border", "button.border" -> 0;
                case "overlay" -> 0x8C0E1016;
                default -> 0xFFFF00FF;
            };
        }
        resolved.put(key, v);
        return v;
    }

    public float m(String key, float def) {
        Float v = metrics.get(key);
        return v == null ? def : v;
    }

    /** Радиус токена в единицах GUI. */
    public float r(String key) {
        return Math.min(m(key, 0), 40f) / DESIGN_SCALE;
    }

    /** Рисует ли тема текст шрифтом из макета, а не шрифтом игры. */
    public boolean usesType() {
        return type;
    }

    public boolean beveled() {
        return m("bevel", 0) > 0;
    }

    // --- примитивы интерфейса: единственное место, где тема влияет на вид ---

    public void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (beveled()) {
            int t = Math.max(1, Math.round(m("panel.bevel", 4) / 2f));
            Draw.rect(g, x - 1, y - 1, w + 2, h + 2, 0xFF000000);
            Draw.bevel(g, x, y, w, h, c("panel.bg"), c("panel.bevel.light"), c("panel.bevel.dark"), t, false);
            return;
        }
        float r = r("panel.radius");
        if (shadow) Draw.shadow(g, x, y, w, h, r, 1f);
        Draw.roundBox(g, x, y, w, h, r, c("panel.bg"), c("panel.border"));
    }

    /** Боковая панель: у темы с прижатой панелью без скруглений и тени, с линией вдоль внутреннего края. */
    public void sidebar(GuiGraphics g, int x, int y, int w, int h) {
        if (!flushSidebar) {
            panel(g, x, y, w, h);
            return;
        }
        Draw.rect(g, x, y, w, h, c("sidebar.bg"));
        Draw.rect(g, x, y, 1, h, beveled() ? c("panel.bevel.light") : c("panel.border"));
    }

    public void card(GuiGraphics g, int x, int y, int w, int h) {
        if (beveled()) {
            Draw.bevel(g, x, y, w, h, c("card.bg"), c("panel.bevel.dark"), c("panel.bevel.light"), 1, false);
            return;
        }
        Draw.roundBox(g, x, y, w, h, r("card.radius"), c("card.bg"), c("card.border"));
    }

    /** Подложка для рецептов чужих модов: они нарисованы в расчёте на светло-серую ванильную панель. */
    public void plate(GuiGraphics g, int x, int y, int w, int h) {
        if (beveled()) {
            Draw.rect(g, x, y, w, h, c("plate.bg"));
            return;
        }
        Draw.roundBox(g, x, y, w, h, Math.min(r("card.radius"), 4f), c("plate.bg"), c("plate.border"));
    }

    public void slot(GuiGraphics g, int x, int y, int size, boolean hovered) {
        if (beveled()) {
            Draw.bevel(g, x, y, size, size, c("slot.bg"), c("slot.bevel.light"), c("slot.bevel.dark"), 1, true);
            if (hovered) Draw.rect(g, x + 1, y + 1, size - 2, size - 2, 0x80FFFFFF);
            return;
        }
        float r = r("slot.radius");
        Draw.roundBox(g, x, y, size, size, r, c("slot.bg"), hovered ? c("accent") : c("slot.border"));
    }

    /** Слот с рамкой состояния, например «есть в инвентаре» и «не хватает». border = 0 даёт обычный слот. */
    public void slot(GuiGraphics g, int x, int y, int size, boolean hovered, int border) {
        if (border == 0 || hovered) {
            slot(g, x, y, size, hovered);
            return;
        }
        if (beveled()) {
            slot(g, x, y, size, false);
            Draw.rect(g, x + 1, y + 1, size - 2, size - 2, Draw.alpha(border, 0.35f));
            return;
        }
        Draw.roundBox(g, x, y, size, size, r("slot.radius"), c("slot.bg"), border);
    }

    /** Ячейка списка предметов по макету: свой фон и рамка, размер в единицах GUI может быть дробным. */
    public void cells(GuiGraphics g, float size, int[] xy, int count, int border) {
        Tiles.draw(g, size, new Tiles.Style(beveled() ? 0 : m("cell.radius", 7f) / DESIGN_SCALE, c("cell.bg"), border,
            c("slot.bevel.light"), c("slot.bevel.dark"), beveled()), xy, count);
    }

    /** Слот рецепта по макету: радиус в пикселях макета (сетка 8, результат 10). */
    public void slotRounded(GuiGraphics g, int x, int y, int size, float radiusPx, boolean hovered, int border) {
        if (beveled()) {
            slot(g, x, y, size, hovered, border);
            return;
        }
        Draw.roundBox(g, x, y, size, size, radiusPx / DESIGN_SCALE, c("slot.bg"), hovered ? c("accent") : border == 0 ? c("card.border") : border);
    }

    /** Много одинаковых слотов за один вызов отрисовки: для длинных списков. */
    public void slots(GuiGraphics g, int size, int[] xy, int count) {
        slots(g, size, xy, count, c("slot.border"));
    }

    public void slots(GuiGraphics g, int size, int[] xy, int count, int border) {
        Tiles.draw(g, size, new Tiles.Style(beveled() ? 0 : r("slot.radius"), c("slot.bg"), border,
            c("slot.bevel.light"), c("slot.bevel.dark"), beveled()), xy, count);
    }

    /** Подсветка ячейки списка предметов под курсором (без рамки слота). */
    public void hover(GuiGraphics g, int x, int y, int size) {
        if (beveled()) {
            Draw.rect(g, x, y, size, size, 0x80FFFFFF);
            return;
        }
        Draw.round(g, x, y, size, size, r("slot.radius"), Draw.alpha(c("slot.hover"), 0.9f));
    }

    public void button(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean accent) {
        if (beveled()) {
            int fill = accent ? c("accent") : c("button.bg");
            if (hovered) fill = Draw.mix(fill, 0xFFFFFFFF, 0.18f);
            Draw.rect(g, x - 1, y - 1, w + 2, h + 2, 0xFF000000);
            Draw.bevel(g, x, y, w, h, fill, Draw.mix(fill, 0xFFFFFFFF, 0.45f), Draw.mix(fill, 0xFF000000, 0.45f), 1, false);
            return;
        }
        float r = Math.min(r("button.radius"), h / 2f);
        int fill = accent ? c("accent") : c("button.bg");
        if (hovered) fill = Draw.mix(fill, accent ? 0xFFFFFFFF : c("text.primary"), accent ? 0.15f : 0.08f);
        Draw.roundBox(g, x, y, w, h, r, fill, accent ? 0 : c("button.border"));
    }

    public void input(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        if (beveled()) {
            Draw.rect(g, x - 1, y - 1, w + 2, h + 2, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
            Draw.rect(g, x, y, w, h, c("input.bg"));
            return;
        }
        float r = Math.min(r("button.radius"), h / 2f);
        Draw.roundBox(g, x, y, w, h, r, c("input.bg"), focused ? c("accent") : c("input.border"));
    }

    public void tab(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered) {
        if (beveled()) {
            int fill = active ? c("panel.bg") : c("tab.inactive.bg");
            Draw.rect(g, x - 1, y - 1, w + 2, h + 2, 0xFF000000);
            Draw.bevel(g, x, y, w, h, fill, c("panel.bevel.light"), c("panel.bevel.dark"), 1, false);
            return;
        }
        float r = pillTabs ? h / 2f : Math.min(r("button.radius"), h / 2f);
        int fill = active ? c("tab.active.bg") : c("tab.inactive.bg");
        if (hovered && !active) fill = Draw.mix(fill, c("text.primary"), 0.08f);
        Draw.roundBox(g, x, y, w, h, r, fill, active ? 0 : c("button.border"));
    }

    /**
     * Вкладка раздела окна (с 1.9.1 у каждой темы своя): в slate линия акцента под активной, в теме с пилюлями (glass) активная
     * лежит на пилюле {@code tab.active.bg}, в теме с фаской (vanilla) это закладка у нижнего края: неактивная серая
     * {@code tab.inactive.bg}, активная цвета панели и сливается с ней. Рамки и чёрного контура у закладки нет.
     */
    public void tabLine(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered) {
        if (beveled()) {
            // закладка: 17 единиц (34 px макета) от нижнего края, светлая кромка сверху и слева
            float th = Math.min(h, 17);
            float ty = y + h - th;
            int fill = active ? c("panel.bg") : c("tab.inactive.bg");
            if (hovered && !active) fill = Draw.mix(fill, 0xFFFFFFFF, 0.12f);
            int edge = active ? c("panel.bevel.light") : Draw.mix(fill, 0xFFFFFFFF, 0.3f);
            Draw.bevel(g, x, ty, w, th + (active ? 1 : 0), fill, edge, fill, 1, false);
            return;
        }
        if (pillTabs) {
            if (!active) return;
            float ph = Math.min(h - 2, 15);
            float py = y + (h - 1) / 2f - ph / 2f;
            Draw.round(g, x, py, w, ph, ph / 2f, c("tab.active.bg"));
            return;
        }
        if (active) Draw.round(g, x, y + h - 1, w, 1, 0.02f, c("accent"));
    }

    /**
     * Карточка, строка списка, плашка задачи (1.9.1): в теме с фаской выпуклая плашка (светлый край сверху и слева, тёмный снизу
     * и справа), в остальных скруглённая {@code card.bg} с рамкой {@code card.border}. Координаты дробные, радиус в единицах GUI.
     */
    public void raised(GuiGraphics g, float x, float y, float w, float h, float radius) {
        if (beveled()) {
            Draw.bevel(g, x, y, w, h, c("card.bg"), c("panel.bevel.light"), c("panel.bevel.dark"), 1, false);
            return;
        }
        Draw.roundBox(g, x, y, w, h, radius, c("card.bg"), c("card.border"));
    }

    /**
     * Нажатый чип фильтра или выбранный пункт переключателя (1.9.1): в теме с фаской вдавленная кнопка {@code slot.bg} с тёмным
     * краем сверху и слева, в остальных плашка {@code tab.active.bg}. Текст на ней {@link #pressedFg()}.
     */
    public void pressed(GuiGraphics g, float x, float y, float w, float h, float radius) {
        if (beveled()) {
            Draw.bevel(g, x, y, w, h, c("slot.bg"), c("slot.bevel.dark"), c("slot.bevel.light"), 1, false);
            return;
        }
        Draw.round(g, x, y, w, h, radius, c("tab.active.bg"));
    }

    /** Цвет текста на нажатом чипе: в теме с фаской светлый, как на ванильной кнопке, в остальных {@code text.primary}. */
    public int pressedFg() {
        return beveled() ? c("tab.inactive.fg") : c("text.primary");
    }

    /**
     * Рамка выбранной карточки (1.9.1), поверх её плашки: в теме с фаской белая полоса в 1,5 единицы и тёмная линия внутри
     * ({@code panel.bevel.light}, {@code panel.bevel.dark}), в остальных тонкая рамка {@code text.secondary}. Не акцент:
     * в vanilla акцент почти совпадает с «выполнено».
     */
    public void selected(GuiGraphics g, float x, float y, float w, float h, float radius) {
        if (beveled()) {
            Draw.roundBorder(g, x, y, w, h, 0.02f, c("panel.bevel.light"), 1.5f);
            Draw.roundBorder(g, x + 1.5f, y + 1.5f, w - 3, h - 3, 0.02f, c("panel.bevel.dark"), 0.5f);
            return;
        }
        Draw.roundBorder(g, x, y, w, h, radius, c("text.secondary"));
    }

    /** Цвет текста на вкладке раздела. */
    public int tabLineFg(boolean active, boolean hovered) {
        if (beveled()) return active ? c("tab.active.fg") : c("tab.inactive.fg");
        return active || hovered ? c("text.primary") : c("text.secondary");
    }

    public void chip(GuiGraphics g, int x, int y, int w, int h, String kind) {
        float r = beveled() ? 0 : h / 2f;
        Draw.round(g, x, y, w, h, r, c("chip." + kind + ".bg"));
    }

    public void tooltipBox(GuiGraphics g, int x, int y, int w, int h) {
        if (beveled()) {
            Draw.rect(g, x - 1, y - 1, w + 2, h + 2, c("tooltip.border"));
            Draw.rect(g, x, y, w, h, c("tooltip.bg"));
            return;
        }
        float r = Math.min(r("card.radius"), 5f);
        if (shadow) Draw.shadow(g, x, y, w, h, r, 0.8f);
        Draw.roundBox(g, x, y, w, h, r, c("tooltip.bg"), c("tooltip.border"));
    }
}
