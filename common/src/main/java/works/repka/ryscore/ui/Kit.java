package works.repka.ryscore.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.ui.Type.Weight;

/**
 * Элементы окон из макетов: шапка, кнопки, вкладки, переключатель, счётчик. Числа в единицах GUI (половина пикселя макета).
 * Экран рисует их сверху вниз и складывает нажимаемые места в {@link Hits}.
 */
public final class Kit {
    public static final int HEADER_H = 44;
    public static final int PAD = 9;
    public static final int TABS_H = 17;

    private Kit() {}

    /** Нажимаемые места текущего кадра. Позже добавленные лежат сверху. */
    public static final class Hits {
        private record Hit(int x, int y, int w, int h, IntConsumer action) {}

        private final List<Hit> hits = new ArrayList<>();
        private int top = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;

        public void clear() {
            hits.clear();
            top = Integer.MIN_VALUE;
            bottom = Integer.MAX_VALUE;
        }

        /** Места вне этой полосы не нажимаются: для прокручиваемых списков. */
        public void clip(int top, int bottom) {
            this.top = top;
            this.bottom = bottom;
        }

        public void unclip() {
            top = Integer.MIN_VALUE;
            bottom = Integer.MAX_VALUE;
        }

        public void add(int x, int y, int w, int h, IntConsumer action) {
            int y0 = Math.max(y, top), y1 = Math.min(y + h, bottom);
            if (y1 > y0) hits.add(new Hit(x, y0, w, y1 - y0, action));
        }

        public boolean click(double mx, double my, int button) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                    h.action.accept(button);
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Отступ окна сверху: как у карточки рецепта в макете. */
    public static int panelTop(int screenHeight) {
        return Math.max(4, screenHeight * 18 / 400);
    }

    public static void divider(GuiGraphics g, Theme t, float x, float y, float w) {
        Draw.round(g, x, y, w, 0.5f, 0.02f, t.c("card.border"));
    }

    /** Плашка значка в шапке, 46 пикселей макета. Предмет кладётся в (x + 4, y + 4). */
    public static void iconTile(GuiGraphics g, Theme t, int x, int y) {
        Draw.roundBox(g, x, y, 23, 23, 5f, t.c("slot.bg"), t.c("card.border"));
    }

    /** Заголовок и подзаголовок шапки. */
    public static void headerText(GuiGraphics g, Theme t, String title, String subtitle, float x, int cardY, float room) {
        Draw.label(g, Draw.ellipsize(title, room, 20, Weight.SEMIBOLD), x, cardY + 15f, 20, Weight.SEMIBOLD, t.c("text.primary"));
        if (!subtitle.isEmpty()) Draw.label(g, Draw.ellipsize(subtitle, room, 12, Weight.REGULAR), x, cardY + 26f, 12, Weight.REGULAR, t.c("text.muted"));
    }

    public static void iconButton(GuiGraphics g, Theme t, int x, int y, int size, boolean hot) {
        if (t.beveled()) {
            t.button(g, x, y, size, size, hot, false);
            return;
        }
        Draw.roundBox(g, x, y, size, size, 4.5f, hot ? Draw.mix(t.c("button.bg"), t.c("text.primary"), 0.08f) : t.c("button.bg"), t.c("button.border"));
    }

    /** Кнопка «закрыть» 18×18. */
    public static void close(GuiGraphics g, Theme t, Hits hits, int x, int y, int mx, int my, Runnable action) {
        boolean hot = over(mx, my, x, y, 18, 18);
        iconButton(g, t, x, y, 18, hot);
        Draw.cross(g, x + 5, y + 5, hot ? t.c("accent") : t.c("button.fg"));
        hits.add(x, y, 18, 18, b -> action.run());
    }

    public static float buttonWidth(String label, float px) {
        return Draw.labelWidth(label, px, Weight.SEMIBOLD) + 16;
    }

    /** Кнопка действия: главная янтарная (кегль 13, полужирный), обычная серая (кегль 12). */
    public static void button(GuiGraphics g, Theme t, Hits hits, int x, int y, int w, int h, String label, boolean primary, int mx, int my, Runnable action) {
        boolean hot = over(mx, my, x, y, w, h);
        if (t.beveled()) {
            t.button(g, x, y, w, h, hot, primary);
        } else if (primary) {
            Draw.round(g, x, y, w, h, 4.5f, hot ? Draw.mix(t.c("accent"), 0xFFFFFFFF, 0.15f) : t.c("accent"));
        } else {
            Draw.roundBox(g, x, y, w, h, 4.5f, hot ? Draw.mix(t.c("button.bg"), t.c("text.primary"), 0.08f) : t.c("button.bg"), t.c("button.border"));
        }
        float px = primary ? 13 : 12;
        Weight weight = primary ? Weight.SEMIBOLD : Weight.REGULAR;
        float tw = Draw.labelWidth(label, px, weight);
        Draw.label(g, label, x + (w - tw) / 2f, y + h / 2f, px, weight, primary ? t.c("accent.text") : t.c("button.fg"));
        hits.add(x, y, w, h, b -> {
            if (b == 0) action.run();
        });
    }

