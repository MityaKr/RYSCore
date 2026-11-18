package works.repka.ryscore.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Type.Weight;

/**
 * Уведомление как тост игры: место и очередь даёт {@link ToastComponent}, вид и время считает ядро. Размеры по макету
 * {@code _audit/notify} (вариант A), в единицах интерфейса семьи (одна единица — два пикселя макета).
 */
final class NoticeToast implements Toast {
    /** Ширина карточки: 320 пикселей макета, как тост игры при GUI Scale 2. */
    static final float W = 160;
    private static final float KEY_H = 9, CROSS = 10;
    private static final long AFTER_HOVER = 1500;
    /** Масштаб семьи для уведомлений, как у HUD, не ниже 75 %: полтора пикселя экрана в единице. */
    private static final double MIN_PIXELS = 1.5;
    /** Зелёный шарик опыта: цвет значка, задуманный различимым. */
    private static final int XP_COLOR = 0xFF7ED321;

    Notice notice;
    private int count;
    private final List<ItemStack> icons = new ArrayList<>();
    private long first, last, elapsed, renderedAt;
    private boolean hovered, dismissed, leaving;
    /** Где карточка стояла в последнем кадре, в единицах GUI игры. */
    private float gx, gy;
    private Layout layout;
    private String layoutKey = "";
    private float[] cross;
    private final List<float[]> buttonHits = new ArrayList<>();

    NoticeToast(Notice notice) {
        this.notice = notice;
    }

    /** Новое содержимое с тем же ключом: время заново. @return поместилось ли оно в прежние места колонки */
    boolean replace(Notice next) {
        int before = slotCount();
        notice = next;
        layoutKey = "";
        elapsed = 0;
        return slotCount() == before;
    }

    /** Сводка забирает карточку: на одну больше, её значок в ряд, её действие на клавишу, время заново. */
    void absorb(Notice card) {
        count++;
        if (!card.item.isEmpty()) icons.add(card.item);
        if (card.action != null) notice.action = card.action;
        elapsed = 0;
        layoutKey = "";
    }

    void dismiss() {
        dismissed = true;
    }

    boolean leaving() {
        return leaving || dismissed;
    }

    /** Видна ли карточка сейчас (а не ждёт места в колонке). */
    boolean onScreen() {
        return renderedAt != 0 && Util.getMillis() - renderedAt < 250 && !leaving();
    }

    /** Во сколько раз карточка крупнее единиц семьи: в маленьком окне масштаб не опускается ниже 75 %. */
    private static float zoom() {
        double pixels = UiScale.pixels();
        return pixels < MIN_PIXELS ? (float) (MIN_PIXELS / pixels) : 1f;
    }

    /** Единицы карточки в единицы GUI игры. */
    private static double gui(double units) {
        return UiScale.toGui(units * zoom());
    }

    int[] bounds() {
        Layout l = layout();
        return new int[] {Math.round(gx), Math.round(gy), (int) Math.ceil(gui(l.w)), (int) Math.ceil(gui(l.h))};
    }

    boolean contains(double x, double y) {
        if (!onScreen() || Minecraft.getInstance().screen == null) return false;
        Layout l = layout();
        return x >= gx && y >= gy && x < gx + gui(l.w) && y < gy + gui(l.h);
    }

    boolean click(double x, double y, int button) {
        if (!contains(x, y)) return false;
        float lx = (float) (UiScale.fromGui(x - gx) / zoom()), ly = (float) (UiScale.fromGui(y - gy) / zoom());
        if (button == 1) {
            dismiss();
            return true;
        }
        if (button != 0) return true;
        if (cross != null && inside(cross, lx, ly)) {
            dismiss();
            return true;
        }
        for (int i = 0; i < buttonHits.size() && i < notice.buttons.size(); i++) {
            if (inside(buttonHits.get(i), lx, ly)) {
                notice.buttons.get(i).action().run();
                dismiss();
                return true;
            }
        }
        if (notice.action != null && notice.kind != Notice.Kind.ROW && notice.kind != Notice.Kind.BUTTONS) {
            notice.action.run();
            dismiss();
        }
        return true;
    }

