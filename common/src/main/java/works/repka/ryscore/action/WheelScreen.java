package works.repka.ryscore.action;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 *   <li>в секторе только значок и короткое название, подпись наведённого сектора или строки видна в середине (1.16);</li>
 *   <li>сектор с подменю отмечен у края: шеврон у списка, три точки у набора действий;</li>
 *   <li>сектор с подменю («Перенести…», «Места сервера», «Пинг команде») открывает его панелью рядом, колесо остаётся на месте
 *       (1.16): по клику или клавише, а наведением только в режиме «по зажатию». Esc и «Назад» закрывают подменю, второй Esc колесо.</li>
 * </ul>
 * Мир не на паузе, камера не крутится: курсор свободен, пока колесо открыто. Цвета колеса тёмные в любой теме (токены {@code wheel.*}).
 */
final class WheelScreen extends ScaledScreen {
    private static final float R = 88, IR = 36, GAP = 1.6f;
    private static final long HOLD_MS = 250;
    private static final float LIST_W = 160, ROW_H = 17, SUB_GAP = 14, MARGIN = 6, LABEL_H = 7, LABEL_PAD = 2.5f;
    /** С какого числа строк в подменю есть поиск: короткому набору («Перенести…») он не нужен. */
    private static final int SEARCH_FROM = 9;

    private final List<Ring> rings;
    private int ring;
    private boolean holding;
    private final long openedAt = System.currentTimeMillis();
    private int hovered = -1, hoverDone = -1;
    private long hoverAt;
    private final Kit.Hits hits = new Kit.Hits();
    /** Открытые подменю: внизу сектор колеса, сверху строка-список, раскрытая внутри него (1.16). */
    private final Deque<Action> subs = new ArrayDeque<>();
    private String query = "";
    private int selected;
    private final Scroller scroll = new Scroller();
    private float listX, listY, listH;
    private List<Action> shownRows = List.of();
    /** Строка подменю под мышью в прошлом кадре: её подпись в середине, её выполняет отпускание Ё. */
    private @Nullable Action rowHot;
    private int lastMx, lastMy;

    private WheelScreen(boolean hold, @Nullable Action sub) {
        super(Component.translatable("ryscore.wheel.title"));
        this.rings = Actions.availableRings();
        this.holding = hold;
        if (sub != null) subs.push(sub);
        ResourceLocationIndex last = new ResourceLocationIndex();
        this.ring = last.find(rings, Actions.lastRing());
    }