    /** Вкладка раздела: подпись, число (меньше нуля, если не нужно), пустое действие делает её неактивной. */
    public record Tab(String label, int count, boolean active, @Nullable Runnable action) {}

    private static final float TAB_PX = 12f;
    private static final float TAB_PAD_MIN = 3f, TAB_PAD_MAX = 8f;
    private static final float TAB_NUMBER_GAP = 3f;
    private static final int TAB_GAP = 2;

    private static float tabText(Tab tab, boolean numbers) {
        float w = Draw.labelWidth(tab.label(), TAB_PX, Weight.SEMIBOLD);
        return numbers && tab.count() >= 0 ? w + TAB_NUMBER_GAP + Draw.labelWidth(Integer.toString(tab.count()), TAB_PX, Weight.REGULAR) : w;
    }

    private static float tabPad(List<Tab> tabs, boolean numbers, int room) {
        float text = 0;
        for (Tab tab : tabs) text += tabText(tab, numbers);
        return Math.min(TAB_PAD_MAX, (room - text - TAB_GAP * (tabs.size() - 1f)) / (tabs.size() * 2f));
    }

    /**
     * Вкладки раздела окна: текст и линия под активной, без заливки, все в одну строку на ширину {@code room}.
     * Если не помещаются, сначала сужаются отступы, потом убираются числа.
     */
    public static void tabs(GuiGraphics g, Theme t, Hits hits, int x, int y, int h, int room, List<Tab> tabs, int mx, int my) {
        float pad = tabPad(tabs, true, room);
        boolean numbers = pad >= TAB_PAD_MIN;
        if (!numbers) pad = Math.max(TAB_PAD_MIN, tabPad(tabs, false, room));
        int left = x, right = x + room;
        float cy = y + (h - 1) / 2f;
        for (Tab tab : tabs) {
            int w = Math.round(tabText(tab, numbers) + pad * 2);
            if (left + w > right + 1) return;
            Runnable action = tab.action();
            boolean hot = action != null && over(mx, my, left, y, w, h);
            t.tabLine(g, left, y, w, h, tab.active(), hot);
            float tx = left + pad;
            Draw.label(g, tab.label(), tx, cy, TAB_PX, tab.active() ? Weight.SEMIBOLD : Weight.REGULAR, action == null ? t.c("text.dim") : t.tabLineFg(tab.active(), hot));
            if (numbers && tab.count() >= 0) {
                float nx = tx + Draw.labelWidth(tab.label(), TAB_PX, Weight.SEMIBOLD) + TAB_NUMBER_GAP;
                Draw.label(g, Integer.toString(tab.count()), nx, cy, TAB_PX, Weight.REGULAR, t.c("text.dim"));
            }
            if (action != null) hits.add(left, y, w, h, b -> action.run());
            left += w + TAB_GAP;
        }
    }

    /** Одна вкладка раздела. Возвращает ширину. Без действия ({@code action == null}) вкладка неактивна: тусклая и не нажимается. */
    public static int sectionTab(GuiGraphics g, Theme t, Hits hits, int x, int y, int h, String label, boolean active, int mx, int my, @Nullable Runnable action) {
        int w = (int) Math.ceil(Draw.labelWidth(label, TAB_PX, Weight.SEMIBOLD)) + 12;
        boolean hot = action != null && over(mx, my, x, y, w, h);
        t.tabLine(g, x, y, w, h, active, hot);
        int fg = action == null ? t.c("text.dim") : t.tabLineFg(active, hot);
        Draw.label(g, label, x + 6, y + (h - 1) / 2f, TAB_PX, active ? Weight.SEMIBOLD : Weight.REGULAR, fg);
        if (action != null) hits.add(x, y, w, h, b -> action.run());
        return w;
    }

    /** Состояние не только цветом: полный круг, половина круга, крест. */
    /** ORDER: своего нет, но недостающее можно заказать (автокрафт сети AE2). */
    public enum State { FULL, PARTIAL, NONE, ORDER }