    private static boolean inside(float[] r, float x, float y) {
        return x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3];
    }

    @Override
    public Object getToken() {
        return this;
    }

    @Override
    public int width() {
        return (int) Math.ceil(gui(W));
    }

    @Override
    public int height() {
        return (int) Math.ceil(gui(layout().h));
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent component, long sinceVisible) {
        long now = Util.getMillis();
        if (first == 0) {
            first = now;
            last = now;
            Notices.playOnce(notice.sound);
        }
        long dt = Math.max(0, Math.min(250, now - last));
        last = now;
        renderedAt = now;
        Matrix4f m = g.pose().last().pose();
        gx = m.m30();
        gy = m.m31();
        Minecraft mc = Minecraft.getInstance();
        boolean hover = false;
        if (mc.screen != null) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            Layout l = layout();
            hover = mx >= gx && my >= gy && mx < gx + gui(l.w) && my < gy + gui(l.h);
        }
        if (hover) {
            hovered = true;
        } else {
            if (hovered) elapsed = Math.min(elapsed, Math.max(0, notice.millis - AFTER_HOVER));
            hovered = false;
            elapsed += dt;
        }
        boolean alive = notice.alive != null && notice.alive.getAsBoolean();
        boolean gone = leaving || dismissed || (notice.hidden != null && notice.hidden.getAsBoolean())
            || (notice.alive != null ? !alive : elapsed >= notice.millis);
        leaving = gone;
        long age = now - first;
        float z = zoom(), shift = (float) UiScale.fromGui(width()) / z - W;
        boolean lit = hover;
        UiScale.render(g, s -> {
            s.pose().pushPose();
            s.pose().scale(z, z, 1f);
            s.pose().translate(shift, 0, 0);
            draw(s, ThemeManager.get(), lit, age);
            s.pose().popPose();
        });
        if (gone) Notices.finished(this);
        return gone ? Visibility.HIDE : Visibility.SHOW;
    }

    // --- раскладка ---

    private record Layout(float w, float h, List<String> title, String line, List<String> rowLines) {}

    private Layout layout() {
        Theme t = ThemeManager.get();
        String key = t.id + "|" + UiScale.pixels() + "|" + count + "|" + System.identityHashCode(notice);
        if (layout != null && key.equals(layoutKey)) return layout;
        layoutKey = key;
        layout = switch (notice.kind) {
            case CARD -> {
                float textW = W - 5 - 19 - 5.5f - 6 - reserve();
                if (notice.progress != null) {
                    yield new Layout(W, 37.5f, List.of(Draw.ellipsize(notice.title, textW, 13, Weight.SEMIBOLD)),
                        fit(notice.lines, textW, 11.5f, Weight.REGULAR), List.of());
                }
                List<String> title = Draw.wrapLabel(notice.title, textW, 13, Weight.SEMIBOLD, 2);
                yield new Layout(W, title.size() > 1 ? 37.5f : 29, title, fit(notice.lines, textW, 11.5f, Weight.REGULAR), List.of());
            }
            case BIG -> {
                float header = Math.max(22, 7 + lineOf(notice.titlePx) + 7.5f);
                float row = notice.chips.isEmpty() ? KEY_H : 13;
                float textW = W - 6 - 22 - 6 - 6;
                List<String> title = List.of(Draw.ellipsize(notice.title, textW, notice.titlePx, Weight.SEMIBOLD));
                yield new Layout(W, 6 + header + 5 + row + 6, title, fit(notice.lines, textW, 11.5f, Weight.REGULAR), List.of());
            }
            case SUMMARY -> {
                float textW = W - 12 - reserve();
                String title = notice.summaryTitle == null ? "" : notice.summaryTitle.apply(count);
                yield new Layout(W, 43, List.of(Draw.ellipsize(title, textW, 13, Weight.SEMIBOLD)),
                    Draw.ellipsize(notice.summaryLine, textW, 11.5f, Weight.REGULAR), List.of());
            }
            case ROW -> {
                float textW = W - 4 - 10 - 4.5f - 6 - CROSS - 3;
                List<String> rows = Draw.wrapLabel(notice.title, textW, 12, Weight.MEDIUM, 3);
                yield new Layout(W, Math.max(18, 7 + rows.size() * 8), List.of(), "", rows);
            }
            case BUTTONS -> {
                float textW = W - 5 - 19 - 5.5f - 6;
                yield new Layout(W, 45.5f, List.of(Draw.ellipsize(notice.title, textW, 13, Weight.SEMIBOLD)),
                    fit(notice.lines, textW, 11.5f, Weight.REGULAR), List.of());
            }
        };
        return layout;
    }

    /** Место справа под клавишу или крестик: одно и то же, чтобы перенос названия не менялся при наведении. */
    private float reserve() {
        float key = notice.key != null && notice.action != null ? keyWidth(notice.key.get()) : 0;
        return Math.max(key, CROSS) + 4;
    }

    private static float lineOf(float px) {
        return px <= 14 ? 9 : px <= 15 ? 9.5f : 10;
    }

    /** Первая формулировка, которая помещается; если ни одна, последняя с многоточием. */
    private static String fit(List<String> variants, float width, float px, Weight weight) {
        if (variants.isEmpty()) return "";
        for (String v : variants) {
            if (Draw.labelWidth(v, px, weight) <= width) return v;
        }
        return Draw.ellipsize(variants.getLast(), width, px, weight);
    }

    private static float keyWidth(String label) {
        return Draw.labelWidth(label, 11, Weight.MEDIUM) + 6;
    }

    // --- отрисовка ---

    private void draw(GuiGraphics g, Theme t, boolean hover, long age) {
        Layout l = layout();
        boolean motion = !t.reducedMotion;
        cross = null;
        buttonHits.clear();
        plate(g, t, l.w, l.h, hover);
        switch (notice.kind) {
            case CARD -> card(g, t, l, hover, motion ? age : 10_000);
            case BIG -> big(g, t, l, hover, motion ? age : 10_000);
            case SUMMARY -> summary(g, t, l, hover);
            case ROW -> row(g, t, l, hover);
            case BUTTONS -> buttons(g, t, l);
        }
    }

    private static float radius(Theme t) {
        return t.beveled() ? 0 : t.m("hud.radius", 12) / 2f;
    }

    private static int solid(int argb) {
        return 0xFF000000 | (argb & 0xFFFFFF);
    }

    private static int primary(Theme t) {
        return t.c("hud.fg");
    }

    private static int secondary(Theme t) {
        return t.beveled() ? t.c("hud.fg") : t.c("text.secondary");
    }

    private static int muted(Theme t) {
        return t.c("hud.muted");
    }

    private int rankColor(Theme t) {
        return notice.rank == Notice.Rank.GOLD ? t.c("hud.gold") : t.c("hud.ok");
    }

    private void plate(GuiGraphics g, Theme t, float w, float h, boolean hover) {
        float r = radius(t);
        Draw.shadow(g, 0, 0, w, h, Math.max(r, 2), 0.35f);
        int bg = hover ? (t.beveled() ? Draw.mix(t.c("hud.bg"), solid(t.c("hud.fg")), 0.08f) : Draw.alpha(t.c("card.bg"), 0.97f))
            : Draw.alpha(t.c("hud.bg"), 0.95f);
        int border = hover ? (t.beveled() ? t.c("hud.muted") : t.c("text.dim")) : t.c("hud.border");
        Draw.roundBox(g, 0, 0, w, h, r, bg, border);
    }

    /** Плашка значка: в теме с фаской вдавленная. */
    private static void slot(GuiGraphics g, Theme t, float x, float y, float size, int rank) {
        if (t.beveled()) {
            Draw.bevel(g, x, y, size, size, t.c("slot.bg"), t.c("slot.bevel.dark"), t.c("slot.bevel.light"), 1, false);
            if (rank != 0) Draw.roundBorder(g, x, y, size, size, 0.02f, rank, 1f);
            return;
        }
        float r = t.r("slot.radius");
        Draw.roundBox(g, x, y, size, size, r, t.c("slot.bg"), t.c("slot.border"));
        if (rank != 0) Draw.roundBorder(g, x, y, size, size, r, rank, 0.75f);
    }

    private void icon(GuiGraphics g, float x, float y, float size) {
        if (notice.head != null) {
            face(g, notice.head, x, y, size);
        } else if (!notice.item.isEmpty()) {
            item(g, notice.item, x, y, size);
        } else if (notice.shape != null) {
            Icons.draw(g, notice.shape, x, y, size, secondary(ThemeManager.get()));
        }
    }

    static void item(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 16f, size / 16f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    static void face(GuiGraphics g, UUID player, float x, float y, float size) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(player);
        ResourceLocation skin = info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(player).texture();
        Batch.flush();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 8f, size / 8f, 1f);
        PlayerFaceRenderer.draw(g, skin, 0, 0, 8);
        g.pose().popPose();
    }

    /** Отметка на углу значка: галочка цветом ранга или голова товарища; выскакивает до 125 % и садится. */
    private void mark(GuiGraphics g, Theme t, float slotX, float slotY, float slot, int color, long age) {
        float d = 8, cx = slotX + slot + 2.5f - d / 2f, cy = slotY + slot + 2.5f - d / 2f;
        float k = age < 140 ? 0 : age < 230 ? (age - 140) / 90f * 1.25f : age < 320 ? 1.25f - (age - 230) / 90f * 0.25f : 1f;
        if (k <= 0) return;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(k, k, 1f);
        int plate = solid(t.c("hud.bg"));
        if (notice.markHead != null) {
            Draw.round(g, -d / 2f - 0.5f, -d / 2f - 0.5f, d + 1, d + 1, 1.5f, plate);
            face(g, notice.markHead, -d / 2f, -d / 2f, d);
        } else {
            Draw.disc(g, 0, 0, d / 2f + 1, plate);
            Draw.disc(g, 0, 0, d / 2f, color);
            Icons.draw(g, Icons.Shape.CHECK, -2.25f, -2.25f, 4.5f, plate);
        }
        g.pose().popPose();
    }

    private static void key(GuiGraphics g, Theme t, String label, float right, float cy) {
        float w = keyWidth(label);
        if (t.beveled()) {
            Draw.bevel(g, right - w, cy - KEY_H / 2f, w, KEY_H, t.c("button.bg"), t.c("panel.bevel.light"), t.c("button.border"), 0.75f, false);
            Draw.label(g, label, right - w + 3, cy, 11, Weight.MEDIUM, t.c("button.fg"));
            return;
        }
        Draw.roundBox(g, right - w, cy - KEY_H / 2f, w, KEY_H, 2.5f, t.c("button.bg"), t.c("button.border"));
        Draw.label(g, label, right - w + 3, cy, 11, Weight.MEDIUM, muted(t));
    }

    /** Крестик при наведении на месте клавиши. */
    private void cross(GuiGraphics g, Theme t, float right, float cy) {
        float x = right - CROSS, y = cy - CROSS / 2f;
        if (t.beveled()) Draw.bevel(g, x, y, CROSS, CROSS, t.c("button.bg"), t.c("panel.bevel.light"), t.c("button.border"), 0.75f, false);
        else Draw.roundBox(g, x, y, CROSS, CROSS, 2.5f, t.c("button.bg"), t.c("button.border"));
        Icons.draw(g, Icons.Shape.CROSS, x + 2.5f, y + 2.5f, 5, t.beveled() ? t.c("button.fg") : muted(t));
        cross = new float[] {x, y, CROSS, CROSS};
    }

    private void keyOrCross(GuiGraphics g, Theme t, float right, float cy, boolean hover) {
        if (hover) cross(g, t, right, cy);
        else if (notice.key != null && notice.action != null) key(g, t, notice.key.get(), right, cy);
    }

    private void card(GuiGraphics g, Theme t, Layout l, boolean hover, long age) {
        float slot = 19, sx = 5, sy = (l.h - slot) / 2f;
        slot(g, t, sx, sy, slot, 0);
        icon(g, sx + 2.5f, sy + 2.5f, 14);
        float tx = sx + slot + 5.5f;
        if (notice.progress != null) {
            // разогрев: название, полоса, строка «урон отменит»
            float top = (l.h - 8.5f - 6f - 7.5f) / 2f, barW = l.w - tx - 6 - reserve();
            Draw.label(g, l.title.getFirst(), tx, top + 4.25f, 13, Weight.SEMIBOLD, primary(t));
            float part = (float) Math.max(0, Math.min(1, notice.progress.getAsDouble()));
            Draw.round(g, tx, top + 10.5f, barW, 2, 1, t.c("hud.border"));
            if (part > 0) Draw.round(g, tx, top + 10.5f, Math.max(2, barW * part), 2, 1, primary(t));
            Draw.label(g, l.line, tx, top + 14.5f + 3.75f, 11.5f, Weight.REGULAR, notice.lineBright ? secondary(t) : muted(t));
            keyOrCross(g, t, l.w - 6, top + 4.25f, hover);
            return;
        }
        float block = l.title.size() * 8.5f + 7.5f, top = (l.h - block) / 2f;
        for (int i = 0; i < l.title.size(); i++) {
            Draw.label(g, l.title.get(i), tx, top + 4.25f + i * 8.5f, 13, Weight.SEMIBOLD, primary(t));
        }
        Draw.label(g, l.line, tx, top + l.title.size() * 8.5f + 3.75f, 11.5f, Weight.REGULAR, notice.lineBright ? secondary(t) : muted(t));
        if (notice.check || notice.markHead != null) mark(g, t, sx, sy, slot, t.c("hud.ok"), age);
        keyOrCross(g, t, l.w - 6, top + 4.25f, hover);
    }

    private void big(GuiGraphics g, Theme t, Layout l, boolean hover, long age) {
        int rank = rankColor(t);
        float r = radius(t);
        // линия ранга по верхнему краю чертится слева направо за 400 мс
        float p = Math.min(1f, age / 400f), inset = r > 0 ? r * 0.6f : 0;
        Draw.round(g, inset, 0, (l.w - inset * 2) * p, 1, 0.3f, rank);
        float slot = 22, sx = 6, sy = 6;
        slot(g, t, sx, sy, slot, rank);
        icon(g, sx + 3, sy + 3, 16);
        float tx = sx + slot + 6, header = Math.max(22, 7 + lineOf(notice.titlePx) + 7.5f);
        float block = 7 + lineOf(notice.titlePx) + 7.5f, top = sy + (header - block) / 2f;
        Draw.label(g, notice.label.toUpperCase(java.util.Locale.ROOT), tx, top + 3.5f, 10.5f, Weight.SEMIBOLD, rank, 0.42f);
        Draw.label(g, l.title.getFirst(), tx, top + 7 + lineOf(notice.titlePx) / 2f, notice.titlePx, Weight.SEMIBOLD, primary(t));
        Draw.label(g, l.line, tx, top + 7 + lineOf(notice.titlePx) + 3.75f, 11.5f, Weight.REGULAR, notice.lineBright ? secondary(t) : muted(t));
        if (notice.check) mark(g, t, sx, sy, slot, rank, age);
        float ry = sy + header + 5, rowH = notice.chips.isEmpty() ? KEY_H : 13, cy = ry + rowH / 2f;
        if (notice.chips.isEmpty()) {
            Draw.label(g, Draw.ellipsize(notice.note, l.w - 12 - reserve(), 12, Weight.MEDIUM), 6, cy, 12, Weight.MEDIUM, secondary(t));
        } else {
            chips(g, t, 6, ry, age);
        }
        keyOrCross(g, t, l.w - 6, cy, hover);
    }

    private void chips(GuiGraphics g, Theme t, float x, float y, long age) {
        float r = t.beveled() ? 0 : t.r("button.radius") > 15 ? 6.5f : t.r("slot.radius");
        float pen = x, limit = W - 6 - reserve();
        int shown = 0;
        for (Notice.Chip chip : notice.chips) {
            if (shown == 4) break;
            float w = chipWidth(chip);
            if (pen + w > limit) break;
            float a = Math.max(0f, Math.min(1f, (age - 220 - shown * 80) / 120f));
            if (a > 0) chip(g, t, chip, pen, y, w, r, a);
            pen += w + 3;
            shown++;
        }
        int rest = notice.chips.size() - shown;
        if (rest > 0) Draw.label(g, "+" + rest, pen, y + 6.5f, 12, Weight.MEDIUM, secondary(t));
    }

    private static float chipWidth(Notice.Chip chip) {
        float glyph = switch (chip.kind()) {
            case XP, COIN -> 5.5f;
            case ITEM -> 8;
            case RANDOM, CHOICE -> 7;
        };
        float text = chip.text().isEmpty() ? 0 : 2.5f + Draw.labelWidth(chip.text(), 12, Weight.MEDIUM);
        return 3 + glyph + text + (chip.text().isEmpty() ? 3 : 4);
    }

    private static void chip(GuiGraphics g, Theme t, Notice.Chip chip, float x, float y, float w, float r, float alpha) {
        int bg = t.beveled() ? Draw.mix(solid(t.c("hud.bg")), 0xFF000000, 0.45f) : t.c("slot.bg");
        int border = t.beveled() ? t.c("hud.border") : t.c("slot.border");
        Draw.roundBox(g, x, y, w, 13, r, Draw.alpha(bg, alpha * ((bg >>> 24) / 255f)), Draw.alpha(border, alpha * ((border >>> 24) / 255f)));
        float pen = x + 3, cy = y + 6.5f;
        int fg = Draw.alpha(secondary(t), alpha);
        switch (chip.kind()) {
            case XP -> {
                Draw.disc(g, pen + 2.75f, cy, 2.75f, Draw.alpha(XP_COLOR, alpha));
                pen += 5.5f;
            }
            case COIN -> {
                Draw.disc(g, pen + 2.75f, cy, 2.75f, Draw.alpha(t.c("coin"), alpha));
                pen += 5.5f;
            }
            case ITEM -> {
                item(g, chip.item(), pen, cy - 4, 8);
                pen += 8;
            }
            case RANDOM -> {
                Icons.draw(g, Icons.Shape.SPARK, pen, cy - 3.5f, 7, fg);
                pen += 7;
            }
            case CHOICE -> {
                Icons.draw(g, Icons.Shape.HAND, pen, cy - 3.5f, 7, fg);
                pen += 7;
            }
        }
        if (!chip.text().isEmpty()) Draw.label(g, chip.text(), pen + 2.5f, cy, 12, Weight.MEDIUM, fg);
    }

    private void summary(GuiGraphics g, Theme t, Layout l, boolean hover) {
        Draw.label(g, l.title.getFirst(), 6, 5 + 4.25f, 13, Weight.SEMIBOLD, primary(t));
        Draw.label(g, l.line, 6, 5 + 8.5f + 3.75f, 11.5f, Weight.REGULAR, muted(t));
        float pen = 6, y = 25;
        int shown = Math.min(5, icons.size());
        for (int i = icons.size() - shown; i < icons.size(); i++) {
            slot(g, t, pen, y, 12, 0);
            item(g, icons.get(i), pen + 2, y + 2, 8);
            pen += 14.5f;
        }
        int rest = count - shown;
        if (rest > 0) Draw.label(g, "+" + rest, pen + 1.5f, y + 6, 12, Weight.MEDIUM, secondary(t));
        keyOrCross(g, t, l.w - 6, 5 + 4.25f, hover);
    }

    private void row(GuiGraphics g, Theme t, Layout l, boolean hover) {
        float tile = 10, tx = 4, ty = Math.min((l.h - tile) / 2f, 4);
        String src = "src." + notice.source;
        if (t.beveled()) Draw.bevel(g, tx, ty, tile, tile, t.c("slot.bg"), t.c("slot.bevel.dark"), t.c("slot.bevel.light"), 0.75f, false);
        else Draw.round(g, tx, ty, tile, tile, 2.5f, t.c(src + ".bg"));
        int fg = t.beveled() ? t.c("hud.fg") : t.c(src + ".fg");
        if (!notice.item.isEmpty()) item(g, notice.item, tx + 1.5f, ty + 1.5f, 7);
        else Icons.draw(g, notice.shape != null ? notice.shape : Icons.Shape.CHECK, tx + 1.5f, ty + 1.5f, 7, fg);
        float x = tx + tile + 4.5f, first = l.h / 2f - (l.rowLines.size() - 1) * 4f;
        int color = notice.bad ? t.c("state.bad") : primary(t);
        for (int i = 0; i < l.rowLines.size(); i++) Draw.label(g, l.rowLines.get(i), x, first + i * 8, 12, Weight.MEDIUM, color);
        if (hover) cross(g, t, l.w - 6, Math.min(l.h / 2f, 9));
    }

    private void buttons(GuiGraphics g, Theme t, Layout l) {
        float head = 19, hx = 5, hy = 5;
        if (notice.head != null) {
            face(g, notice.head, hx, hy, head);
        } else {
            slot(g, t, hx, hy, head, 0);
            icon(g, hx + 2.5f, hy + 2.5f, 14);
        }
        float tx = hx + head + 5.5f, top = hy + (head - 16) / 2f;
        Draw.label(g, l.title.getFirst(), tx, top + 4.25f, 13, Weight.SEMIBOLD, primary(t));
        Draw.label(g, l.line, tx, top + 8.5f + 3.75f, 11.5f, Weight.REGULAR, muted(t));
        float by = hy + head + 4.5f, bh = 12, pen = 5;
        float r = t.beveled() ? 0 : t.r("button.radius") > 15 ? 6 : 3.5f;
        for (Notice.Button b : notice.buttons) {
            String keyLabel = b == notice.accept ? works.repka.ryscore.action.Answers.acceptKey()
                : b == notice.decline ? works.repka.ryscore.action.Answers.declineKey() : "";
            float keyW = keyLabel.isEmpty() ? 0 : Draw.labelWidth(keyLabel, 10.5f, Weight.SEMIBOLD) + 5;
            float w = Draw.labelWidth(b.label(), 12, Weight.MEDIUM) + 10 + (keyW > 0 ? keyW + 3 : 0);
            if (t.beveled()) {
                Draw.bevel(g, pen, by, w, bh, b.primary() ? t.c("accent") : t.c("button.bg"), t.c("panel.bevel.light"), t.c("button.border"), 1f, false);
            } else if (b.primary()) {
                Draw.round(g, pen, by, w, bh, r, t.c("accent"));
            } else {
                Draw.roundBox(g, pen, by, w, bh, r, t.c("button.bg"), t.c("button.border"));
            }
            int fg = b.primary() ? t.c("accent.text") : t.beveled() ? t.c("button.fg") : primary(t);
            Draw.label(g, b.label(), pen + 5, by + bh / 2f, 12, Weight.MEDIUM, fg);
            if (keyW > 0) {
                // клавиша ответа на кнопке: «Принять [Y]», плашка темнее кнопки
                float kx = pen + w - 3 - keyW, kh = bh - 4;
                int kb = b.primary() ? Draw.alpha(0xFF000000, 0.78f) : t.c("hud.border");
                Draw.round(g, kx, by + 2, keyW, kh, 2, kb);
                Draw.label(g, keyLabel, kx + 2.5f, by + bh / 2f, 10.5f, Weight.SEMIBOLD, b.primary() ? 0xFFFFFFFF : muted(t));
            }
            buttonHits.add(new float[] {pen, by, w, bh});
            pen += w + 3;
        }
        if (notice.timer != null) {
            String timer = notice.timer.get();
            Draw.label(g, timer, l.w - 6 - Draw.labelWidth(timer, 11, Weight.MEDIUM), by + bh / 2f, 11, Weight.MEDIUM,
                t.beveled() ? t.c("hud.muted") : t.c("text.dim"));
        }
    }
}