    /** Открывает колесо; {@code hold}: клавиша ещё зажата и отпускание выполнит сектор. */
    static void open(boolean hold) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new WheelScreen(hold, null));
    }

    /** Колесо с раскрытым подменю набора (своя клавиша на «Перенести…»). */
    static void openNested(Action action) {
        openSub(action);
    }

    /** Колесо с раскрытым подменю-списком (своя клавиша на «Места сервера…»). */
    static void openList(Action action) {
        openSub(action);
    }

    private static void openSub(Action action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WheelScreen w) {
            w.showSub(action);
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
        Ring r = currentRing();
        return r == null ? java.util.Collections.nCopies(Ring.SECTORS, null) : Actions.slots(r.id());
    }

    private float cx() {
        return width / 2f;
    }

    private float cy() {
        return height / 2f - 6;
    }

    private static boolean same(@Nullable Action a, @Nullable Action b) {
        return a != null && b != null && a.id().equals(b.id());
    }

    /** Сектор колеса, чьё подменю открыто. */
    private @Nullable Action root() {
        return subs.peekLast();
    }

    /** Подменю, которое сейчас видно. */
    private @Nullable Action top() {
        return subs.peekFirst();
    }

    private boolean searchable() {
        Action owner = top();
        return owner != null && owner.sub().size() >= SEARCH_FROM;
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
        // отпустили после удержания: над подменю выполнить строку под мышью, над сектором — сектор, в середине ничего
        if (!subs.isEmpty() && overList(lastMx, lastMy)) {
            if (rowHot != null) perform(rowHot, true);
            return;
        }
        if (hovered >= 0) {
            Action a = slots().get(hovered);
            if (a != null && a.opensSub()) {
                if (!same(root(), a)) showSub(a);
            } else if (a != null && a.state().usable()) perform(a, false);
            else onClose();
        } else {
            onClose();
        }
    }

    /** Выполняет действие; с подменю открывает его: из сектора рядом с колесом, из строки — в той же панели. */
    private void perform(Action a, boolean fromRow) {
        if (a.opensSub()) {
            if (fromRow) drill(a);
            else if (same(root(), a)) closeSub();
            else showSub(a);
            return;
        }
        if (!a.state().usable()) return;
        Minecraft.getInstance().setScreen(null);
        Actions.run(a);
    }

    private void showSub(Action a) {
        subs.clear();
        subs.push(a);
        resetList();
    }

    private void drill(Action a) {
        subs.push(a);
        resetList();
    }

    /** Закрывает видимое подменю: строку-список возвращает к набору, набор — к колесу. */
    private void back() {
        if (subs.isEmpty()) return;
        subs.pop();
        if (subs.isEmpty()) hoverDone = hovered;
        resetList();
    }

    private void closeSub() {
        if (subs.isEmpty()) return;
        subs.clear();
        hoverDone = hovered;
        resetList();
    }

    private void resetList() {
        query = "";
        selected = 0;
        scroll.reset();
        rowHot = null;
        shownRows = List.of();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Actions.WHEEL.matches(key, scan)) {
            if (!holding && System.currentTimeMillis() - openedAt > HOLD_MS) onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!subs.isEmpty()) back();
            else onClose();
            return true;
        }
        // 1–8 выбирают сектор и при открытом подменю, пока в поиске пусто
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8 && query.isEmpty()) {
            Action a = slots().get(key - GLFW.GLFW_KEY_1);
            if (a != null) perform(a, false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            switchRing(Screen.hasShiftDown() ? -1 : 1);
            return true;
        }
        if (!subs.isEmpty()) {
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (query.isEmpty()) back();
                    else query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
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
                    if (selected < shownRows.size()) perform(shownRows.get(selected), true);
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (subs.isEmpty() || Character.isISOControl(c)) return false;
        if (!searchable()) return true;
        if (query.isEmpty() && c >= '1' && c <= '8') return true; // цифру уже забрал сектор
        query += c;
        selected = 0;
        scroll.reset();
        return true;
    }

    private void switchRing(int step) {
        if (rings.size() < 2) return;
        ring = Math.floorMod(ring + step, rings.size());
        Actions.setLastRing(currentRing().id());
        subs.clear();
        resetList();
        hoverDone = -1;
    }

    private boolean overList(double x, double y) {
        return !subs.isEmpty() && x >= listX - 4 && x < listX + LIST_W + 4 && y >= listY - 4 && y < listY + listH + 4;
    }

    @Override
    protected boolean onScroll(double x, double y, double sx, double sy) {
        if (overList(x, y)) {
            scroll.by((float) (-sy * ROW_H * 2));
            return true;
        }
        switchRing(sy < 0 ? 1 : -1);
        return true;
    }

    @Override
    protected boolean onClick(double x, double y, int button) {
        if (hits.click(x, y, button)) return true;
        if (overList(x, y)) return true;
        int s = sectorAt(x, y);
        if (s >= 0 && button == 0) {
            Action a = slots().get(s);
            if (a != null) perform(a, false);
            return true;
        }
        closeSub();
        return true;
    }

    private int sectorAt(double x, double y) {
        if (overList(x, y)) return -1;
        double dx = x - cx(), dy = y - cy(), d = Math.hypot(dx, dy);
        if (d < IR) return -1;
        // с открытым подменю промежуток между колесом и панелью ничей: путь мыши к панели не выбирает сектор
        if (!subs.isEmpty() && d > R + 10) return -1;
        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 22.5;
        return (int) Math.floor(Math.floorMod((long) Math.floor(angle * 1000), 360_000L) / 1000.0 / 45.0) % Ring.SECTORS;
    }

    // --- отрисовка ---

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float partialTick) {
        Theme t = ThemeManager.get();
        hits.clear();
        lastMx = mx;
        lastMy = my;
        Action row = rowHot;
        rowHot = null;
        Draw.rect(g, 0, 0, width, height, t.c("wheel.dim"));
        List<@Nullable Action> slots = slots();
        int over = sectorAt(mx, my);
        if (over != hovered) {
            hovered = over;
            hoverAt = System.currentTimeMillis();
            hoverDone = -1;
        }
        // задержка мыши на секторе с подменю открывает его только в режиме «по зажатию»
        if (hovered >= 0) {
            Action a = slots.get(hovered);
            if (a != null && a.opensSub()
                && WheelLayout.hoverOpens(Actions.holdMode(), same(root(), a), hoverDone == hovered, System.currentTimeMillis() - hoverAt)) {
                hoverDone = hovered;
                showSub(a);
            }
        }
        float cx = cx(), cy = cy();
        if (rings.size() > 1) ringTabs(g, t, cx, cy - R - 20, mx, my);
        for (int i = 0; i < Ring.SECTORS; i++) sector(g, t, i, slots.get(i), cx, cy);
        for (int i = 0; i < Ring.SECTORS; i++) sectorContent(g, t, i, slots.get(i), cx, cy);
        Action info = row != null && !subs.isEmpty() ? row : hovered >= 0 ? slots.get(hovered) : null;
        center(g, t, cx, cy, info, mx, my);
        keysHint(g, t, cx, cy + R + 19);
        if (!subs.isEmpty()) list(g, t, cx, cy, mx, my);
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
                if (index != Math.floorMod(ring, rings.size())) switchRing(index - Math.floorMod(ring, rings.size()));
            });
            x += w;
        }
    }

    private static float startDeg(int i) {
        return -90 - 22.5f + i * 45 + GAP / 2f;
    }

    private void sector(GuiGraphics g, Theme t, int i, @Nullable Action a, float cx, float cy) {
        boolean on = i == hovered || same(root(), a);
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

    /** Значок и название; подпись (место, расстояние, причина) только в середине колеса. Пауза остаётся часами под названием. */
    private void sectorContent(GuiGraphics g, Theme t, int i, @Nullable Action a, float cx, float cy) {
        double rad = Math.toRadians(-90 + i * 45);
        // в верхней половине значок с подписью чуть дальше от середины: подпись под значком уходит в широкую часть сектора
        float ir = WheelLayout.contentRadius(i, IR, R);
        float x = cx + (float) Math.cos(rad) * ir, y = cy + (float) Math.sin(rad) * ir;
        float nx = cx + (float) Math.cos(rad) * (R + 7.5f), ny = cy + (float) Math.sin(rad) * (R + 7.5f);
        String n = Integer.toString(i + 1);
        Draw.label(g, n, nx - Draw.labelWidth(n, 11, Weight.MEDIUM) / 2f, ny, 11, Weight.MEDIUM, t.c("wheel.muted"));
        if (a == null) return;
        Action.State st = a.state();
        boolean dim = !st.usable() && st.kind() != Action.State.Kind.PAUSE;
        boolean pause = st.kind() == Action.State.Kind.PAUSE;
        int fg = dim ? t.c("wheel.muted") : t.c("wheel.fg");
        float tile = 19, top = y - (pause ? 17 : 14);
        boolean on = i == hovered || same(root(), a);
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
        subMark(g, t, i, a, cx, cy, on && !dim, dim);
        float ly = top + tile + 6;
        float room = WheelLayout.labelRoom(i, x - cx, ly - cy, LABEL_H, IR, R, GAP, LABEL_PAD);
        WheelLayout.Fit fit = WheelLayout.fit(a.label().getString(), a.shortLabel().getString(), room,
            (s, px) -> Draw.labelWidth(s, px, Weight.SEMIBOLD));
        Draw.label(g, fit.text(), x - Draw.labelWidth(fit.text(), fit.px(), Weight.SEMIBOLD) / 2f, ly, fit.px(), Weight.SEMIBOLD, fg);
        if (pause) {
            String c = st.clock();
            Draw.label(g, c, x - Draw.labelWidth(c, 11, Weight.REGULAR) / 2f, top + tile + 13, 11, Weight.REGULAR, t.c("wheel.fg"));
        }
    }

    /**
     * Тихий знак подменю у внешнего края сектора (просьба автора 20.11): список («Места сервера», «Пинг команде») — шеврон
     * в сторону, где встанет панель; набор действий («Перенести…», бывший второй круг) — три точки по дуге.
     */
    private void subMark(GuiGraphics g, Theme t, int i, Action a, float cx, float cy, boolean on, boolean dim) {
        if (!a.opensSub()) return;
        int c = on ? t.c("wheel.fg") : t.c("wheel.muted");
        if (dim) c = Draw.alpha(c, 0.6f);
        float r = R - 6;
        if (a.opensNested()) {
            for (int k = -1; k <= 1; k++) {
                float[] p = WheelLayout.polar(i, WheelLayout.markDeg(i) + k * WheelLayout.MARK_STEP_DEG, r);
                Draw.disc(g, cx + p[0], cy + p[1], 1.1f, c);
            }
            return;
        }
        float[] p = WheelLayout.polar(i, WheelLayout.markDeg(i), r);
        boolean left = WheelLayout.subX(cx, R + SUB_GAP, LIST_W, width, MARGIN) < cx;
        Icons.draw(g, left ? Icons.Shape.CHEVRON_LEFT : Icons.Shape.CHEVRON_RIGHT, cx + p[0] - 2.5f, cy + p[1] - 2.5f, 5, c);
    }

    private void center(GuiGraphics g, Theme t, float cx, float cy, @Nullable Action a, int mx, int my) {
        float r = IR - 2;
        Draw.disc(g, cx, cy, r, t.c("wheel.center"));
        Draw.ring(g, cx, cy, r, 0.5f, t.c("wheel.line"));
        int fg = t.c("wheel.fg"), muted = t.c("wheel.muted");
        float room = r * 2 - 8;
        if (a == null && !subs.isEmpty()) {
            // открыто подменю: в середине его имя и «Назад»
            Action owner = top();
            centered(g, owner == null ? "" : owner.label().getString(), cx, cy - 9, 13, Weight.SEMIBOLD, fg, room);
            button(g, t, Component.translatable("ryscore.wheel.back").getString(), cx, cy + 1, room, mx, my, this::back);
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
            button(g, t, Component.translatable("ryscore.wheel.configure").getString(), cx, cy + 1, room, mx, my, () -> Actions.openKeys(null));
            return;
        }
        Action.State st = a.state();
        // полное имя: крупно, мельче, в две строки
        String full = a.label().getString();
        List<String> title = new ArrayList<>();
        float tpx = 13;
        for (float px : new float[] {16, 14, 13}) {
            if (Draw.labelWidth(full, px, Weight.SEMIBOLD) <= room) {
                title.add(full);
                tpx = px;
                break;
            }
        }
        if (title.isEmpty()) title.addAll(Draw.wrapLabel(full, room, 13, Weight.SEMIBOLD, 2));
        List<String> lines = new ArrayList<>();
        int most = title.size() > 1 ? 2 : 3;
        switch (st.kind()) {
            case OFF -> lines.addAll(Draw.wrapLabel(st.reason().isEmpty() ? Component.translatable("ryscore.wheel.unavailable").getString() : st.reason(),
                room, 11.5f, Weight.REGULAR, most));
            case PAUSE -> lines.add(Component.translatable("ryscore.wheel.pause", st.clock()).getString());
            default -> {
                if (!a.detail().isEmpty()) lines.addAll(Draw.wrapLabel(a.detail(), room, 11.5f, Weight.REGULAR, most - 1));
                else if (a.opensSub()) lines.add(Component.translatable("ryscore.wheel.list", a.sub().size()).getString());
            }
        }
        boolean hint = holding && st.usable();
        float step = title.size() > 1 ? 8 : 11;
        float block = (title.size() > 1 ? title.size() * 8f - 1 : 10) + lines.size() * 6.5f + (hint ? 10 : 0), y = cy - block / 2f;
        for (String line : title) {
            centered(g, line, cx, y + (title.size() > 1 ? 3.5f : 4.5f), tpx, Weight.SEMIBOLD, fg, room);
            y += step;
        }
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

    private void button(GuiGraphics g, Theme t, String text, float cx, float by, float room, int mx, int my, Runnable click) {
        float bw = Math.min(room, Draw.labelWidth(text, 12, Weight.MEDIUM) + 12), bh = 13, bx = cx - bw / 2f;
        boolean hot = Kit.over(mx, my, (int) bx, (int) by, (int) bw, (int) bh);
        Draw.roundBox(g, bx, by, bw, bh, 3.5f, hot ? t.c("wheel.hover") : t.c("wheel.tile"), t.c("wheel.line"));
        centered(g, text, cx, by + bh / 2f, 12, Weight.MEDIUM, t.c("wheel.fg"), room);
        hits.add((int) bx, (int) by, (int) bw, (int) bh, b -> click.run());
    }

    private static void centered(GuiGraphics g, String s, float cx, float cy, float px, Weight w, int color, float room) {
        String text = Draw.ellipsize(s, room, px, w);
        Draw.label(g, text, cx - Draw.labelWidth(text, px, w) / 2f, cy, px, w, color);
    }

    private void keysHint(GuiGraphics g, Theme t, float cx, float y) {
        List<String[]> parts = new ArrayList<>();
        if (!subs.isEmpty()) {
            if (searchable()) parts.add(new String[] {"", Component.translatable("ryscore.wheel.hint.search").getString()});
            parts.add(new String[] {"Enter", Component.translatable("ryscore.wheel.hint.enter").getString()});
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

    // --- подменю рядом с колесом ---

    private void list(GuiGraphics g, Theme t, float cx, float cy, int mx, int my) {
        Action owner = top();
        if (owner == null) return;
        List<Action> rows = owner.sub();
        boolean search = rows.size() >= SEARCH_FROM;
        float w = LIST_W, pad = 7, footer = 18;
        float head = 20 + (search ? 21 : 2);
        // высота по всем строкам, а не по найденным: верхний край не прыгает при поиске
        float content = 0;
        String section = null;
        for (Action a : rows) {
            if (!a.section().isEmpty() && !a.section().equals(section)) {
                content += 12;
                section = a.section();
            }
            content += ROW_H;
        }
        float h = Math.min(Math.min(296, height - MARGIN * 2), head + Math.max(content, ROW_H) + footer + 3);
        float x = WheelLayout.subX(cx, R + SUB_GAP, w, width, MARGIN), y = WheelLayout.subY(cy, h, height, MARGIN);
        listX = x;
        listY = y;
        listH = h;
        int fg = t.c("wheel.fg"), muted = t.c("wheel.muted"), line = t.c("wheel.line");
        Draw.shadow(g, x, y, w, h, 7, 0.6f);
        Draw.roundBox(g, x, y, w, h, 7, t.c("wheel.center"), line);
        // шапка: «назад» внутри набора, значок, имя, число, Esc
        float hy = y + 11, tx = x + pad;
        if (subs.size() > 1) {
            boolean hot = Kit.over(mx, my, (int) tx - 2, (int) hy - 7, 11, 14);
            Icons.draw(g, Icons.Shape.CHEVRON_LEFT, tx - 1, hy - 3.5f, 7, hot ? fg : muted);
            hits.add((int) tx - 2, (int) hy - 7, 11, 14, b -> back());
            tx += 10;
        }
        Paint.icon(g, owner, tx, hy - 4, 8, fg);
        String count = Integer.toString(rows.size());
        float escW = Paint.kbdWidth("Esc", 10.5f), countW = Draw.labelWidth(count, 12, Weight.MEDIUM);
        String title = Draw.ellipsize(owner.label().getString(), x + w - pad - escW - 8 - countW - 4 - (tx + 11), 14, Weight.SEMIBOLD);
        Draw.label(g, title, tx + 11, hy, 14, Weight.SEMIBOLD, fg);
        Draw.label(g, count, tx + 11 + Draw.labelWidth(title, 14, Weight.SEMIBOLD) + 4, hy, 12, Weight.MEDIUM, t.c("link"));
        float ex = x + w - pad - escW;
        boolean escHot = Kit.over(mx, my, (int) ex, (int) hy - 6, (int) escW, 12);
        Paint.kbd(g, "Esc", ex, hy, 10.5f, escHot ? t.c("wheel.hover") : t.c("wheel.tile"), line, escHot ? fg : muted);
        hits.add((int) ex, (int) hy - 6, (int) escW, 12, b -> back());
        // поиск у длинных списков
        if (search) {
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
        }
        // строки по разделам
        String needle = query.toLowerCase(Locale.ROOT);
        List<Action> shown = new ArrayList<>();
        for (Action a : rows) if (needle.isEmpty() || a.label().getString().toLowerCase(Locale.ROOT).contains(needle)) shown.add(a);
        shownRows = shown;
        selected = Math.min(selected, Math.max(0, shown.size() - 1));
        float top = y + head, bottom = y + h - footer;
        float shownContent = 0;
        section = null;
        for (Action a : shown) {
            if (!a.section().isEmpty() && !a.section().equals(section)) {
                shownContent += 12;
                section = a.section();
            }
            shownContent += ROW_H;
        }
        float off = scroll.update(shownContent - (bottom - top));
        Draw.scissor(g, (int) x, (int) top, (int) (x + w), (int) bottom);
        hits.clip((int) top, (int) bottom);
        float ry = top - off;
        section = null;
        for (int i = 0; i < shown.size(); i++) {
            Action a = shown.get(i);
            if (!a.section().isEmpty() && !a.section().equals(section)) {
                section = a.section();
                int n = 0;
                for (Action b : shown) if (section.equals(b.section())) n++;
                Draw.label(g, section, x + pad + 2, ry + 6, 11, Weight.MEDIUM, muted);
                String c = Integer.toString(n);
                Draw.label(g, c, x + w - pad - 2 - Draw.labelWidth(c, 11, Weight.MEDIUM), ry + 6, 11, Weight.MEDIUM, muted);
                ry += 12;
            }
            if (ry + ROW_H > top - 1 && ry < bottom + 1) row(g, t, a, i, x + pad - 2, ry, w - pad * 2 + 4, top, bottom, mx, my);
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

    private void row(GuiGraphics g, Theme t, Action a, int index, float x, float y, float w, float top, float bottom, int mx, int my) {
        boolean hot = Kit.over(mx, my, (int) x, (int) y, (int) w, (int) ROW_H) && my >= top && my < bottom;
        if (hot) {
            if (index != selected) selected = index;
            rowHot = a;
        }
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
        if (a.opensSub()) {
            Icons.draw(g, Icons.Shape.CHEVRON_RIGHT, right - 6, pcy - 3, 6, t.c("wheel.muted"));
            right -= 10;
        }
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
            if (btn == 0) perform(a, true);
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