    /** Рисует индикатор состояния. Возвращает true под курсором: вызывающий показывает подсказку. */
    public static boolean indicator(GuiGraphics g, Theme t, float x, float y, float size, State state, int mx, int my) {
        Icons.Shape shape = switch (state) {
            case FULL -> Icons.Shape.DOT;
            case PARTIAL -> Icons.Shape.HALF_DOT;
            case NONE -> Icons.Shape.CROSS;
            case ORDER -> Icons.Shape.GEAR;
        };
        int color = t.c(switch (state) {
            case FULL -> "state.ok";
            case PARTIAL -> "state.warn";
            case NONE -> "state.bad";
            case ORDER -> "state.order";
        });
        Icons.draw(g, shape, x, y, size, color);
        return over(mx, my, (int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(size) + 1, (int) Math.ceil(size) + 1);
    }

    /** Вкладка-фильтр: серая плашка у активной, кегль 12.5. Возвращает ширину. */
    public static int pill(GuiGraphics g, Theme t, Hits hits, int x, int y, String label, boolean active, int mx, int my, Runnable action) {
        Weight weight = active ? Weight.MEDIUM : Weight.REGULAR;
        int w = (int) Math.ceil(Draw.labelWidth(label, 12.5f, weight)) + 12, h = 14;
        boolean hot = over(mx, my, x, y, w, h);
        if (active) Draw.round(g, x, y, w, h, 4f, t.c("filter.active.bg"));
        else if (hot) Draw.round(g, x, y, w, h, 4f, Draw.alpha(t.c("filter.active.bg"), 0.5f));
        Draw.label(g, label, x + 6, y + h / 2f, 12.5f, weight, active ? t.c("text.primary") : t.c("text.muted"));
        hits.add(x, y, w, h, b -> action.run());
        return w;
    }

    /** Заглавная подпись с разрядкой, кегль и разрядка из макета (11 и 0.08em дают 11 и 0.44). */
    public static void caps(GuiGraphics g, Theme t, String text, float x, float centerY, float px, float spacing) {
        capsColored(g, text, x, centerY, px, spacing, t.c("text.dim"));
    }

    public static void capsColored(GuiGraphics g, String text, float x, float centerY, float px, float spacing, int color) {
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        float pen = x;
        for (int i = 0; i < upper.length(); ) {
            int cp = upper.codePointAt(i);
            String ch = Character.toString(cp);
            i += Character.charCount(cp);
            Draw.label(g, ch, pen, centerY, px, Weight.MEDIUM, color);
            pen += Draw.labelWidth(ch, px, Weight.MEDIUM) + spacing;
        }
    }

    public static float capsWidth(String text, float px, float spacing) {
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        return Draw.labelWidth(upper, px, Weight.MEDIUM) + spacing * upper.codePointCount(0, upper.length());
    }

    /** Подсказка в стиле темы: кегль 12, перенос по ширине. Для описания предметов нужна подсказка игры. */
    public static void tooltip(GuiGraphics g, Theme t, List<String> lines, int mx, int my) {
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) wrapped.addAll(Draw.wrapLabel(line, 180, 12, Weight.REGULAR, 6));
        float textW = 0;
        for (String line : wrapped) textW = Math.max(textW, Draw.labelWidth(line, 12, Weight.REGULAR));
        int w = (int) Math.ceil(textW) + 10, h = wrapped.size() * 8 + 7;
        int x = mx + 8, y = my - 4;
        if (x + w > g.guiWidth() - 4) x = mx - 8 - w;
        if (x < 4) x = 4;
        if (y + h > g.guiHeight() - 4) y = g.guiHeight() - 4 - h;
        if (y < 4) y = 4;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        t.tooltipBox(g, x, y, w, h);
        float cy = y + 7.5f;
        for (String line : wrapped) {
            Draw.label(g, line, x + 5, cy, 12, Weight.REGULAR, t.c("text.body"));
            cy += 8;
        }
        g.pose().popPose();
    }

    /** Подпись раздела, кегль 12. */
    public static void section(GuiGraphics g, Theme t, String label, float x, float centerY) {
        Draw.label(g, label, x, centerY, 12, Weight.REGULAR, t.c("text.muted"));
    }

    /** Счётчик: минус, значение, плюс. Ширина w, высота 18. Значение колесом меняет сам экран. */
    public static void stepper(GuiGraphics g, Theme t, Hits hits, int x, int y, int w, String value, int mx, int my, Runnable minus, Runnable plus) {
        iconButton(g, t, x, y, 18, over(mx, my, x, y, 18, 18));
        Draw.round(g, x + 5.5f, y + 8.5f, 7, 1f, 0.02f, t.c("button.fg"));
        int px = x + w - 18;
        iconButton(g, t, px, y, 18, over(mx, my, px, y, 18, 18));
        Draw.round(g, px + 5.5f, y + 8.5f, 7, 1f, 0.02f, t.c("button.fg"));
        Draw.round(g, px + 8.5f, y + 5.5f, 1f, 7, 0.02f, t.c("button.fg"));
        float tw = Draw.labelWidth(value, 13, Weight.MEDIUM);
        Draw.label(g, value, x + (w - tw) / 2f, y + 9f, 13, Weight.MEDIUM, t.c("text.primary"));
        hits.add(x, y, 18, 18, b -> minus.run());
        hits.add(px, y, 18, 18, b -> plus.run());
    }

