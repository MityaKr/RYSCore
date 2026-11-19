package works.repka.ryscore.action;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.ScaledScreen;
import works.repka.ryscore.ui.Scroller;
import works.repka.ryscore.ui.Type.Weight;

/**
 * Колесо быстрых действий (1.14, макеты {@code essentials/img/01_wheel.png}, {@code 02_wheel_places.png}).
 *
 * <ul>
 *   <li>держать Ё: мышь выбирает сектор, отпустить — выполнить, отпустить в середине — ничего;</li>
 *   <li>нажать и отпустить: колесо остаётся, выбор кликом, в середине «Настроить»;</li>
 *   <li>1–8 выбирают сектор, колесо мыши и Tab листают кольца, Esc закрывает;</li>
 *   <li>сектор-список («Места сервера…») открывает рядом список с поиском: при задержке мыши, по клику или по клавише.</li>
 * </ul>
 * Мир не на паузе, камера не крутится: курсор свободен, пока колесо открыто. Цвета колеса тёмные в любой теме (токены {@code wheel.*}).
 */
final class WheelScreen extends ScaledScreen {
    private static final float R = 88, IR = 36, GAP = 1.6f;
    private static final long HOLD_MS = 250, LIST_DELAY_MS = 450;
    private static final float LIST_W = 160, ROW_H = 17;

    private final List<Ring> rings;
    private int ring;
    private boolean holding;
    private final long openedAt = System.currentTimeMillis();
    private int hovered = -1, hoverSince = -1;
    private long hoverAt;
    private final Kit.Hits hits = new Kit.Hits();
    private @Nullable Action list;
    /** Открытый второй круг («Перенести…»), с 1.15. */
    private @Nullable Action nested;
    private String query = "";
    private int selected;
    private final Scroller scroll = new Scroller();
    private float listX, listY, listH;
    private List<Action> shownRows = List.of();

    private WheelScreen(boolean hold, @Nullable Action list) {
        super(Component.translatable("ryscore.wheel.title"));
        this.rings = Actions.availableRings();
        this.holding = hold;
        this.list = list;
        ResourceLocationIndex last = new ResourceLocationIndex();
        this.ring = last.find(rings, Actions.lastRing());
    }

