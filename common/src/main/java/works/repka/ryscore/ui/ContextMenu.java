package works.repka.ryscore.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.ui.Type.Weight;

/**
 * Меню по правой кнопке с подменю (1.15.4, отзыв автора о карте 19.11: четыре пинга в один пункт «Пинг ›»). Строка 13,
 * значок 8, подпись 12, справа подсказка мелким («запрос») или стрелка у пункта с подменю. Подменю раскрывается по
 * наведению или клику справа от строки, у края экрана слева. Тонкий разделитель между группами пунктов.
 *
 * <p>Экран хранит меню, пока оно открыто, рисует его последним ({@link #draw}) и отдаёт ему клики ({@link #click}):
 * ложь значит «закрыть». Клик по пункту выполняет его и закрывает меню, клик по пункту с подменю раскрывает подменю,
 * клик мимо закрывает.
 */
public final class ContextMenu {
    public static final int ROW = 13, PAD = 3, DIVIDER = 5, HEAD = 12;
    private static final float Z = 400f;

    /** Пункт меню: обычный, с подменю или разделитель. */
    public static final class Item {
        final @Nullable Icons.Shape icon;
        final String label, hint;
        final boolean enabled;
        final @Nullable Runnable action;
        final List<Item> children;
        final boolean divider;

        private Item(@Nullable Icons.Shape icon, String label, String hint, boolean enabled, @Nullable Runnable action, List<Item> children, boolean divider) {
            this.icon = icon;
            this.label = label;
            this.hint = hint;
            this.enabled = enabled;
            this.action = action;
            this.children = children;
            this.divider = divider;
        }

        /** Обычный пункт; серый и без нажатия, если {@code enabled} ложь. */
        public static Item of(@Nullable Icons.Shape icon, String label, boolean enabled, Runnable action) {
            return new Item(icon, Objects.requireNonNull(label), "", enabled, Objects.requireNonNull(action), List.of(), false);
        }

        /** Пункт с подменю. Пустое подменю делает пункт серым. */
        public static Item sub(@Nullable Icons.Shape icon, String label, List<Item> children) {
            return new Item(icon, Objects.requireNonNull(label), "", !children.isEmpty(), null, List.copyOf(children), false);
        }

        /** Тонкая черта между группами пунктов. */
        public static Item divider() {
            return new Item(null, "", "", false, null, List.of(), true);
        }

        /** Тот же пункт с подсказкой справа мелким шрифтом. */
        public Item hint(String hint) {
            return new Item(icon, label, Objects.requireNonNull(hint), enabled, action, children, divider);
        }

        public boolean submenu() {
            return !children.isEmpty() || action == null && !divider;
        }

        public String label() { return label; }
        public List<Item> children() { return children; }
    }

    private final int ax, ay, width;
    private final @Nullable String title;
    private final List<Item> items;
    /** Раскрытое подменю: номер пункта или −1. */
    private int open = -1;
    private boolean done;
    /** Где меню и подменю легли в прошлом кадре: наведение раскрывает подменю. */
    private int x, y, h, subX, subY, subW, subH;

    /**
     * @param x, y  место клика, меню не выходит за экран
     * @param title заголовок серым над пунктами («Kot_Boris · Барсуки · 92 м») или null
     * @param width ширина меню
     */
    public ContextMenu(int x, int y, @Nullable String title, List<Item> items, int width) {
        this.ax = x;
        this.ay = y;
        this.title = title;
        this.items = new ArrayList<>(items);
        this.width = width;
        // разделители по краям и подряд не нужны
        while (!this.items.isEmpty() && this.items.get(0).divider) this.items.remove(0);
        while (!this.items.isEmpty() && this.items.get(this.items.size() - 1).divider) this.items.remove(this.items.size() - 1);
        for (int i = this.items.size() - 1; i > 0; i--) {
            if (this.items.get(i).divider && this.items.get(i - 1).divider) this.items.remove(i);
        }
    }

    public List<Item> items() {
        return items;
    }

    /** Номер раскрытого подменю или −1 (для автотестов). */
    public int openIndex() {
        return open;
    }

    /** Раскрыть подменю пункта (для автотестов и клавиатуры). */
    public void openSub(int index) {
        open = index >= 0 && index < items.size() && items.get(index).submenu() && items.get(index).enabled ? index : -1;
    }

    /** Пункт выполнен: экран закрывает меню. */
    public boolean done() {
        return done;
    }

    private static int height(List<Item> list) {
        int h = PAD * 2;
        for (Item it : list) h += it.divider ? DIVIDER : ROW;
        return h;
    }

    private static int subWidth(List<Item> list) {
        float w = 80;
        for (Item it : list) {
            float hw = it.hint.isEmpty() ? 0 : Draw.labelWidth(it.hint, 10.5f, Weight.REGULAR) + 6;
            w = Math.max(w, 18 + Draw.labelWidth(it.label, 12, Weight.REGULAR) + 8 + hw);
        }
        return (int) Math.ceil(Math.min(w, 180));
    }