    /** Переключатель 18×10. */
    public static void toggle(GuiGraphics g, Theme t, int x, int y, boolean on, boolean hot) {
        int fill = on ? t.c("state.ok") : t.c("slot.bg");
        if (hot) fill = Draw.mix(fill, t.c("text.primary"), 0.08f);
        Draw.round(g, x, y, 18, 10, 5f, fill);
        Draw.dot(g, on ? x + 9 : x + 1, y + 1, 8, on ? t.c("accent.text") : t.c("text.muted"));
    }

    /** Плашка предмета со счётчиком, 20×20. Сам предмет кладётся в (x + 2, y + 2) до вызова {@link #count}. */
    public static void tile(GuiGraphics g, Theme t, int x, int y, boolean hot) {
        Draw.roundBox(g, x, y, 20, 20, 3.5f, t.c("cell.bg"), hot ? t.c("accent") : t.c("cell.border"));
    }

    /** Число в углу плашки, поверх предмета. */
    public static void count(GuiGraphics g, String text, int x, int y) {
        float w = Draw.labelWidth(text, 10, Weight.SEMIBOLD);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        Draw.label(g, text, x + 19 - w, y + 16f, 10, Weight.SEMIBOLD, 0xFFFFFFFF);
        g.pose().popPose();
    }

    // --- 1.9.0: выпадающий список, окно-вопрос, панелька по наведению, флажок, выбор «один из» ---

    /** Высота слоя поверх окна: раскрытые списки, панельки и окна-вопросы лежат над предметами и подсказками окна. */
    private static final float OVERLAY_Z = 400f;

    private static float chipRadius(Theme t, float h) {
        if (t.beveled()) return 0;
        if (t.pillTabs) return h / 2f;
        return Math.min(h / 2f, t.m("chip.radius", 6f) / 2f);
    }

    /** Строка выпадающего списка: подпись, счёт справа (пустой — без счёта), неактивная строка тусклая и не выбирается. */
    public record Option(String label, String count, boolean enabled) {
        public Option(String label) {
            this(label, "", true);
        }

        public Option(String label, int count) {
            this(label, count < 0 ? "" : Integer.toString(count), true);
        }
    }

    /** Состояние выпадающего списка: экран хранит его между кадрами. */
    public static final class Dropdown {
        public boolean open;
        private int x, y, w, h;
        private final Scroller scroller = new Scroller();
        private int listX, listY, listW, listH;

        public void close() {
            open = false;
        }

        /** Esc закрывает раскрытый список. Возвращает true, если клавиша съедена. */
        public boolean keyPressed(int key) {
            if (open && key == 256) {
                open = false;
                return true;
            }
            return false;
        }

        /** Колесо над раскрытым списком листает его. Возвращает true, если колесо съедено. */
        public boolean scrolled(double mx, double my, double delta) {
            if (!open || !over(mx, my, listX, listY, listW, listH)) return false;
            scroller.by((float) (-delta * DROP_ROW * 2));
            return true;
        }
    }

    public static final int DROP_H = 14;
    private static final float DROP_PX = 12.5f;
    private static final float DROP_ROW = 15.5f;

    /** Ширина кнопки выпадающего списка с подписью {@code label}, не больше {@code maxW}. */
    public static int dropdownWidth(String label, int maxW) {
        return Math.min(maxW, (int) Math.ceil(5.5f + Draw.labelWidth(label, DROP_PX, Weight.REGULAR) + 3 + 6.5f + 4));
    }

    /**
     * Кнопка выпадающего списка (макет {@code .dd}): подпись выбранного и шеврон, высота {@link #DROP_H}.
     * {@code highlighted} — выбрано не значение по умолчанию: светлая рамка. Клик открывает и закрывает список.
     * Раскрытый список рисует {@link #dropdownList} в конце кадра. Возвращает ширину.
     */
    public static int dropdown(GuiGraphics g, Theme t, Hits hits, Dropdown d, int x, int y, int maxW, String label, boolean highlighted, int mx, int my) {
        int w = dropdownWidth(label, maxW), h = DROP_H;
        boolean hot = over(mx, my, x, y, w, h);
        boolean on = highlighted || d.open;
        float r = chipRadius(t, h);
        int border = on ? t.c("text.secondary") : hot ? t.c("text.muted") : t.c("panel.border");
        if (t.beveled()) {
            t.button(g, x, y, w, h, hot, false);
        } else {
            if (hot) Draw.round(g, x, y, w, h, r, Draw.alpha(t.c("filter.active.bg"), 0.5f));
            Draw.roundBorder(g, x, y, w, h, r, border);
        }
        int fg = on || hot ? t.c("text.primary") : t.c("text.secondary");
        float room = w - 5.5f - 3 - 6.5f - 4;
        String text = Draw.ellipsize(label, room, DROP_PX, Weight.REGULAR);
        Draw.label(g, text, x + 5.5f, y + h / 2f, DROP_PX, Weight.REGULAR, fg);
        Icons.draw(g, Icons.Shape.CHEVRON_DOWN, x + w - 4 - 6.5f, y + (h - 6.5f) / 2f, 6.5f, fg);
        d.x = x;
        d.y = y;
        d.w = w;
        d.h = h;
        hits.add(x, y, w, h, b -> {
            if (b == 0) d.open = !d.open;
        });
        return w;
    }

