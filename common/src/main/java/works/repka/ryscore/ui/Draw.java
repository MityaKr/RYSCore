package works.repka.ryscore.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import works.repka.ryscore.theme.ThemeManager;

/**
 * Примитивы отрисовки. Скруглённые фигуры рисуются в пикселях экрана, а не GUI,
 * поэтому углы остаются гладкими при любом GUI Scale.
 */
public final class Draw {
    private Draw() {}

    /** Пикселей экрана в единице: внутри {@link UiScale#render} это масштаб интерфейса. */
    private static double scaleOverride;

    public static double scale() {
        return scaleOverride > 0 ? scaleOverride : Minecraft.getInstance().getWindow().getGuiScale();
    }

    static double pushScale(double pixels) {
        double saved = scaleOverride;
        scaleOverride = pixels;
        return saved;
    }

    static void popScale(double saved) {
        scaleOverride = saved;
    }

    /** Область обрезки в единицах холста. Закрывается {@code g.disableScissor()}. */
    public static void scissor(GuiGraphics g, int minX, int minY, int maxX, int maxY) {
        if (g instanceof ScaledGraphics scaled) scaled.scissorUi(minX, minY, maxX, maxY);
        else g.enableScissor(minX, minY, maxX, maxY);
    }

    public static int alpha(int argb, float mul) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, mul)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
            | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int argb) {
        if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, argb);
    }

    /** Скруглённый прямоугольник. Координаты и радиус в единицах GUI. */
    public static void round(GuiGraphics g, float x, float y, float w, float h, float radius, int argb) {
        if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
        if (radius <= 0.01f) {
            rect(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), argb);
            return;
        }
        double s = scale();
        int px = (int) Math.round(x * s), py = (int) Math.round(y * s);
        int pw = (int) Math.round((x + w) * s) - px, ph = (int) Math.round((y + h) * s) - py;
        int r = (int) Math.round(radius * s);
        begin(g, s);
        g.drawManaged(() -> fillRound(g, px, py, pw, ph, r, argb));
        end(g);
    }

    /** Рамка скруглённого прямоугольника толщиной в один пиксель GUI (не меньше одного пикселя экрана). */
    public static void roundBorder(GuiGraphics g, float x, float y, float w, float h, float radius, int argb) {
        roundBorder(g, x, y, w, h, radius, argb, 0.5f);
    }

    public static void roundBorder(GuiGraphics g, float x, float y, float w, float h, float radius, int argb, float thickness) {
        if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
        double s = scale();
        int px = (int) Math.round(x * s), py = (int) Math.round(y * s);
        int pw = (int) Math.round((x + w) * s) - px, ph = (int) Math.round((y + h) * s) - py;
        int r = (int) Math.round(radius * s);
        int t = Math.max(1, (int) Math.round(thickness * s));
        begin(g, s);
        g.drawManaged(() -> ring(g, px, py, pw, ph, r, t, argb));
        end(g);
    }

    /** Заливка с рамкой за один вызов. */
    /** Значок «?» размером 10×10: подсказка по синтаксису при наведении. */
    public static void question(GuiGraphics g, int x, int y, int color) {
        roundBorder(g, x, y, 10, 10, 5f, color);
        textScaled(g, "?", x + 3, y + 2, 0.75f, color);
    }

    /** Значок «i» размером 10×10: рядом с ним живёт подсказка при наведении. */
    public static void info(GuiGraphics g, int x, int y, int color) {
        roundBorder(g, x, y, 10, 10, 5f, color);
        rect(g, x + 4, y + 2, 2, 2, color);
        rect(g, x + 4, y + 5, 2, 3, color);
    }

    public static void roundBox(GuiGraphics g, float x, float y, float w, float h, float radius, int fill, int border) {
        round(g, x, y, w, h, radius, fill);
        roundBorder(g, x, y, w, h, radius, border);
    }

    /** Мягкая тень под панелью. */
    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float radius, float strength) {
        if (strength <= 0) return;
        // как box-shadow: 0 30px 80px rgba(0,0,0,.55) в макете, при strength 1
        int layers = 28;
        float spread = 20f * strength, offset = 15f * strength;
        for (int i = 0; i < layers; i++) {
            float t = (i + 0.5f) / layers;
            float e = (t * 2f - 1f) * spread;
            float r = Math.max(0.5f, radius + e);
            if (w + e * 2 <= 0 || h + e * 2 <= 0) continue;
            round(g, x - e, y - e + offset, w + e * 2, h + e * 2, r, alpha(0xFF000000, 0.55f * Math.min(1f, strength + 0.15f) / layers));
        }
    }

    /** Ванильный бевель: светлая кромка сверху-слева, тёмная снизу-справа. Для слотов передаётся inset = true. */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, int fill, int light, int dark, int t, boolean inset) {
        int tl = inset ? dark : light;
        int br = inset ? light : dark;
        rect(g, x, y, w, h, fill);
        rect(g, x, y, w - t, t, tl);
        rect(g, x, y, t, h - t, tl);
        rect(g, x + t, y + h - t, w - t, t, br);
        rect(g, x + w - t, y + t, t, h - t, br);
    }

    /** Кегль обычного текста в шрифте темы: близок по величине к шрифту игры, чтобы старые раскладки не сломались. */
    private static final float DEFAULT_PX = 15.3f;

    private static boolean useType() {
        return ThemeManager.get().usesType() && Type.available();
    }

    public static void text(GuiGraphics g, String s, int x, int y, int argb) {
        if (useType()) {
            Type.draw(g, s, x, y + 3.5f, DEFAULT_PX, Type.Weight.REGULAR, argb);
            return;
        }
        g.drawString(Minecraft.getInstance().font, s, x, y, argb, false);
    }

    public static void text(GuiGraphics g, Component s, int x, int y, int argb) {
        if (useType()) {
            Type.draw(g, s.getString(), x, y + 3.5f, DEFAULT_PX, Type.Weight.REGULAR, argb);
            return;
        }
        g.drawString(Minecraft.getInstance().font, s, x, y, argb, false);
    }

    public static void textCentered(GuiGraphics g, String s, int cx, int y, int argb) {
        if (useType()) {
            Type.draw(g, s, cx - width(s) / 2f, y + 3.5f, DEFAULT_PX, Type.Weight.REGULAR, argb);
            return;
        }
        Font font = Minecraft.getInstance().font;
        g.drawString(font, s, cx - font.width(s) / 2, y, argb, false);
    }

    /** Текст по макету: кегль в пикселях макета, y это вертикальная середина строки. */
    public static void label(GuiGraphics g, String s, float x, float centerY, float designPx, Type.Weight weight, int argb) {
        if (useType()) {
            Type.draw(g, s, x, centerY, designPx, weight, argb);
            return;
        }
        float k = designPx / 16f;
        g.pose().pushPose();
        g.pose().translate(x, centerY - 3.5f * k, 0);
        g.pose().scale(k, k, 1f);
        g.drawString(Minecraft.getInstance().font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Ширина в единицах GUI текста по макету. */
    public static float labelWidth(String s, float designPx, Type.Weight weight) {
        if (useType()) return Type.width(s, designPx, weight);
        return Minecraft.getInstance().font.width(s) * designPx / 16f;
    }

    /** Ширина обычного текста в шрифте темы. */
    public static int width(String s) {
        if (useType()) return (int) Math.ceil(Type.width(s, DEFAULT_PX, Type.Weight.REGULAR));
        return Minecraft.getInstance().font.width(s);
    }

    public static int width(Component s) {
        return width(s.getString());
    }

    /** Ширина текста, нарисованного через {@link #textScaled}. */
    public static float widthScaled(String s, float scale) {
        if (useType()) return Type.width(s, DEFAULT_PX * scale, Type.Weight.REGULAR);
        return Minecraft.getInstance().font.width(s) * scale;
    }

    /** Перенос текста по словам под ширину в единицах GUI. */
    public static java.util.List<String> wrap(String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && width(next) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** Текст, уменьшенный в scale раз (для подписей и бейджей). */
    public static void textScaled(GuiGraphics g, String s, float x, float y, float scale, int argb) {
        if (useType()) {
            Type.draw(g, s, x, y + 3.5f * scale, DEFAULT_PX * scale, Type.Weight.REGULAR, argb);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(Minecraft.getInstance().font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    public static String ellipsize(String s, int maxWidth) {
        if (useType()) return ellipsize(s, maxWidth, DEFAULT_PX, Type.Weight.REGULAR);
        Font font = Minecraft.getInstance().font;
        if (font.width(s) <= maxWidth) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    /** Перенос текста по макету: не больше {@code maxLines} строк, остаток обрезается многоточием. */
    public static java.util.List<String> wrapLabel(String text, float maxWidth, float designPx, Type.Weight weight, int maxLines) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String next = line.length() == 0 ? words[i] : line + " " + words[i];
            if (line.length() > 0 && labelWidth(next, designPx, weight) > maxWidth) {
                if (lines.size() == maxLines - 1) {
                    StringBuilder rest = new StringBuilder(line);
                    for (int k = i; k < words.length; k++) rest.append(' ').append(words[k]);
                    lines.add(ellipsize(rest.toString(), maxWidth, designPx, weight));
                    return lines;
                }
                lines.add(line.toString());
                line = new StringBuilder(words[i]);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) lines.add(ellipsize(line.toString(), maxWidth, designPx, weight));
        return lines;
    }

    /** Обрезка под ширину текста по макету. */
    public static String ellipsize(String s, float maxWidth, float designPx, Type.Weight weight) {
        if (labelWidth(s, designPx, weight) <= maxWidth) return s;
        float room = maxWidth - labelWidth("…", designPx, weight);
        int end = 0;
        float w = 0;
        while (end < s.length()) {
            int cp = s.codePointAt(end);
            float a = labelWidth(Character.toString(cp), designPx, weight);
            if (w + a > room) break;
            w += a;
            end += Character.charCount(cp);
        }
        return s.substring(0, end) + "…";
    }

    /** Стрелка вправо: используется в рецептах и кнопках. */
    public static void arrow(GuiGraphics g, int x, int y, int w, int argb) {
        int mid = y + 4;
        rect(g, x, mid, w - 4, 2, argb);
        for (int i = 0; i < 5; i++) {
            rect(g, x + w - 5 + i, mid - 4 + i, 1, 10 - i * 2, argb);
        }
    }

    /** Лупа 9×9 для полей поиска. */
    public static void magnifier(GuiGraphics g, int x, int y, int color) {
        if (useType()) {
            Icons.draw(g, Icons.Shape.MAGNIFIER, x, y, 8f, color);
            return;
        }
        roundBorder(g, x, y, 7, 7, 3.5f, color, 1f);
        for (int i = 0; i < 3; i++) rect(g, x + 6 + i, y + 6 + i, 1, 1, color);
    }

    /** Крестик 7×7 «закрыть». */
    public static void cross(GuiGraphics g, int x, int y, int color) {
        if (useType()) {
            Icons.draw(g, Icons.Shape.CROSS, x, y, 7.5f, color);
            return;
        }
        for (int i = 0; i < 7; i++) {
            rect(g, x + i, y + i, 1, 1, color);
            rect(g, x + 6 - i, y + i, 1, 1, color);
        }
    }

    /** Звезда 9×9: закладка. Пустая рисуется контуром. */
    public static void star(GuiGraphics g, int x, int y, boolean filled, int color) {
        if (useType()) {
            Icons.draw(g, Icons.Shape.STAR, x, y, 8f, color);
            return;
        }
        final String[] shape = {"....#....", "....#....", "...#.#...", "####.####", ".#.....#.", "..#...#..", "..#.#.#..", ".#.#.#.#.", ".##...##."};
        final String[] solid = {"....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..#####..", ".###.###.", ".##...##."};
        String[] rows = filled ? solid : shape;
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < 9; col++) {
                if (rows[row].charAt(col) == '#') rect(g, x + col, y + row, 1, 1, color);
            }
        }
    }

    /** Круглая точка статуса. */
    public static void dot(GuiGraphics g, int x, int y, int size, int color) {
        round(g, x, y, size, size, size / 2f, color);
    }

    /** Замок 7×8: дужка и корпус. */
    public static void lock(GuiGraphics g, int x, int y, int argb) {
        rect(g, x + 1, y, 1, 4, argb);
        rect(g, x + 5, y, 1, 4, argb);
        rect(g, x + 2, y - 1, 3, 1, argb);
        rect(g, x, y + 3, 7, 5, argb);
        rect(g, x + 3, y + 5, 1, 2, 0xC0000000);
    }

    public static void chevron(GuiGraphics g, int x, int y, boolean right, int argb) {
        for (int i = 0; i < 4; i++) {
            int px = right ? x + i : x + 3 - i;
            rect(g, px, y + i, 1, 1, argb);
            rect(g, px, y + 6 - i, 1, 1, argb);
        }
    }

    private static void begin(GuiGraphics g, double s) {
        g.pose().pushPose();
        float inv = (float) (1.0 / s);
        g.pose().scale(inv, inv, 1f);
    }

    private static void end(GuiGraphics g) {
        g.pose().popPose();
    }

    private static double exactInset(int r, int row) {
        double dy = r - row - 0.5;
        return r - Math.sqrt(r * (double) r - dy * dy);
    }

    private static void fillRound(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        g.fill(x, y + r, x + w, y + h - r, color);
        int a = color >>> 24;
        for (int i = 0; i < r; i++) {
            double exact = exactInset(r, i);
            int dx = (int) Math.ceil(exact);
            g.fill(x + dx, y + i, x + w - dx, y + i + 1, color);
            g.fill(x + dx, y + h - 1 - i, x + w - dx, y + h - i, color);
            int aa = (int) (a * (dx - exact));
            if (aa > 6 && dx > 0) {
                int c2 = (aa << 24) | (color & 0xFFFFFF);
                g.fill(x + dx - 1, y + i, x + dx, y + i + 1, c2);
                g.fill(x + w - dx, y + i, x + w - dx + 1, y + i + 1, c2);
                g.fill(x + dx - 1, y + h - 1 - i, x + dx, y + h - i, c2);
                g.fill(x + w - dx, y + h - 1 - i, x + w - dx + 1, y + h - i, c2);
            }
        }
    }

    private static void ring(GuiGraphics g, int x, int y, int w, int h, int r, int t, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (t * 2 >= Math.min(w, h)) {
            fillRound(g, x, y, w, h, r, color);
            return;
        }
        int corner = Math.max(r, t);
        // прямые участки боковых сторон
        g.fill(x, y + corner, x + t, y + h - corner, color);
        g.fill(x + w - t, y + corner, x + w, y + h - corner, color);
        int ri = Math.max(0, r - t);
        int a = color >>> 24;
        for (int i = 0; i < corner; i++) {
            double exact = i < r ? exactInset(r, i) : 0;
            int outer = (int) Math.ceil(exact);
            int innerRow = i - t;
            int inner;
            if (innerRow < 0) inner = -1;
            else inner = t + (innerRow < ri ? (int) Math.ceil(exactInset(ri, innerRow)) : 0);
            for (int pass = 0; pass < 2; pass++) {
                int yy = pass == 0 ? y + i : y + h - 1 - i;
                if (inner < 0) {
                    g.fill(x + outer, yy, x + w - outer, yy + 1, color);
                } else {
                    g.fill(x + outer, yy, x + inner, yy + 1, color);
                    g.fill(x + w - inner, yy, x + w - outer, yy + 1, color);
                }
                int aa = (int) (a * (outer - exact));
                if (aa > 6 && outer > 0) {
                    int c2 = (aa << 24) | (color & 0xFFFFFF);
                    g.fill(x + outer - 1, yy, x + outer, yy + 1, c2);
                    g.fill(x + w - outer, yy, x + w - outer + 1, yy + 1, c2);
                }
            }
        }
    }
}
