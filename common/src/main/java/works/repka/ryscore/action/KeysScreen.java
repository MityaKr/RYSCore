package works.repka.ryscore.action;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * Окно «Клавиши» (1.14, макет {@code essentials/img/03_keys.png}): слева действия по группам со своей клавишей и сектором
 * колеса, справа колесо выбранного кольца, клавиша колеса и её режим, свободные клавиши.
 *
 * <ul>
 *   <li>«Назначить» ждёт нажатия: клавиша или кнопка мыши с Ctrl, Shift или Alt; Backspace снимает, Esc отменяет;</li>
 *   <li>занятая клавиша показывает, у кого она («Mekanism (подсказки HUD)»): можно оставить, сработает у всех, как в игре;</li>
 *   <li>действие перетаскивается на сектор колеса; «в колесо» кладёт его в выбранный или первый пустой сектор; ПКМ по сектору очищает.</li>
 * </ul>
 */
final class KeysScreen extends ScaledScreen {
    private static final float ROW_H = 20, GROUP_H = 12, WR = 61, WIR = 24, GAP = 1.6f;
    private static final ResourceLocation WHEEL_KEY = ResourceLocation.fromNamespaceAndPath("ryscore", "wheel_key");
    /** Фильтр «Конфликты»: id группы, которой нет у действий. */
    private static final String CLASHES = "#clashes";

    private final @Nullable Screen parent;
    private final Kit.Hits hits = new Kit.Hits();
    private final Scroller scroll = new Scroller();
    private final Kit.Dropdown modeDrop = new Kit.Dropdown();
    private String query = "";
    private @Nullable String group;
    private ResourceLocation ring = Ring.MINE;
    private int selectedSector = -1;
    /** Чьё сочетание ждём: id действия или {@link #WHEEL_KEY}. */
    private @Nullable ResourceLocation capturing;
    /** Сочетание с конфликтом, ждёт ответа «оставить» или «другая». */
    private @Nullable Binding pending;
    private List<String> pendingConflicts = List.of();
    private @Nullable Action dragging;
    private double dragX, dragY;
    private boolean dragMoved;
    private @Nullable Action pressed;
    private float wheelCx, wheelCy;
    private @Nullable List<String> tooltip;
    private List<Actions.Clash> clashes = List.of();
    private long clashesAt;