    /** Нарисовать меню поверх всего; нажатия идут в {@code hits} последними, поэтому лежат сверху. */
    public void draw(GuiGraphics g, Theme t, Kit.Hits hits, int mx, int my) {
        int sw = g.guiWidth(), sh = g.guiHeight();
        int head = title != null ? HEAD : 0;
        h = height(items) + head;
        x = Math.max(4, Math.min(ax, sw - width - 4));
        y = Math.max(4, Math.min(ay, sh - h - 4));
        g.pose().pushPose();
        g.pose().translate(0, 0, Z);
        t.tooltipBox(g, x, y, width, h);
        if (title != null) {
            Draw.label(g, Draw.ellipsize(title, width - 12, 11, Weight.REGULAR), x + 6, y + 8, 11, Weight.REGULAR, t.c("text.muted"));
            Draw.rect(g, x + 4, y + head + 1, width - 8, 1, t.c("panel.border"));
        }
        // наведение: строка с подменю раскрывает его, обычная строка закрывает; над подменю всё как было
        boolean overSub = open >= 0 && Kit.over(mx, my, subX, subY, subW, subH);
        int ry = y + PAD + head;
        int openRowY = ry;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.divider) {
                ry += DIVIDER;
                continue;
            }
            if (!overSub && it.enabled && Kit.over(mx, my, x + 2, ry, width - 4, ROW)) open = it.submenu() ? i : -1;
            if (i == open) openRowY = ry;
            ry += ROW;
        }
        ry = y + PAD + head;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.divider) {
                Draw.rect(g, x + 6, ry + DIVIDER / 2, width - 12, 1, t.c("panel.border"));
                ry += DIVIDER;
                continue;
            }
            int index = i;
            row(g, t, hits, it, x, ry, width, mx, my, i == open, () -> openSub(index));
            ry += ROW;
        }
        if (open >= 0) {
            List<Item> sub = items.get(open).children;
            subW = subWidth(sub);
            subH = height(sub);
            subX = x + width - 2;
            if (subX + subW > sw - 4) subX = x - subW + 2;
            subY = Math.max(4, Math.min(openRowY - PAD, sh - subH - 4));
            g.pose().translate(0, 0, 1);
            t.tooltipBox(g, subX, subY, subW, subH);
            int sy = subY + PAD;
            for (Item it : sub) {
                if (it.divider) {
                    Draw.rect(g, subX + 6, sy + DIVIDER / 2, subW - 12, 1, t.c("panel.border"));
                    sy += DIVIDER;
                    continue;
                }
                row(g, t, hits, it, subX, sy, subW, mx, my, false, null);
                sy += ROW;
            }
        }
        g.pose().popPose();
    }

    private void row(GuiGraphics g, Theme t, Kit.Hits hits, Item it, int bx, int ry, int w, int mx, int my, boolean expanded, @Nullable Runnable expand) {
        boolean hot = it.enabled && (expanded || Kit.over(mx, my, bx + 2, ry, w - 4, ROW));
        if (hot) Draw.round(g, bx + 2, ry, w - 4, ROW, 3, t.c("slot.hover"));
        int color = it.enabled ? t.c("text.primary") : t.c("text.muted");
        if (it.icon != null) Icons.draw(g, it.icon, bx + 6, ry + 2.5f, 8, color);
        float right = 0;
        if (it.submenu()) {
            Icons.draw(g, Icons.Shape.CHEVRON_RIGHT, bx + w - 12, ry + 3.5f, 6, it.enabled ? t.c("text.muted") : t.c("text.dim"));
            right = 14;
        } else if (!it.hint.isEmpty()) {
            right = Draw.labelWidth(it.hint, 10.5f, Weight.REGULAR) + 6;
            Draw.label(g, it.hint, bx + w - right, ry + ROW / 2f, 10.5f, Weight.REGULAR, t.c("text.dim"));
        }
        Draw.label(g, Draw.ellipsize(it.label, w - 24 - right, 12, Weight.REGULAR), bx + 18, ry + ROW / 2f, 12, Weight.REGULAR, color);
        if (!it.enabled) {
            // серый пункт съедает клик: меню не закрывается от промаха по нему
            hits.add(bx + 2, ry, w - 4, ROW, b -> {});
        } else if (it.submenu()) {
            hits.add(bx + 2, ry, w - 4, ROW, b -> {
                if (expand != null) expand.run();
            });
        } else {
            hits.add(bx + 2, ry, w - 4, ROW, b -> {
                done = true;
                Objects.requireNonNull(it.action).run();
            });
        }
    }

    /**
     * Клик, пока меню открыто: true, если меню остаётся (раскрыто подменю, нажат серый пункт), ложь: закрыть (пункт
     * выполнен или клик мимо). Нажатия меню должны лежать в {@code hits} последними.
     */
    public boolean click(Kit.Hits hits, double mx, double my, int button) {
        boolean inside = Kit.over(mx, my, x, y, width, h) || open >= 0 && Kit.over(mx, my, subX, subY, subW, subH);
        // клик мимо только закрывает: кнопки окна под меню не нажимаются
        if (!inside) return false;
        hits.click(mx, my, button);
        return !done;
    }
}