    /**
     * Раскрытый список под кнопкой {@link #dropdown}: отметка у выбранного, счёт справа, прокрутка колесом.
     * Вызывать последним в кадре: список лежит поверх всего и его нажатия тоже. Клик мимо закрывает список.
     * {@code listW} — ширина списка (не уже кнопки), {@code pick} получает номер выбранной строки.
     */
    public static void dropdownList(GuiGraphics g, Theme t, Hits hits, Dropdown d, List<Option> options, int selected, int listW, int mx, int my, IntConsumer pick) {
        if (!d.open) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int w = Math.min(Math.max(listW, d.w), sw - 8);
        int pad = 4;
        int full = Math.round(options.size() * DROP_ROW + pad * 2);
        int top = d.y + d.h + 3;
        int roomBelow = sh - 4 - top, roomAbove = d.y - 3 - 4;
        boolean below = full <= roomBelow || roomBelow >= roomAbove;
        int h = Math.min(full, below ? roomBelow : roomAbove);
        int y = below ? top : d.y - 3 - h;
        // правый край по кнопке, если список шире; не за краем экрана
        int x = w > d.w ? d.x + d.w - w : d.x;
        x = Math.max(4, Math.min(x, sw - 4 - w));
        d.listX = x;
        d.listY = y;
        d.listW = w;
        d.listH = h;
        float scroll = d.scroller.update(Math.max(0, full - h));
        // клик мимо списка закрывает его и дальше не идёт
        hits.unclip();
        hits.add(0, 0, sw, sh, b -> d.open = false);
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);
        popFrame(g, t, x, y, w, h);
        Draw.scissor(g, x, y + 1, x + w, y + h - 1);
        hits.clip(y + 1, y + h - 1);
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            float ry = y + pad + i * DROP_ROW - scroll;
            if (ry + DROP_ROW < y || ry > y + h) continue;
            int rx = x + pad, rw = w - pad * 2, rh = 15;
            boolean sel = i == selected;
            boolean hot = o.enabled() && over(mx, my, rx, Math.round(ry), rw, rh) && over(mx, my, x, y, w, h);
            float rr = t.beveled() ? 0 : 3.5f;
            if (sel) Draw.round(g, rx, ry, rw, rh, rr, t.c("row.selected.bg"));
            else if (hot) Draw.round(g, rx, ry, rw, rh, rr, Draw.alpha(t.c("row.selected.bg"), 0.5f));
            int fg = sel ? t.c("text.primary") : t.c("text.body");
            if (!o.enabled()) fg = Draw.alpha(fg, 0.5f);
            if (sel) Icons.draw(g, Icons.Shape.CHECK, rx + 4, ry + (rh - 6.5f) / 2f, 6.5f, t.c("text.primary"));
            float countW = o.count().isEmpty() ? 0 : Draw.labelWidth(o.count(), 11.5f, Weight.REGULAR);
            float lx = rx + 4 + 6.5f + 4;
            float room = rx + rw - 4 - (countW > 0 ? countW + 6 : 0) - lx;
            Weight weight = sel ? Weight.SEMIBOLD : Weight.REGULAR;
            Draw.label(g, Draw.ellipsize(o.label(), room, DROP_PX, weight), lx, ry + rh / 2f, DROP_PX, weight, fg);
            if (countW > 0) {
                int cc = o.enabled() ? t.c("text.muted") : Draw.alpha(t.c("text.muted"), 0.5f);
                Draw.label(g, o.count(), rx + rw - 4 - countW, ry + rh / 2f, 11.5f, Weight.REGULAR, cc);
            }
            if (o.enabled()) {
                int index = i;
                hits.add(rx, Math.round(ry), rw, rh, b -> {
                    if (b != 0) return;
                    d.open = false;
                    pick.accept(index);
                });
            }
        }
        hits.unclip();
        g.disableScissor();
        g.pose().popPose();
    }

    /** Рамка всплывающего блока (макет {@code .pop}): подложка боковой панели, рамка кнопки, мягкая тень. */
    private static void popFrame(GuiGraphics g, Theme t, int x, int y, int w, int h) {
        if (t.beveled()) {
            // у ванильной темы подсказка тёмная, а текст окна тёмный: всплывающее рисуется панелью
            t.panel(g, x, y, w, h);
            return;
        }
        float r = 6f;
        if (t.shadow) Draw.shadow(g, x, y, w, h, r, 0.7f);
        Draw.roundBox(g, x, y, w, h, r, t.c("sidebar.bg"), t.c("button.border"));
    }

    // --- панелька по наведению ---

    /** С какой стороны якоря ставить панельку. Если места нет, она уходит на противоположную сторону. */
    public enum Side { BELOW, ABOVE, RIGHT, LEFT }

    /** Содержимое панельки: прямоугольник всего блока, отступ внутри {@link #POP_PAD_X}×{@link #POP_PAD_Y}. */
    @FunctionalInterface
    public interface PopoverBody {
        void draw(GuiGraphics g, int x, int y, int w, int h);
    }

    public static final int POP_PAD_X = 7, POP_PAD_Y = 6;

    /**
     * Панелька по наведению с любым содержимым (макет {@code .pop}): подсказка главы, легенда, список глав.
     * Ставится со стороны {@code side} от якоря (x, y, w, h) с зазором 4 и не выходит за {@code bounds}
     * ({@code {x, y, w, h}}, {@code null} — экран). Рисовать в конце кадра. Возвращает место блока {@code {x, y, w, h}}:
     * экран держит панельку открытой, пока курсор над якорем или над ней.
     */
    public static int[] popover(GuiGraphics g, Theme t, int ax, int ay, int aw, int ah, int w, int h, Side side, int @Nullable [] bounds, PopoverBody body) {
        int[] box = placePopover(ax, ay, aw, ah, w, h, side, bounds != null ? bounds : new int[] {0, 0, g.guiWidth(), g.guiHeight()});
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);
        popFrame(g, t, box[0], box[1], box[2], box[3]);
        body.draw(g, box[0], box[1], box[2], box[3]);
        g.pose().popPose();
        return box;
    }

    /** Место панельки без отрисовки: экран может заранее знать, где она встанет. */
    public static int[] placePopover(int ax, int ay, int aw, int ah, int w, int h, Side side, int[] bounds) {
        int gap = 4, margin = 4;
        int left = bounds[0] + margin, top = bounds[1] + margin, right = bounds[0] + bounds[2] - margin, bottom = bounds[1] + bounds[3] - margin;
        w = Math.min(w, right - left);
        h = Math.min(h, bottom - top);
        int x, y;
        if (side == Side.BELOW || side == Side.ABOVE) {
            boolean below = side == Side.BELOW;
            if (below && ay + ah + gap + h > bottom && ay - gap - h >= top) below = false;
            else if (!below && ay - gap - h < top && ay + ah + gap + h <= bottom) below = true;
            y = below ? ay + ah + gap : ay - gap - h;
            x = ax;
        } else {
            boolean rightSide = side == Side.RIGHT;
            if (rightSide && ax + aw + gap + w > right && ax - gap - w >= left) rightSide = false;
            else if (!rightSide && ax - gap - w < left && ax + aw + gap + w <= right) rightSide = true;
            x = rightSide ? ax + aw + gap : ax - gap - w;
            y = ay;
        }
        x = Math.max(left, Math.min(x, right - w));
        y = Math.max(top, Math.min(y, bottom - h));
        return new int[] {x, y, w, h};
    }

    /** Заголовок панельки: кегль 14, заголовочное начертание. */
    public static void popoverTitle(GuiGraphics g, Theme t, String text, float x, float centerY) {
        Draw.label(g, text, x, centerY, 14, Weight.DISPLAY, t.c("text.primary"));
    }

    // --- окно-вопрос ---

    /** Содержимое окна-вопроса под текстом: строки со значками, список и т. п. */
    @FunctionalInterface
    public interface DialogBody {
        void draw(GuiGraphics g, int x, int y, int w, int mx, int my);
    }

    /**
     * Окно-вопрос (макеты {@code 10}, {@code 33b}): затемнение, заголовок, текст, до трёх кнопок.
     * {@link #no} — ответ «нет», его же даёт Esc; {@link #yes} — главная янтарная кнопка справа.
     * При трёх кнопках «нет» стоит слева, две другие справа. Экран хранит окно, пока оно открыто, и рисует его последним.
     */
    public static final class Dialog {
        private record Answer(String label, boolean primary, Runnable action) {}

        public final String title;
        private final List<String> text = new ArrayList<>();
        private final List<Answer> answers = new ArrayList<>();
        @Nullable
        private Answer no;
        @Nullable
        private DialogBody body;
        private int bodyH;
        private int width = 230;

        public Dialog(String title) {
            this.title = title;
        }

        /** Абзац текста, переносится по ширине окна. */
        public Dialog text(String paragraph) {
            text.add(paragraph);
            return this;
        }

        /** Своё содержимое высотой {@code height} под текстом. */
        public Dialog body(int height, DialogBody body) {
            this.body = body;
            this.bodyH = height;
            return this;
        }

        /** Ответ «нет»: кнопка и Esc. */
        public Dialog no(String label, Runnable action) {
            no = new Answer(label, false, action);
            return this;
        }

        /** Обычная кнопка между «нет» и главной. */
        public Dialog button(String label, Runnable action) {
            answers.add(new Answer(label, false, action));
            return this;
        }

        /** Главное действие: янтарная кнопка справа. */
        public Dialog yes(String label, Runnable action) {
            answers.add(new Answer(label, true, action));
            return this;
        }

        /** Ширина окна в единицах, по умолчанию 230 (460 пикселей макета). */
        public Dialog width(int width) {
            this.width = width;
            return this;
        }

        /** Esc отвечает «нет». Всегда true: пока окно открыто, клавиши дальше не идут. */
        public boolean keyPressed(int key) {
            if (key == 256 && no != null) no.action.run();
            return true;
        }

        private List<Answer> buttons() {
            List<Answer> out = new ArrayList<>();
            if (no != null) out.add(no);
            List<Answer> rest = new ArrayList<>(answers);
            rest.sort((a, b) -> Boolean.compare(a.primary, b.primary));
            out.addAll(rest);
            return out.size() > 3 ? out.subList(0, 3) : out;
        }
    }

    private static final float DIALOG_TEXT_PX = 12.5f, DIALOG_LINE = 9f;

    /** Рисует окно-вопрос по центру экрана поверх затемнения. Пока оно на экране, нажатия мимо кнопок никуда не идут. */
    public static void dialog(GuiGraphics g, Theme t, Hits hits, Dialog d, int mx, int my) {
        int sw = g.guiWidth(), sh = g.guiHeight();
        int padX = 10, padY = 9, gap = 5;
        int w = Math.min(d.width, sw - 16);
        int inner = w - padX * 2;
        List<String> lines = new ArrayList<>();
        List<Integer> breaks = new ArrayList<>();
        for (String p : d.text) {
            if (!lines.isEmpty()) breaks.add(lines.size());
            lines.addAll(Draw.wrapLabel(p, inner, DIALOG_TEXT_PX, Weight.REGULAR, 12));
        }
        int titleH = 11;
        int textH = lines.isEmpty() ? 0 : Math.round(lines.size() * DIALOG_LINE + breaks.size() * 3);
        int buttonsH = 17;
        int h = padY + titleH + (textH > 0 ? gap + textH : 0) + (d.body != null ? gap + d.bodyH : 0) + gap + 3 + buttonsH + padY;
        int x = (sw - w) / 2, y = Math.max(4, (sh - h) / 2);
        hits.unclip();
        hits.add(0, 0, sw, sh, b -> {});
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);
        Draw.rect(g, 0, 0, sw, sh, t.c("overlay"));
        popFrame(g, t, x, y, w, h);
        float cy = y + padY + titleH / 2f;
        Draw.label(g, Draw.ellipsize(d.title, inner, 17, Weight.DISPLAY), x + padX, cy, 17, Weight.DISPLAY, t.c("text.primary"));
        float ty = y + padY + titleH + gap;
        for (int i = 0; i < lines.size(); i++) {
            if (breaks.contains(i)) ty += 3;
            Draw.label(g, lines.get(i), x + padX, ty + DIALOG_LINE / 2f, DIALOG_TEXT_PX, Weight.REGULAR, t.c("text.body"));
            ty += DIALOG_LINE;
        }
        int by = y + h - padY - buttonsH;
        if (d.body != null) d.body.draw(g, x + padX, by - 3 - gap - d.bodyH, inner, mx, my);
        List<Dialog.Answer> buttons = d.buttons();
        int right = x + w - padX;
        int count = buttons.size();
        for (int i = count - 1; i >= 0; i--) {
            Dialog.Answer a = buttons.get(i);
            int bw = (int) Math.ceil(Draw.labelWidth(a.label(), a.primary() ? 13 : 12, a.primary() ? Weight.SEMIBOLD : Weight.REGULAR)) + 14;
            boolean leftAligned = count == 3 && i == 0;
            int bx = leftAligned ? x + padX : right - bw;
            button(g, t, hits, bx, by, bw, buttonsH, a.label(), a.primary(), mx, my, a.action());
            if (!leftAligned) right = bx - 4;
        }
        g.pose().popPose();
    }

    // --- флажок и выбор «один из» ---

    /**
     * Флажок (макет {@code .cb}): квадрат {@code size} (обычно 9) со скруглением, отмеченный залит {@code state.ok}
     * с галочкой. {@code round} — круглый, как в «Первых шагах». Без действия флажок тусклый и не нажимается.
     * Возвращает true под курсором.
     */
    public static boolean checkbox(GuiGraphics g, Theme t, Hits hits, int x, int y, int size, boolean checked, boolean round, int mx, int my, @Nullable Runnable toggle) {
        boolean hot = toggle != null && over(mx, my, x, y, size, size);
        float r = round ? size / 2f : t.beveled() ? 0 : size * 5f / 18f;
        float border = Math.max(0.5f, size / 12f);
        int ok = t.c("state.ok");
        float k = toggle == null ? 0.5f : 1f;
        if (t.beveled() && !round) {
            Draw.bevel(g, x, y, size, size, t.c("slot.bg"), t.c("slot.bevel.dark"), t.c("slot.bevel.light"), 1, true);
            if (checked) Icons.draw(g, Icons.Shape.CHECK, x + 1, y + 1, size - 2, Draw.alpha(ok, k));
            else if (hot) Draw.rect(g, x + 1, y + 1, size - 2, size - 2, 0x40FFFFFF);
        } else if (checked) {
            Draw.round(g, x, y, size, size, r, Draw.alpha(hot ? Draw.mix(ok, 0xFFFFFFFF, 0.12f) : ok, k));
            Icons.draw(g, Icons.Shape.CHECK, x + size * 0.12f, y + size * 0.12f, size * 0.76f, Draw.alpha(t.c("accent.text"), k));
        } else {
            Draw.roundBorder(g, x, y, size, size, r, Draw.alpha(hot ? t.c("text.secondary") : t.c("text.dim"), k), border);
        }
        if (toggle != null) hits.add(x, y, size, size, b -> {
            if (b == 0) toggle.run();
        });
        return hot;
    }

    /** Квадратный флажок 9×9. */
    public static boolean checkbox(GuiGraphics g, Theme t, Hits hits, int x, int y, boolean checked, int mx, int my, @Nullable Runnable toggle) {
        return checkbox(g, t, hits, x, y, 9, checked, false, mx, my, toggle);
    }

    private static void radioMark(GuiGraphics g, Theme t, float cx, float cy, int size, boolean selected, boolean hot, float k) {
        int ring = selected ? t.c("text.primary") : hot ? t.c("text.secondary") : t.c("text.dim");
        Draw.ring(g, cx, cy, size / 2f, Math.max(0.5f, size / 9.5f), Draw.alpha(ring, k));
        if (selected) Draw.disc(g, cx, cy, size * 0.28f, Draw.alpha(t.c("text.primary"), k));
    }

    /**
     * Кружок выбора «один из» (макет {@code .rd}): кольцо {@code size} (обычно 7 или 8), у выбранного светлое кольцо
     * и точка в середине. Без действия тусклый. Возвращает true под курсором.
     */
    public static boolean radio(GuiGraphics g, Theme t, Hits hits, int x, int y, int size, boolean selected, int mx, int my, @Nullable Runnable select) {
        boolean hot = select != null && over(mx, my, x, y, size, size);
        radioMark(g, t, x + size / 2f, y + size / 2f, size, selected, hot, select == null ? 0.5f : 1f);
        if (select != null) hits.add(x, y, size, size, b -> {
            if (b == 0) select.run();
        });
        return hot;
    }

    /**
     * Строка выбора «один из» (макеты {@code 09a}, {@code 21}): рамка карточки на всю строку и кружок справа,
     * у выбранной светлая подложка и рамка. Содержимое (значок, подписи) рисует экран поверх. Нажимается вся строка.
     * Возвращает true под курсором.
     */
    public static boolean radioRow(GuiGraphics g, Theme t, Hits hits, int x, int y, int w, int h, boolean selected, int mx, int my, Runnable select) {
        boolean hot = over(mx, my, x, y, w, h);
        if (t.beveled()) {
            int fill = selected ? Draw.mix(t.c("card.bg"), 0xFFFFFFFF, 0.2f) : t.c("card.bg");
            Draw.bevel(g, x, y, w, h, fill, t.c("panel.bevel.light"), t.c("panel.bevel.dark"), 1, false);
        } else {
            int fill = selected ? t.c("row.selected.bg") : hot ? Draw.mix(t.c("card.bg"), t.c("text.primary"), 0.04f) : t.c("card.bg");
            Draw.roundBox(g, x, y, w, h, 5f, fill, selected ? t.c("text.muted") : t.c("panel.border"));
        }
        int size = 7;
        radioMark(g, t, x + w - 6 - size / 2f, y + h / 2f, size, selected, hot, 1f);
        hits.add(x, y, w, h, b -> {
            if (b == 0) select.run();
        });
        return hot;
    }
}