    KeysScreen(@Nullable Screen parent) {
        super(Component.translatable("ryscore.keys.title"));
        this.parent = parent;
        ResourceLocation last = Actions.lastRing();
        if (Actions.ringById(last) != null) ring = last;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    // --- раскладка окна ---

    private int winW() {
        return Math.min(600, width - 16);
    }

    private int winH() {
        return Math.min(364, height - 16);
    }

    private int winX() {
        return (width - winW()) / 2;
    }

    private int winY() {
        return Math.max(8, (height - winH()) / 2);
    }

    private int rightW() {
        return winW() >= 520 ? 200 : 170;
    }

    // --- ввод ---

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (modeDrop.keyPressed(key)) return true;
        if (pending != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                pending = null;
                capturing = null;
                return true;
            }
            return true;
        }
        if (capturing != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                capturing = null;
                return true;
            }
            if (isModifier(key)) return true;
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                if (!capturing.equals(WHEEL_KEY)) Actions.bind(capturing, null);
                capturing = null;
                return true;
            }
            offer(Binding.of(InputConstants.getKey(key, scan), mods));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!query.isEmpty()) {
                query = "";
                return true;
            }
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
            scroll.reset();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private static boolean isModifier(int key) {
        return key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL || key == GLFW.GLFW_KEY_LEFT_SHIFT
            || key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
            || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (capturing != null || pending != null || Character.isISOControl(c)) return true;
        if (c == '/' && query.isEmpty()) return true;
        query += c;
        scroll.reset();
        return true;
    }

    /** Нажатое сочетание: без конфликта ставится сразу, с конфликтом ждёт ответа. */
    private void offer(Binding b) {
        ResourceLocation who = capturing;
        if (who == null) return;
        if (who.equals(WHEEL_KEY)) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.setKey(Actions.WHEEL, b.key());
            KeyMapping.resetMapping();
            mc.options.save();
            capturing = null;
            return;
        }
        List<String> conflicts = Actions.conflicts(b, who);
        if (conflicts.isEmpty()) {
            Actions.bind(who, b);
            capturing = null;
            return;
        }
        pending = b;
        pendingConflicts = conflicts;
    }

    @Override
    protected boolean onClick(double x, double y, int button) {
        if (capturing != null && pending == null && button >= 2) {
            offer(Binding.of(InputConstants.Type.MOUSE.getOrCreate(button), Screen.hasControlDown(), Screen.hasShiftDown(), Screen.hasAltDown()));
            return true;
        }
        if (hits.click(x, y, button)) return true;
        int s = sectorAt(x, y);
        if (s >= 0) {
            if (button == 1) Actions.setSlot(ring, s, null);
            else selectedSector = selectedSector == s ? -1 : s;
            return true;
        }
        if (capturing != null) {
            capturing = null;
            pending = null;
        }
        modeDrop.close();
        return true;
    }

    @Override
    protected boolean onDrag(double x, double y, int button, double dx, double dy) {
        if (pressed != null && button == 0) {
            dragX = x;
            dragY = y;
            if (!dragMoved && Math.abs(dx) + Math.abs(dy) > 0) dragMoved = true;
            if (dragMoved) dragging = pressed;
            return true;
        }
        return super.onDrag(x, y, button, dx, dy);
    }

    @Override
    protected boolean onRelease(double x, double y, int button) {
        if (dragging != null) {
            int s = sectorAt(x, y);
            if (s >= 0) Actions.setSlot(ring, s, dragging.id());
        }
        dragging = null;
        pressed = null;
        dragMoved = false;
        return true;
    }

    @Override
    protected boolean onScroll(double x, double y, double sx, double sy) {
        if (modeDrop.scrolled(x, y, sy)) return true;
        scroll.by((float) (-sy * ROW_H * 2));
        return true;
    }

    private int sectorAt(double x, double y) {
        double dx = x - wheelCx, dy = y - wheelCy, d = Math.hypot(dx, dy);
        if (d < WIR || d > WR + 2) return -1;
        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 22.5;
        angle = ((angle % 360) + 360) % 360;
        return (int) (angle / 45) % Ring.SECTORS;
    }

    // --- отрисовка ---

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float partialTick) {
        Theme t = ThemeManager.get();
        hits.clear();
        tooltip = null;
        Draw.rect(g, 0, 0, width, height, t.c("overlay"));
        int x = winX(), y = winY(), w = winW(), h = winH();
        Draw.shadow(g, x, y, w, h, 8, 0.7f);
        t.panel(g, x, y, w, h);
        header(g, t, x, y, w, mx, my);
        int pad = 10, top = y + 28 + 10;
        int rw = rightW(), lw = w - pad * 2 - rw - 11;
        List<Action> all = Actions.all();
        left(g, t, all, x + pad, top, lw, y + h - pad - top, mx, my);
        right(g, t, x + w - pad - rw, top, rw, mx, my);
        if (dragging != null) ghost(g, t, dragging);
        if (modeDrop.open) {
            List<Kit.Option> options = List.of(new Kit.Option(Component.translatable("ryscore.keys.mode.hold").getString(), -1),
                new Kit.Option(Component.translatable("ryscore.keys.mode.click").getString(), -1));
            Kit.dropdownList(g, t, hits, modeDrop, options, Actions.holdMode() ? 0 : 1, 110, mx, my, i -> Actions.setHoldMode(i == 0));
        }
        if (tooltip != null && dragging == null) Kit.tooltip(g, t, tooltip, mx, my);
    }

    private void header(GuiGraphics g, Theme t, int x, int y, int w, int mx, int my) {
        float cy = y + 14;
        Draw.round(g, x + 8, cy - 7, 14, 14, 3.5f, t.c("state.ok"));
        Icons.draw(g, Icons.Shape.KEY, x + 11, cy - 4, 8, 0xFFFFFFFF);
        Draw.label(g, title.getString(), x + 27, cy, 15, Weight.SEMIBOLD, t.c("text.primary"));
        int cw = 16, sw = Math.min(125, w / 3);
        int cxb = x + w - 8 - cw;
        boolean hot = Kit.over(mx, my, cxb, (int) cy - 8, cw, cw);
        Kit.iconButton(g, t, cxb, (int) cy - 8, cw, hot);
        Draw.cross(g, cxb + 4, (int) cy - 4, hot ? t.c("accent") : t.c("button.fg"));
        hits.add(cxb, (int) cy - 8, cw, cw, b -> onClose());
        int sx = cxb - 4 - sw;
        t.input(g, sx, (int) cy - 8, sw, 16, !query.isEmpty());
        Icons.draw(g, Icons.Shape.MAGNIFIER, sx + 5, cy - 3.5f, 7, t.c("text.muted"));
        if (query.isEmpty()) {
            Draw.label(g, Component.translatable("ryscore.keys.search").getString(), sx + 16, cy, 12.5f, Weight.REGULAR, t.c("text.muted"));
        } else {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            Draw.label(g, Draw.ellipsize(query, sw - 30, 12.5f, Weight.REGULAR) + (blink ? "|" : ""), sx + 16, cy, 12.5f, Weight.REGULAR, t.c("text.primary"));
        }
        Paint.kbd(g, "/", sx + sw - 12, cy, 10.5f, t.c("button.bg"), t.c("button.border"), t.c("text.muted"));
    }

    private boolean matches(Action a) {
        if (group != null && !a.group().id().equals(group)) return false;
        if (query.isEmpty()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        if (a.label().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
        Binding b = Actions.binding(a.id());
        return b != null && b.label().toLowerCase(Locale.ROOT).contains(q);
    }

    private void left(GuiGraphics g, Theme t, List<Action> all, int x, int y, int w, int h, int mx, int my) {
        // вкладки-фильтры по группам
        Map<String, Action.Group> groups = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Action a : all) {
            groups.putIfAbsent(a.group().id(), a.group());
            counts.merge(a.group().id(), 1, Integer::sum);
        }
        // конфликты наших клавиш с чужими: пересчёт раз в полсекунды, клавиш игры сотни
        long now = System.currentTimeMillis();
        if (now - clashesAt > 500) {
            clashes = Actions.clashes();
            clashesAt = now;
        }
        if (clashes.isEmpty() && CLASHES.equals(group)) group = null;
        int px = x;
        px += Kit.pill(g, t, hits, px, y, Component.translatable("ryscore.keys.all", all.size()).getString(), group == null, mx, my, () -> {
            group = null;
            scroll.reset();
        }) + 3;
        if (!clashes.isEmpty()) {
            px += Kit.pill(g, t, hits, px, y, Component.translatable("ryscore.keys.clashes", clashes.size()).getString(), CLASHES.equals(group), mx, my, () -> {
                group = CLASHES.equals(group) ? null : CLASHES;
                scroll.reset();
            }) + 3;
        }
        for (var e : groups.entrySet()) {
            String label = e.getValue().label().getString() + " · " + counts.get(e.getKey());
            int pw = (int) Math.ceil(Draw.labelWidth(label, 12.5f, Weight.MEDIUM)) + 12;
            if (px + pw > x + w) break;
            String id = e.getKey();
            px += Kit.pill(g, t, hits, px, y, label, id.equals(group), mx, my, () -> {
                group = id.equals(group) ? null : id;
                scroll.reset();
            }) + 3;
        }
        // список
        int ly = y + 19, lh = h - 19;
        t.card(g, x, ly, w, lh);
        List<Action> shown = new ArrayList<>();
        boolean onlyClashes = CLASHES.equals(group);
        if (!onlyClashes) for (Action a : all) if (matches(a)) shown.add(a);
        // блок «Конфликты» сверху: во «Всех» без поиска и в своём фильтре
        List<Actions.Clash> clashRows = (onlyClashes || group == null && query.isEmpty()) ? clashes : List.of();
        float content = clashRows.isEmpty() ? 0 : GROUP_H + clashRows.size() * ROW_H;
        String last = null;
        for (Action a : shown) {
            if (!a.group().id().equals(last)) {
                content += GROUP_H;
                last = a.group().id();
            }
            content += ROW_H;
            if (a.id().equals(capturing) && pending != null) content += 34;
        }
        float off = scroll.update(content + 6 - lh);
        int top = ly + 1, bottom = ly + lh - 1;
        Draw.scissor(g, x, top, x + w, bottom);
        hits.clip(top, bottom);
        float ry = ly + 3 - off;
        if (!clashRows.isEmpty()) {
            if (ry + GROUP_H > top && ry < bottom) {
                Kit.capsColored(g, Component.translatable("ryscore.keys.clashes_head").getString(), x + 8, ry + GROUP_H / 2f + 1, 10.5f, 0.42f, t.c("state.warn"));
                String hint = Component.translatable("ryscore.keys.clashes_hint").getString();
                Draw.label(g, hint, x + w - 8 - Draw.labelWidth(hint, 11, Weight.REGULAR), ry + GROUP_H / 2f + 1, 11, Weight.REGULAR, t.c("text.dim"));
            }
            ry += GROUP_H;
            for (Actions.Clash c : clashRows) {
                if (ry + ROW_H > top && ry < bottom) clashRow(g, t, c, x + 3, ry, w - 6, mx, my);
                ry += ROW_H;
            }
        }
        last = null;
        for (Action a : shown) {
            if (!a.group().id().equals(last)) {
                last = a.group().id();
                if (ry + GROUP_H > top && ry < bottom) groupHeader(g, t, shown, a.group(), x + 8, ry + GROUP_H / 2f + 1, w - 16);
                ry += GROUP_H;
            }
            if (ry + ROW_H > top && ry < bottom) row(g, t, a, x + 3, ry, w - 6, mx, my);
            ry += ROW_H;
            if (a.id().equals(capturing) && pending != null) {
                warn(g, t, x + 28, ry + 1, w - 34, mx, my);
                ry += 34;
            }
        }
        hits.unclip();
        g.disableScissor();
        if (shown.isEmpty() && clashRows.isEmpty()) {
            String none = Component.translatable("ryscore.keys.nothing").getString();
            Draw.label(g, none, x + 10, ly + 12, 12.5f, Weight.REGULAR, t.c("text.muted"));
            if (!query.isEmpty() || group != null) {
                String reset = Component.translatable("ryscore.keys.show_all").getString();
                float rx = x + 10 + Draw.labelWidth(none, 12.5f, Weight.REGULAR) + 4;
                Draw.label(g, reset, rx, ly + 12, 12.5f, Weight.REGULAR, t.c("link"));
                hits.add((int) rx, ly + 6, (int) Draw.labelWidth(reset, 12.5f, Weight.REGULAR), 12, b -> {
                    query = "";
                    group = null;
                });
            }
        }
    }

    /** Строка конфликта: наша клавиша, у кого ещё она, «Снять нашу». */
    private void clashRow(GuiGraphics g, Theme t, Actions.Clash c, float x, float y, float w, int mx, int my) {
        boolean hot = Kit.over(mx, my, (int) x, (int) y, (int) w, (int) ROW_H);
        if (hot) Draw.round(g, x, y + 0.5f, w, ROW_H - 1, 4, Draw.alpha(t.c("row.selected.bg"), 0.5f));
        float cy = y + ROW_H / 2f;
        Draw.roundBox(g, x + 5, cy - 7, 14, 14, 3.5f, t.c("slot.bg"), t.c("card.border"));
        Icons.draw(g, Icons.Shape.WARN, x + 8, cy - 4, 8, t.c("state.warn"));
        String unbind = Component.translatable("ryscore.keys.unbind").getString();
        int bw = (int) Kit.buttonWidth(unbind, 12) - 4;
        int bx = (int) (x + w - 4 - bw);
        float kw = Paint.kbdWidth(c.key(), 11);
        float kx = bx - 8 - kw;
        float room = kx - (x + 27) - 6;
        String name = Draw.ellipsize(c.label(), room, 13, Weight.MEDIUM);
        String others = String.join(", ", c.others());
        String sub = Draw.ellipsize(others, room, 11, Weight.REGULAR);
        Draw.label(g, name, x + 27, cy - 3.5f, 13, Weight.MEDIUM, t.c("text.primary"));
        Draw.label(g, sub, x + 27, cy + 4.5f, 11, Weight.REGULAR, t.c("text.muted"));
        if (Kit.over(mx, my, (int) x + 27, (int) y, (int) room, (int) ROW_H)) {
            tooltip = List.of(Component.translatable("ryscore.keys.busy", c.key()).getString(), others,
                Component.translatable("ryscore.keys.all_fire").getString());
        }
        Paint.kbd(g, c.key(), kx, cy, 11, t.c("button.bg"), t.c("button.border"), t.c("text.body"));
        Kit.button(g, t, hits, bx, (int) cy - 6, bw, 12, unbind, false, mx, my, () -> {
            c.unbind();
            clashesAt = 0;
        });
    }

    private void groupHeader(GuiGraphics g, Theme t, List<Action> shown, Action.Group grp, float x, float cy, float w) {
        Kit.caps(g, t, grp.label().getString(), x, cy, 10.5f, 0.42f);
        Set<String> mods = new LinkedHashSet<>();
        for (Action a : shown) if (a.group().equals(grp)) mods.add(Actions.modName(a.id().getNamespace()));
        String m = String.join(", ", mods);
        Draw.label(g, m, x + w - Draw.labelWidth(m, 11, Weight.REGULAR), cy, 11, Weight.REGULAR, t.c("text.dim"));
    }

    private void row(GuiGraphics g, Theme t, Action a, float x, float y, float w, int mx, int my) {
        boolean sel = a.id().equals(capturing);
        boolean hot = Kit.over(mx, my, (int) x, (int) y, (int) w, (int) ROW_H);
        if (sel) Draw.round(g, x, y + 0.5f, w, ROW_H - 1, 4, t.c("row.selected.bg"));
        else if (hot) Draw.round(g, x, y + 0.5f, w, ROW_H - 1, 4, Draw.alpha(t.c("row.selected.bg"), 0.5f));
        float cy = y + ROW_H / 2f;
        Draw.roundBox(g, x + 5, cy - 7, 14, 14, 3.5f, t.c("slot.bg"), t.c("card.border"));
        Action.State st = a.state();
        boolean dim = st.kind() == Action.State.Kind.OFF;
        Paint.icon(g, a, x + 8, cy - 4, 8, dim ? t.c("text.muted") : t.c("text.primary"));
        float keyX = x + Math.max(150, w * 0.58f), slotX = x + w - 48;
        float nameRoom = keyX - (x + 27) - 6;
        String name = a.label().getString(), shownName = Draw.ellipsize(name, nameRoom, 13, Weight.MEDIUM);
        Draw.label(g, shownName, x + 27, cy - 3.5f, 13, Weight.MEDIUM, dim ? t.c("text.muted") : t.c("text.primary"));
        String sub = a.detail();
        if (st.kind() == Action.State.Kind.PAUSE) sub = sub.isEmpty() ? st.clock() : sub + " · " + st.clock();
        if (dim && !st.reason().isEmpty()) sub = st.reason();
        if (a.opensList()) sub = Component.translatable("ryscore.keys.list", a.list().size()).getString();
        if (a.opensNested()) sub = Component.translatable("ryscore.keys.nested", a.nested().size()).getString();
        String shownSub = Draw.ellipsize(sub, nameRoom, 11, Weight.REGULAR);
        Draw.label(g, shownSub, x + 27, cy + 4.5f, 11, Weight.REGULAR, t.c("text.muted"));
        if (Kit.over(mx, my, (int) x + 27, (int) y, (int) nameRoom, (int) ROW_H) && (!shownName.equals(name) || !shownSub.equals(sub))) {
            tooltip = sub.isEmpty() ? List.of(name) : List.of(name, sub);
        }
        // перетаскивание за строку
        hits.add((int) x, (int) y, (int) (keyX - x - 4), (int) ROW_H, b -> {
            if (b == 0) {
                pressed = a;
                dragX = mx;
                dragY = my;
                dragMoved = false;
            }
        });
        // клавиша
        if (!a.bindable()) {
            Draw.label(g, "—", keyX, cy, 12, Weight.REGULAR, t.c("text.dim"));
        } else if (sel && pending == null) {
            capture(g, t, keyX, cy);
        } else {
            Binding b = sel && pending != null ? pending : Actions.binding(a.id());
            if (b != null) {
                float kw = Paint.kbd(g, b.label(), keyX, cy, 11, t.c("button.bg"), t.c("button.border"), t.c("text.body"));
                hits.add((int) keyX, (int) cy - 6, (int) kw, 12, btn -> startCapture(a.id()));
            } else {
                String assign = Component.translatable("ryscore.keys.assign").getString();
                boolean khot = Kit.over(mx, my, (int) keyX, (int) cy - 6, (int) Draw.labelWidth(assign, 12.5f, Weight.REGULAR), 12);
                Draw.label(g, assign, keyX, cy, 12.5f, Weight.REGULAR, khot ? t.c("text.primary") : t.c("text.muted"));
                hits.add((int) keyX, (int) cy - 6, (int) Draw.labelWidth(assign, 12.5f, Weight.REGULAR) + 2, 12, btn -> startCapture(a.id()));
            }
        }
        // сектор колеса
        int slot = Actions.slotOf(ring, a.id());
        if (slot >= 0) {
            String n = Integer.toString(slot + 1);
            float sw = 8 + 4 + Draw.labelWidth(n, 11.5f, Weight.MEDIUM) + 8;
            Draw.roundBox(g, slotX, cy - 6, sw, 12, 3, t.c("button.bg"), t.c("button.border"));
            Icons.draw(g, Icons.Shape.WHEEL, slotX + 4, cy - 3, 6, t.c("text.secondary"));
            Draw.label(g, n, slotX + 12, cy, 11.5f, Weight.MEDIUM, t.c("text.secondary"));
            hits.add((int) slotX, (int) cy - 6, (int) sw, 12, btn -> Actions.setSlot(ring, slot, null));
            if (Kit.over(mx, my, (int) slotX, (int) cy - 6, (int) sw, 12)) tooltip = List.of(Component.translatable("ryscore.keys.from_wheel").getString());
        } else {
            String label = Component.translatable("ryscore.keys.to_wheel").getString();
            float sw = Draw.labelWidth(label, 11.5f, Weight.REGULAR) + 10;
            boolean shot = Kit.over(mx, my, (int) slotX, (int) cy - 6, (int) sw, 12);
            Draw.roundBorder(g, slotX, cy - 6, sw, 12, 3, shot ? t.c("text.muted") : t.c("card.border"));
            Draw.label(g, label, slotX + 5, cy, 11.5f, Weight.REGULAR, shot ? t.c("text.primary") : t.c("text.muted"));
            hits.add((int) slotX, (int) cy - 6, (int) sw, 12, btn -> toWheel(a));
        }
    }

    private void startCapture(ResourceLocation id) {
        capturing = id;
        pending = null;
    }

    /** «в колесо»: выбранный сектор, иначе первый пустой. */
    private void toWheel(Action a) {
        List<Action> slots = Actions.slots(ring);
        int target = selectedSector;
        if (target < 0) for (int i = 0; i < slots.size(); i++) if (slots.get(i) == null) {
            target = i;
            break;
        }
        if (target < 0) {
            tooltip = List.of(Component.translatable("ryscore.keys.wheel_full").getString());
            return;
        }
        Actions.setSlot(ring, target, a.id());
        selectedSector = -1;
    }

    private void capture(GuiGraphics g, Theme t, float x, float cy) {
        String hint = Component.translatable("ryscore.keys.press").getString();
        String mods = (Screen.hasControlDown() ? "Ctrl+" : "") + (Screen.hasShiftDown() ? "Shift+" : "") + (Screen.hasAltDown() ? "Alt+" : "");
        float w = 9 + (mods.isEmpty() ? 0 : Paint.kbdWidth(mods + "…", 10.5f) + 4) + Draw.labelWidth(hint, 11.5f, Weight.REGULAR) + 8;
        Draw.roundBox(g, x - 2, cy - 7, w, 14, 3.5f, t.c("input.bg"), t.c("text.primary"));
        Draw.disc(g, x + 3.5f, cy, 2, t.c("state.bad"));
        float pen = x + 8;
        if (!mods.isEmpty()) pen += Paint.kbd(g, mods + "…", pen, cy, 10.5f, t.c("button.bg"), t.c("button.border"), t.c("text.body")) + 4;
        Draw.label(g, hint, pen, cy, 11.5f, Weight.REGULAR, t.c("text.muted"));
    }

    private void warn(GuiGraphics g, Theme t, float x, float y, float w, int mx, int my) {
        Binding b = pending;
        if (b == null) return;
        float h = 31;
        Draw.roundBox(g, x, y, w, h, 4, t.c("callout.bg"), t.c("callout.border"));
        Icons.draw(g, Icons.Shape.WARN, x + 5, y + 4, 7.5f, t.c("state.warn"));
        String head = Component.translatable("ryscore.keys.busy", b.label()).getString();
        String list = String.join(", ", pendingConflicts) + ". " + Component.translatable("ryscore.keys.all_fire").getString();
        float tx = x + 16, room = w - 22;
        Draw.label(g, Draw.ellipsize(head + " " + list, room, 12, Weight.REGULAR), tx, y + 7.5f, 12, Weight.REGULAR, t.c("text.primary"));
        if (Kit.over(mx, my, (int) tx, (int) y + 2, (int) room, 11)) tooltip = List.of(head, list);
        int by = (int) (y + 15);
        String keep = Component.translatable("ryscore.keys.keep").getString(), other = Component.translatable("ryscore.keys.other").getString();
        int kw = (int) Kit.buttonWidth(keep, 12) - 4;
        Kit.button(g, t, hits, (int) tx, by, kw, 12, keep, false, mx, my, () -> {
            if (capturing != null) Actions.bind(capturing, b);
            pending = null;
            capturing = null;
        });
        float ox = tx + kw + 6;
        boolean ohot = Kit.over(mx, my, (int) ox, by, (int) Draw.labelWidth(other, 12, Weight.REGULAR) + 4, 12);
        Draw.label(g, other, ox, by + 6, 12, Weight.REGULAR, ohot ? t.c("text.primary") : t.c("text.muted"));
        hits.add((int) ox, by, (int) Draw.labelWidth(other, 12, Weight.REGULAR) + 4, 12, btn -> pending = null);
        String esc = Component.translatable("ryscore.keys.esc").getString();
        Draw.label(g, esc, x + w - 6 - Draw.labelWidth(esc, 11.5f, Weight.REGULAR), by + 6, 11.5f, Weight.REGULAR, t.c("text.muted"));
    }

    private void right(GuiGraphics g, Theme t, int x, int y, int w, int mx, int my) {
        // кольца
        Kit.caps(g, t, Component.translatable("ryscore.keys.wheel").getString(), x, y + 7, 10.5f, 0.42f);
        List<Ring> rings = new ArrayList<>();
        for (Ring r : Actions.rings()) if (r.available()) rings.add(r);
        List<Kit.Segment> segs = new ArrayList<>(rings.size());
        int sel = -1;
        for (int i = 0; i < rings.size(); i++) {
            segs.add(Kit.Segment.of(rings.get(i).label().getString()));
            if (rings.get(i).id().equals(ring)) sel = i;
        }
        float segW = Kit.segmentedWidth(t, segs);
        Kit.segmented(g, t, hits, x + w - segW - 2, y - 1, 16, segs, sel, mx, my, i -> {
            ring = rings.get(i).id();
            selectedSector = -1;
        });
        // колесо
        wheelCx = x + w / 2f;
        wheelCy = y + 26 + WR + 12;
        List<Action> slots = Actions.slots(ring);
        int hover = sectorAt(mx, my);
        for (int i = 0; i < Ring.SECTORS; i++) sector(g, t, i, slots.get(i), i == hover || i == selectedSector, mx, my);
        Draw.disc(g, wheelCx, wheelCy, WIR - 2, t.c("column.bg"));
        Draw.ring(g, wheelCx, wheelCy, WIR - 2, 0.5f, t.c("card.border"));
        Ring current = Actions.ringById(ring);
        String rn = current == null ? "" : current.label().getString();
        Draw.label(g, rn, wheelCx - Draw.labelWidth(rn, 11, Weight.REGULAR) / 2f, wheelCy, 11, Weight.REGULAR, t.c("text.muted"));
        if (hover >= 0) {
            Action a = slots.get(hover);
            tooltip = List.of(a == null ? Component.translatable("ryscore.keys.sector_empty", hover + 1).getString()
                : Component.translatable("ryscore.keys.sector", hover + 1, a.label().getString()).getString());
        }
        // клавиша колеса и режим
        float ky = wheelCy + WR + 16;
        Draw.label(g, Component.translatable("ryscore.keys.wheel_key").getString(), x, ky, 13, Weight.MEDIUM, t.c("text.primary"));
        Draw.label(g, Component.translatable("ryscore.keys.wheel_key_hint").getString(), x, ky + 7.5f, 11, Weight.REGULAR, t.c("text.muted"));
        String mode = Actions.holdMode() ? Component.translatable("ryscore.keys.mode.hold").getString() : Component.translatable("ryscore.keys.mode.click").getString();
        int dw = Kit.dropdownWidth(mode, 110);
        int dx = x + w - dw;
        Kit.dropdown(g, t, hits, modeDrop, dx, (int) ky - 7, 110, mode, !Actions.holdMode(), mx, my);
        boolean wheelCapture = WHEEL_KEY.equals(capturing);
        String wk = wheelCapture ? "…" : Actions.WHEEL.isUnbound() ? "—" : Binding.keyLabel(Binding.keyOf(Actions.WHEEL));
        float kw = Paint.kbdWidth(wk, 11);
        float kx = dx - 6 - kw;
        Paint.kbd(g, wk, kx, ky, 11, t.c("button.bg"), wheelCapture ? t.c("text.primary") : t.c("button.border"), t.c("text.body"));
        hits.add((int) kx, (int) ky - 6, (int) kw, 12, b -> startCapture(WHEEL_KEY));
        // свободные клавиши
        float fy = ky + 21;
        Kit.caps(g, t, Component.translatable("ryscore.keys.free").getString(), x, fy, 10.5f, 0.42f);
        String nobody = Component.translatable("ryscore.keys.free_hint").getString();
        Draw.label(g, nobody, x + w - Draw.labelWidth(nobody, 11, Weight.REGULAR), fy, 11, Weight.REGULAR, t.c("text.dim"));
        float cx = x, cy = fy + 11;
        for (InputConstants.Key k : Actions.freeKeys()) {
            String label = Binding.keyLabel(k);
            float cw = Paint.kbdWidth(label, 12) + 4;
            if (cx + cw > x + w) {
                cx = x;
                cy += 14;
            }
            boolean hot = Kit.over(mx, my, (int) cx, (int) cy - 6, (int) cw, 12);
            Draw.roundBox(g, cx, cy - 6, cw, 12, 3, hot && capturing != null ? t.c("button.hover") : t.c("button.bg"), t.c("button.border"));
            Draw.label(g, label, cx + 5, cy, 12, Weight.MEDIUM, t.c("text.body"));
            if (capturing != null && pending == null) hits.add((int) cx, (int) cy - 6, (int) cw, 12, b -> offer(Binding.of(k, false, false, false)));
            cx += cw + 3;
        }
        // врезка с пояснением
        float ny = cy + 12;
        String note = Component.translatable("ryscore.keys.note").getString();
        List<String> lines = Draw.wrapLabel(note, w - 24, 12, Weight.REGULAR, 4);
        float nh = lines.size() * 8 + 9;
        Draw.roundBox(g, x, ny, w, nh, 4, t.c("callout.bg"), t.c("card.border"));
        Draw.info(g, x + 6, ny + 5, 7, t.c("text.muted"));
        for (int i = 0; i < lines.size(); i++) Draw.label(g, lines.get(i), x + 17, ny + 8.5f + i * 8, 12, Weight.REGULAR, t.c("text.secondary"));
    }

    private void sector(GuiGraphics g, Theme t, int i, @Nullable Action a, boolean on, int mx, int my) {
        float a0 = -90 - 22.5f + i * 45 + GAP / 2f, sweep = 45 - GAP;
        int fill = on ? t.c("tab.active.bg") : a == null ? t.c("column.bg") : t.c("card.bg");
        int line = on ? t.c("text.secondary") : t.c("card.border");
        Draw.arc(g, wheelCx, wheelCy, (WR + WIR) / 2f, a0, sweep, WR - WIR, fill);
        Draw.arc(g, wheelCx, wheelCy, WR - 0.25f, a0, sweep, 0.5f, line);
        Draw.arc(g, wheelCx, wheelCy, WIR + 0.25f, a0, sweep, 0.5f, line);
        double rad = Math.toRadians(-90 + i * 45);
        float r = (WR + WIR) / 2f;
        float x = wheelCx + (float) Math.cos(rad) * r, y = wheelCy + (float) Math.sin(rad) * r;
        if (a == null) {
            String n = Integer.toString(i + 1);
            Draw.label(g, n, x - Draw.labelWidth(n, 11, Weight.REGULAR) / 2f, y, 11, Weight.REGULAR, t.c("text.dim"));
            return;
        }
        boolean dim = a.state().kind() == Action.State.Kind.OFF;
        Draw.roundBox(g, x - 7.5f, y - 11, 15, 15, 4, t.c("slot.bg"), t.c("card.border"));
        Paint.icon(g, a, x - 3.75f, y - 7.25f, 7.5f, dim ? t.c("text.muted") : t.c("text.primary"));
        String full = a.label().getString();
        String label = Draw.labelWidth(full, 11, Weight.MEDIUM) <= 36 ? full : Draw.ellipsize(a.shortLabel().getString(), 36, 11, Weight.MEDIUM);
        Draw.label(g, label, x - Draw.labelWidth(label, 11, Weight.MEDIUM) / 2f, y + 9, 11, Weight.MEDIUM, dim ? t.c("text.muted") : t.c("text.primary"));
        int badge = a.badge();
        if (badge > 0) {
            String b = badge > 99 ? "99+" : Integer.toString(badge);
            float bw = Math.max(8, Draw.labelWidth(b, 9.5f, Weight.SEMIBOLD) + 4);
            Draw.round(g, x + 7.5f - bw / 2f, y - 14, bw, 8, 4, t.c("button.bg"));
            Draw.label(g, b, x + 7.5f - Draw.labelWidth(b, 9.5f, Weight.SEMIBOLD) / 2f, y - 10, 9.5f, Weight.SEMIBOLD, t.c("text.primary"));
        }
    }

    private void ghost(GuiGraphics g, Theme t, Action a) {
        String label = a.label().getString();
        float w = Draw.labelWidth(label, 12.5f, Weight.MEDIUM) + 22, h = 15;
        float x = (float) dragX + 6, y = (float) dragY - 8;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Draw.shadow(g, x, y, w, h, 4, 0.6f);
        Draw.roundBox(g, x, y, w, h, 4, t.c("button.bg"), t.c("text.secondary"));
        Paint.icon(g, a, x + 5, y + 4, 7, t.c("text.primary"));
        Draw.label(g, label, x + 16, y + h / 2f, 12.5f, Weight.MEDIUM, t.c("text.primary"));
        g.pose().popPose();
    }
}