    /** Открывает колесо; {@code hold}: клавиша ещё зажата и отпускание выполнит сектор. */
    static void open(boolean hold) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new WheelScreen(hold, null));
    }

    /** Колесо сразу на втором круге действия (своя клавиша на «Перенести…»). */
    static void openNested(Action action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WheelScreen w) {
            w.enter(action);
            return;
        }
        WheelScreen w = new WheelScreen(false, null);
        w.nested = action;
        mc.setScreen(w);
    }

    /** Колесо с открытым списком действия (своя клавиша на «Места сервера…»). */
    static void openList(Action action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WheelScreen w) {
            w.showList(action);
            return;
        }
        mc.setScreen(new WheelScreen(false, action));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Ring currentRing() {
        return rings.isEmpty() ? Actions.ringById(Ring.MINE) : rings.get(Math.floorMod(ring, rings.size()));
    }

    private List<@Nullable Action> slots() {
        if (nested != null) {
            List<Action> out = new ArrayList<>(nested.nested());
            while (out.size() > Ring.SECTORS) out.removeLast();
            while (out.size() < Ring.SECTORS) out.add(null);
            return out;
        }
        Ring r = currentRing();
        return r == null ? java.util.Collections.nCopies(Ring.SECTORS, null) : Actions.slots(r.id());
    }

    private float cx() {
        return list != null ? width / 2f - 85 : width / 2f;
    }

    private float cy() {
        return height / 2f - 6;
    }

    // --- ввод ---

    private boolean wheelKeyDown() {
        InputConstants.Key k = Binding.keyOf(Actions.WHEEL);
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (k.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, k.getValue()) == GLFW.GLFW_PRESS;
        return k.getValue() >= 0 && InputConstants.isKeyDown(window, k.getValue());
    }

    @Override
    public void tick() {
        if (!holding || wheelKeyDown()) return;
        holding = false;
        if (System.currentTimeMillis() - openedAt < HOLD_MS) return;
        // отпустили после удержания: выполнить сектор под мышью, в середине ничего
        if (hovered >= 0) {
            Action a = slots().get(hovered);
            if (a != null && a.opensList()) showList(a);
            else if (a != null && a.opensNested()) enter(a);
            else if (a != null && a.state().usable()) perform(a);
            else onClose();
        } else {
            onClose();
        }
    }

    private void perform(Action a) {
        if (a.opensList()) {
            showList(a);
            return;
        }
        if (a.opensNested()) {
            enter(a);
            return;
        }
        if (!a.state().usable()) return;
        Minecraft.getInstance().setScreen(null);
        Actions.run(a);
    }

    /** Второй круг на месте первого. */
    private void enter(Action a) {
        nested = a;
        list = null;
        hoverSince = -1;
    }

    private void showList(Action a) {
        list = a;
        query = "";
        selected = 0;
        scroll.reset();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Actions.WHEEL.matches(key, scan)) {
            if (!holding && System.currentTimeMillis() - openedAt > HOLD_MS) onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (list != null) list = null;
            else if (nested != null) nested = null;
            else onClose();
            return true;
        }
        if (list != null) {
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!query.isEmpty()) query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
                    selected = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    selected = Math.min(selected + 1, Math.max(0, shownRows.size() - 1));
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    selected = Math.max(0, selected - 1);
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (selected < shownRows.size()) perform(shownRows.get(selected));
                    return true;
                }
                case GLFW.GLFW_KEY_TAB -> {
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
            Action a = slots().get(key - GLFW.GLFW_KEY_1);
            if (a != null) perform(a);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            switchRing(Screen.hasShiftDown() ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (list == null || Character.isISOControl(c)) return false;
        query += c;
        selected = 0;
        scroll.reset();
        return true;
    }

    private void switchRing(int step) {
        if (rings.size() < 2 || nested != null) return;
        ring = Math.floorMod(ring + step, rings.size());
        Actions.setLastRing(currentRing().id());
        hoverSince = -1;
    }

    @Override
    protected boolean onScroll(double x, double y, double sx, double sy) {
        if (list != null && x >= listX && x < listX + LIST_W && y >= listY && y < listY + listH) {
            scroll.by((float) (-sy * ROW_H * 2));
            return true;
        }
        switchRing(sy < 0 ? 1 : -1);
        return true;
    }

    @Override
    protected boolean onClick(double x, double y, int button) {
        if (hits.click(x, y, button)) return true;
        int s = sectorAt(x, y);
        if (s >= 0 && button == 0) {
            Action a = slots().get(s);
            if (a != null) perform(a);
            return true;
        }
        if (list != null && !(x >= listX && x < listX + LIST_W)) list = null;
        return true;
    }

    private int sectorAt(double x, double y) {
        if (list != null && x >= listX - 4 && x < listX + LIST_W + 4 && y >= listY - 4 && y < listY + listH + 4) return -1;
        double dx = x - cx(), dy = y - cy(), d = Math.hypot(dx, dy);
        if (d < IR) return -1;
        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 22.5;
        return (int) Math.floor(Math.floorMod((long) Math.floor(angle * 1000), 360_000L) / 1000.0 / 45.0) % Ring.SECTORS;
    }

    // --- отрисовка ---

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float partialTick) {
        Theme t = ThemeManager.get();
        hits.clear();
        Draw.rect(g, 0, 0, width, height, t.c("wheel.dim"));
        List<@Nullable Action> slots = slots();
        int over = sectorAt(mx, my);
        if (over != hovered) {
            hovered = over;
            hoverAt = System.currentTimeMillis();
        }
        // задержка мыши на секторе-списке открывает список
        if (!holding && hovered >= 0 && list == null) {
            Action a = slots.get(hovered);
            if (a != null && a.opensList() && System.currentTimeMillis() - hoverAt > LIST_DELAY_MS && hoverSince != hovered) {
                hoverSince = hovered;
                showList(a);
            }
        }
        float cx = cx(), cy = cy();
        if (rings.size() > 1 && nested == null) ringTabs(g, t, cx, cy - R - 20, mx, my);
        for (int i = 0; i < Ring.SECTORS; i++) sector(g, t, i, slots.get(i), cx, cy);
        for (int i = 0; i < Ring.SECTORS; i++) sectorContent(g, t, i, slots.get(i), cx, cy);
        center(g, t, cx, cy, hovered >= 0 ? slots.get(hovered) : null, mx, my);
        keysHint(g, t, cx, cy + R + 19);
        if (list != null) list(g, t, cx + 107, cy - 134, mx, my);
    }

    private void ringTabs(GuiGraphics g, Theme t, float cx, float y, int mx, int my) {
        float total = 0;
        List<String> labels = new ArrayList<>();
        for (Ring r : rings) {
            String s = r.label().getString();
            labels.add(s);
            total += Draw.labelWidth(s, 12, Weight.SEMIBOLD) + 14;
        }
        float x = cx - total / 2f - 2;
        Draw.round(g, x - 2, y - 2, total + 8, 16, 8, t.c("wheel.center"));
        for (int i = 0; i < rings.size(); i++) {
            float w = Draw.labelWidth(labels.get(i), 12, Weight.SEMIBOLD) + 14;
            boolean on = i == Math.floorMod(ring, rings.size());
            boolean hot = Kit.over(mx, my, (int) x, (int) y, (int) w, 12);
            if (on || hot) Draw.round(g, x + 2, y, w, 12, 6, on ? t.c("wheel.hover") : t.c("wheel.sector"));
            Draw.label(g, labels.get(i), x + 9, y + 6, 12, Weight.SEMIBOLD, on ? t.c("wheel.fg") : t.c("wheel.muted"));
            int index = i;
            hits.add((int) x + 2, (int) y, (int) w, 12, b -> {
                ring = index;
                Actions.setLastRing(currentRing().id());
            });
            x += w;
        }
    }

    private static float startDeg(int i) {
        return -90 - 22.5f + i * 45 + GAP / 2f;
    }

    private void sector(GuiGraphics g, Theme t, int i, @Nullable Action a, float cx, float cy) {
        boolean on = i == hovered || (list != null && a == list);
        boolean off = a == null || !a.state().usable() && a.state().kind() != Action.State.Kind.PAUSE;
        int fill = on ? t.c("wheel.hover") : off ? t.c("wheel.sector.off") : t.c("wheel.sector");
        int line = on ? t.c("wheel.hover.line") : t.c("wheel.line");
        float a0 = startDeg(i), sweep = 45 - GAP, width = on ? 0.75f : 0.5f;
        Draw.arc(g, cx, cy, (R + IR) / 2f, a0, sweep, R - IR, fill);
        Draw.arc(g, cx, cy, R - width / 2f, a0, sweep, width, line);
        Draw.arc(g, cx, cy, IR + width / 2f, a0, sweep, width, line);
        for (float deg : new float[] {a0, a0 + sweep}) {
            double rad = Math.toRadians(deg);
            float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
            Draw.line(g, cx + c * IR, cy + s * IR, cx + c * R, cy + s * R, width, line);
        }
    }

    private void sectorContent(GuiGraphics g, Theme t, int i, @Nullable Action a, float cx, float cy) {
        double rad = Math.toRadians(-90 + i * 45);
        float ir = (R + IR) / 2f;
        float x = cx + (float) Math.cos(rad) * ir, y = cy + (float) Math.sin(rad) * ir;
        float nx = cx + (float) Math.cos(rad) * (R + 7.5f), ny = cy + (float) Math.sin(rad) * (R + 7.5f);
        String n = Integer.toString(i + 1);
        Draw.label(g, n, nx - Draw.labelWidth(n, 11, Weight.MEDIUM) / 2f, ny, 11, Weight.MEDIUM, t.c("wheel.muted"));
        if (a == null) return;
        Action.State st = a.state();
        boolean dim = !st.usable() && st.kind() != Action.State.Kind.PAUSE;
        int fg = dim ? t.c("wheel.muted") : t.c("wheel.fg");
        String detail = st.kind() == Action.State.Kind.PAUSE ? st.clock() : st.kind() == Action.State.Kind.OFF && !st.reason().isEmpty()
            ? shortReason(st.reason()) : a.opensList() ? "" : a.detail();
        float tile = 19, top = y - (detail.isEmpty() ? 14 : 17);
        boolean on = i == hovered;
        int tileFill = on && !dim ? 0xFFF7F8FB : t.c("wheel.tile");
        Draw.round(g, x - tile / 2f, top, tile, tile, 4.5f, tileFill);
        Paint.icon(g, a, x - 4.75f, top + 4.75f, 9.5f, on && !dim ? 0xFF1C1F27 : fg);
        int badge = a.badge();
        if (badge > 0) {
            String b = badge > 99 ? "99+" : Integer.toString(badge);
            float bw = Math.max(9, Draw.labelWidth(b, 10, Weight.SEMIBOLD) + 5);
            Draw.round(g, x + tile / 2f - bw / 2f - 1, top - 3, bw, 9, 4.5f, t.c("wheel.hover"));
            Draw.label(g, b, x + tile / 2f - 1 - Draw.labelWidth(b, 10, Weight.SEMIBOLD) / 2f, top + 1.5f, 10, Weight.SEMIBOLD, t.c("wheel.fg"));
        }
        String label = Draw.ellipsize(a.label().getString(), 78, 13, Weight.SEMIBOLD);
        Draw.label(g, label, x - Draw.labelWidth(label, 13, Weight.SEMIBOLD) / 2f, top + tile + 6, 13, Weight.SEMIBOLD, fg);
        if (!detail.isEmpty()) {
            String d = Draw.ellipsize(detail, 78, 11, Weight.REGULAR);
            int dc = st.kind() == Action.State.Kind.PAUSE ? t.c("wheel.fg") : t.c("wheel.muted");
            Draw.label(g, d, x - Draw.labelWidth(d, 11, Weight.REGULAR) / 2f, top + tile + 13, 11, Weight.REGULAR, dc);
        }
    }

    private static String shortReason(String reason) {
        return reason.length() > 22 ? reason.substring(0, 21) + "…" : reason;
    }

    private void center(GuiGraphics g, Theme t, float cx, float cy, @Nullable Action a, int mx, int my) {
        float r = IR - 2;
        Draw.disc(g, cx, cy, r, t.c("wheel.center"));
        Draw.ring(g, cx, cy, r, 0.5f, t.c("wheel.line"));
        int fg = t.c("wheel.fg"), muted = t.c("wheel.muted");
        float room = r * 2 - 8;
        if (a == null && nested != null) {
            // второй круг: в середине его имя и «Назад»
            centered(g, nested.label().getString(), cx, cy - 9, 13, Weight.SEMIBOLD, fg, room);
            String back = Component.translatable("ryscore.wheel.back").getString();
            float bw = Math.min(room, Draw.labelWidth(back, 12, Weight.MEDIUM) + 12), bh = 13, bx = cx - bw / 2f, by = cy + 1;
            boolean hot = Kit.over(mx, my, (int) bx, (int) by, (int) bw, (int) bh);
            Draw.roundBox(g, bx, by, bw, bh, 3.5f, hot ? t.c("wheel.hover") : t.c("wheel.tile"), t.c("wheel.line"));
            centered(g, back, cx, by + bh / 2f, 12, Weight.MEDIUM, fg, room);
            hits.add((int) bx, (int) by, (int) bw, (int) bh, b -> nested = null);
            return;
        }
        if (a == null) {
            Ring rg = currentRing();
            String name = rg == null ? "" : rg.label().getString();
            if (holding) {
                centered(g, name, cx, cy - 4, 13, Weight.SEMIBOLD, fg, room);
                centered(g, Component.translatable("ryscore.wheel.release_center").getString(), cx, cy + 6, 11, Weight.REGULAR, muted, room);
                return;
            }
            centered(g, name, cx, cy - 9, 13, Weight.SEMIBOLD, fg, room);
            String set = Component.translatable("ryscore.wheel.configure").getString();
            float bw = Math.min(room, Draw.labelWidth(set, 12, Weight.MEDIUM) + 12), bh = 13, bx = cx - bw / 2f, by = cy + 1;
            boolean hot = Kit.over(mx, my, (int) bx, (int) by, (int) bw, (int) bh);
            Draw.roundBox(g, bx, by, bw, bh, 3.5f, hot ? t.c("wheel.hover") : t.c("wheel.tile"), t.c("wheel.line"));
            centered(g, set, cx, by + bh / 2f, 12, Weight.MEDIUM, fg, room);
            hits.add((int) bx, (int) by, (int) bw, (int) bh, b -> Actions.openKeys(null));
            return;
        }
        Action.State st = a.state();
        String title = a.label().getString();
        List<String> lines = new ArrayList<>();
        switch (st.kind()) {
            case OFF -> lines.addAll(Draw.wrapLabel(st.reason().isEmpty() ? Component.translatable("ryscore.wheel.unavailable").getString() : st.reason(),
                room, 11.5f, Weight.REGULAR, 3));
            case PAUSE -> lines.add(Component.translatable("ryscore.wheel.pause", st.clock()).getString());
            default -> {
                if (a.opensNested()) lines.add(Component.translatable("ryscore.wheel.nested", a.nested().size()).getString());
                else if (a.opensList()) lines.add(Component.translatable("ryscore.wheel.list", a.list().size()).getString());
                else if (!a.detail().isEmpty()) lines.addAll(Draw.wrapLabel(a.detail(), room, 11.5f, Weight.REGULAR, 2));
            }
        }
        boolean hint = holding && st.usable();
        float block = 10 + lines.size() * 6.5f + (hint ? 10 : 0), y = cy - block / 2f;
        centered(g, title, cx, y + 4.5f, 16, Weight.SEMIBOLD, fg, room);
        y += 11;
        for (String line : lines) {
            centered(g, line, cx, y + 3, 11.5f, Weight.REGULAR, st.kind() == Action.State.Kind.OFF ? t.c("state.warn") : muted, room);
            y += 6.5f;
        }
        if (hint) {
            String release = Component.translatable("ryscore.wheel.release").getString(), key = Binding.keyLabel(Binding.keyOf(Actions.WHEEL));
            float w = Draw.labelWidth(release, 11, Weight.REGULAR) + 3 + Paint.kbdWidth(key, 10.5f);
            float x = cx - w / 2f;
            Draw.label(g, release, x, y + 5, 11, Weight.REGULAR, muted);
            Paint.kbd(g, key, x + Draw.labelWidth(release, 11, Weight.REGULAR) + 3, y + 5, 10.5f, t.c("wheel.tile"), t.c("wheel.line"), muted);
        }
    }

    private static void centered(GuiGraphics g, String s, float cx, float cy, float px, Weight w, int color, float room) {
        String text = Draw.ellipsize(s, room, px, w);
        Draw.label(g, text, cx - Draw.labelWidth(text, px, w) / 2f, cy, px, w, color);
    }

    private void keysHint(GuiGraphics g, Theme t, float cx, float y) {
        List<String[]> parts = new ArrayList<>();
        if (list != null) {
            parts.add(new String[] {"", Component.translatable("ryscore.wheel.hint.search").getString()});
            parts.add(new String[] {"Enter", Component.translatable("ryscore.wheel.hint.enter").getString()});
            parts.add(new String[] {"Esc", Component.translatable("ryscore.wheel.hint.back").getString()});
        } else if (nested != null) {
            parts.add(new String[] {"1–8", Component.translatable("ryscore.wheel.hint.pick").getString()});
            parts.add(new String[] {"Esc", Component.translatable("ryscore.wheel.hint.back").getString()});
        } else {
            parts.add(new String[] {"1–8", Component.translatable("ryscore.wheel.hint.pick").getString()});
            parts.add(new String[] {"Esc", Component.translatable("ryscore.wheel.hint.close").getString()});
            if (rings.size() > 1) parts.add(new String[] {"", Component.translatable("ryscore.wheel.hint.rings").getString()});
        }
        float total = 0;
        for (String[] p : parts) total += (p[0].isEmpty() ? 0 : Paint.kbdWidth(p[0], 10.5f) + 3) + Draw.labelWidth(p[1], 11.5f, Weight.REGULAR) + 9;
        float x = cx - (total - 9) / 2f;
        int fg = t.c("wheel.fg");
        for (String[] p : parts) {
            if (!p[0].isEmpty()) x += Paint.kbd(g, p[0], x, y, 10.5f, t.c("wheel.center"), t.c("wheel.line"), fg) + 3;
            Draw.label(g, p[1], x, y, 11.5f, Weight.REGULAR, fg);
            x += Draw.labelWidth(p[1], 11.5f, Weight.REGULAR) + 9;
        }
    }

    // --- список рядом с колесом ---

    private void list(GuiGraphics g, Theme t, float x, float y, int mx, int my) {
        Action owner = list;
        if (owner == null) return;
        float w = LIST_W, h = Math.min(296, height - y - 6);
        if (y < 6) {
            h = Math.min(296, height - 12);
            y = 6;
        }
        x = Math.min(x, width - w - 6);
        listX = x;
        listY = y;
        listH = h;
        int fg = t.c("wheel.fg"), muted = t.c("wheel.muted"), line = t.c("wheel.line");
        Draw.shadow(g, x, y, w, h, 7, 0.6f);
        Draw.roundBox(g, x, y, w, h, 7, t.c("wheel.center"), line);
        float pad = 7;
        // шапка: значок, имя, число, Esc
        float hy = y + 11;
        Paint.icon(g, owner, x + pad, hy - 4, 8, fg);
        String title = owner.label().getString();
        Draw.label(g, title, x + pad + 11, hy, 14, Weight.SEMIBOLD, fg);
        List<Action> rows = owner.list();
        float tw = Draw.labelWidth(title, 14, Weight.SEMIBOLD);
        Draw.label(g, Integer.toString(rows.size()), x + pad + 11 + tw + 4, hy, 12, Weight.MEDIUM, t.c("link"));
        Paint.kbd(g, "Esc", x + w - pad - Paint.kbdWidth("Esc", 10.5f), hy, 10.5f, t.c("wheel.tile"), line, muted);
        // поиск
        float sy = y + 20, sh = 16;
        Draw.roundBox(g, x + pad, sy, w - pad * 2, sh, 4, t.c("wheel.sector.off"), line);
        Icons.draw(g, Icons.Shape.MAGNIFIER, x + pad + 5, sy + sh / 2f - 3.5f, 7, muted);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (query.isEmpty()) {
            Draw.label(g, Component.translatable("ryscore.wheel.search").getString(), x + pad + 16, sy + sh / 2f, 12.5f, Weight.REGULAR, muted);
        } else {
            String q = Draw.ellipsize(query, w - pad * 2 - 24, 12.5f, Weight.REGULAR);
            Draw.label(g, q + (blink ? "|" : ""), x + pad + 16, sy + sh / 2f, 12.5f, Weight.REGULAR, fg);
        }
        // строки по разделам
        String needle = query.toLowerCase(Locale.ROOT);
        List<Action> shown = new ArrayList<>();
        for (Action a : rows) if (needle.isEmpty() || a.label().getString().toLowerCase(Locale.ROOT).contains(needle)) shown.add(a);
        shownRows = shown;
        selected = Math.min(selected, Math.max(0, shown.size() - 1));
        float top = sy + sh + 5, footer = 18, bottom = y + h - footer;
        float content = 0;
        String section = null;
        for (Action a : shown) {
            if (!a.section().isEmpty() && !a.section().equals(section)) {
                content += 12;
                section = a.section();
            }
            content += ROW_H;
        }
        float off = scroll.update(content - (bottom - top));
        Draw.scissor(g, (int) x, (int) top, (int) (x + w), (int) bottom);
        hits.clip((int) top, (int) bottom);
        float ry = top - off;
        section = null;
        for (int i = 0; i < shown.size(); i++) {
            Action a = shown.get(i);
            if (!a.section().isEmpty() && !a.section().equals(section)) {
                section = a.section();
                int count = 0;
                for (Action b : shown) if (section.equals(b.section())) count++;
                Draw.label(g, section, x + pad + 2, ry + 6, 11, Weight.MEDIUM, muted);
                String c = Integer.toString(count);
                Draw.label(g, c, x + w - pad - 2 - Draw.labelWidth(c, 11, Weight.MEDIUM), ry + 6, 11, Weight.MEDIUM, muted);
                ry += 12;
            }
            if (ry + ROW_H > top - 1 && ry < bottom + 1) row(g, t, a, i, x + pad - 2, ry, w - pad * 2 + 4, mx, my);
            ry += ROW_H;
        }
        hits.unclip();
        g.disableScissor();
        if (shown.isEmpty()) {
            Draw.label(g, Component.translatable("ryscore.wheel.nothing").getString(), x + pad + 2, top + 8, 12, Weight.REGULAR, muted);
        }
        // низ: подсказка о своей клавише
        Draw.rect(g, (int) (x + pad), (int) (bottom + 1), (int) (w - pad * 2), 1, line);
        float fy = bottom + footer / 2f + 1;
        Icons.draw(g, Icons.Shape.KEY, x + pad, fy - 3.25f, 6.5f, muted);
        String hint = Component.translatable("ryscore.wheel.own_key").getString();
        boolean hot = Kit.over(mx, my, (int) x, (int) bottom, (int) w, (int) footer);
        Draw.label(g, Draw.ellipsize(hint, w - pad * 2 - 10, 11.5f, Weight.REGULAR), x + pad + 9, fy, 11.5f, Weight.REGULAR, hot ? fg : muted);
        hits.add((int) x, (int) bottom, (int) w, (int) footer, b -> Actions.openKeys(null));
    }

    private void row(GuiGraphics g, Theme t, Action a, int index, float x, float y, float w, int mx, int my) {
        boolean hot = Kit.over(mx, my, (int) x, (int) y, (int) w, (int) ROW_H);
        if (hot && index != selected) selected = index;
        Action.State st = a.state();
        boolean dim = !st.usable() && st.kind() != Action.State.Kind.PAUSE;
        if (index == selected) Draw.round(g, x, y + 0.5f, w, ROW_H - 1, 4, t.c("wheel.hover"));
        int pin = a.color() != 0 ? a.color() : 0xFFCFD3DA;
        float pcx = x + 9, pcy = y + ROW_H / 2f;
        Draw.disc(g, pcx, pcy, 4.5f, dim ? Draw.alpha(pin, 0.5f) : pin);
        if (a.item().isEmpty() && a.texture() == null) Icons.draw(g, a.shape() != null ? a.shape() : Icons.Shape.SERVER, pcx - 2.75f, pcy - 2.75f, 5.5f, 0xFF1C1F27);
        else Paint.icon(g, a, pcx - 4, pcy - 4, 8, 0xFF1C1F27);
        int fg = dim ? t.c("wheel.muted") : t.c("wheel.fg");
        float right = x + w - 4;
        String r = st.kind() == Action.State.Kind.PAUSE ? st.clock() : a.right();
        if (!r.isEmpty()) {
            Draw.label(g, r, right - Draw.labelWidth(r, 11, Weight.REGULAR), pcy, 11, Weight.REGULAR, t.c("wheel.muted"));
            right -= Draw.labelWidth(r, 11, Weight.REGULAR) + 5;
        }
        Binding b = Actions.binding(a.id());
        if (b != null) {
            String k = b.label();
            right -= Paint.kbdWidth(k, 10.5f);
            Paint.kbd(g, k, right, pcy, 10.5f, t.c("wheel.tile"), t.c("wheel.line"), t.c("wheel.muted"));
            right -= 4;
        }
        Draw.label(g, Draw.ellipsize(a.label().getString(), right - x - 20, 13, Weight.REGULAR), x + 18, pcy, 13, Weight.REGULAR, fg);
        hits.add((int) x, (int) y, (int) w, (int) ROW_H, btn -> {
            if (btn == 0) perform(a);
        });
    }

    /** Номер кольца по id: последнее открытое, иначе «Моё». */
    private static final class ResourceLocationIndex {
        int find(List<Ring> rings, net.minecraft.resources.ResourceLocation id) {
            for (int i = 0; i < rings.size(); i++) if (rings.get(i).id().equals(id)) return i;
            return 0;
        }
    }

    private static final class Screen {
        static boolean hasShiftDown() {
            return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        }
    }
}
