package works.repka.ryscore.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
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

    /** Вкладка раздела в одну строку, кегль 13. Возвращает ширину. */
    public static int sectionTab(GuiGraphics g, Theme t, Hits hits, int x, int y, int h, String label, boolean active, int mx, int my, Runnable action) {
        Weight weight = active ? Weight.MEDIUM : Weight.REGULAR;
        int w = (int) Math.ceil(Draw.labelWidth(label, 13, weight)) + 12;
        boolean hot = over(mx, my, x, y, w, h);
        t.tabLine(g, x, y, w, h, active, hot);
        Draw.label(g, label, x + 6, y + (h - 1) / 2f, 13, weight, active || hot ? t.c("text.primary") : t.c("text.muted"));
        hits.add(x, y, w, h, b -> action.run());
        return w;
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
}
