package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/** Значки из макетов: фигура задаётся формулой и рисуется в пикселях экрана, поэтому гладкая на любом масштабе. */
public final class Icons {
    public enum Shape { STAR, CROSS, MAGNIFIER, GEAR, ARROW, CHECK, PLUS, PIN, CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_DOWN, GRIP, PENCIL, UNDO, REDO, DOT, HALF_DOT }

    private record Key(Shape shape, int pixels) {}

    private static final Map<Key, Atlas.Region> CACHE = new HashMap<>();
    private static int cacheGeneration = -1;
    private static final int SUPERSAMPLE = 4;
    /** Порог прозрачности прежнего шейдера position_tex_color: края значков остаются такими же. */
    private static final int ALPHA_CUTOFF = 26;

    private Icons() {}

    public static void draw(GuiGraphics g, Shape shape, float x, float y, float size, int argb) {
        int pixels = Math.max(1, Math.round(size * (float) Draw.scale(g)));
        if (!Atlas.fits(pixels, pixels)) return;
        if (cacheGeneration != Atlas.generation) {
            CACHE.clear();
            cacheGeneration = Atlas.generation;
        }
        Key key = new Key(shape, pixels);
        Atlas.Region r = CACHE.get(key);
        if (r == null) {
            r = paint(key);
            if (cacheGeneration != Atlas.generation) {
                CACHE.clear();
                cacheGeneration = Atlas.generation;
            }
            CACHE.put(key, r);
        }
        Batch.quad(g, g.pose().last().pose(), x, y, x + size, y + size, r.u(0), r.v(0), r.u(pixels), r.v(pixels), argb, r.page());
    }

    /** Значок, повёрнутый вокруг центра на {@code degrees}: стрелка направления. */
    public static void drawRotated(GuiGraphics g, Shape shape, float x, float y, float size, float degrees, int argb) {
        g.pose().pushPose();
        g.pose().translate(x + size / 2f, y + size / 2f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        draw(g, shape, -size / 2f, -size / 2f, size, argb);
        g.pose().popPose();
    }

    private static Atlas.Region paint(Key key) {
        int n = key.pixels;
        Atlas.Region r = Atlas.place(n, n);
        NativeImage image = Atlas.pixels(r);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int hits = 0;
                for (int sy = 0; sy < SUPERSAMPLE; sy++) {
                    for (int sx = 0; sx < SUPERSAMPLE; sx++) {
                        double u = (x + (sx + 0.5) / SUPERSAMPLE) / n, v = (y + (sy + 0.5) / SUPERSAMPLE) / n;
                        if (inside(key.shape, u, v)) hits++;
                    }
                }
                int alpha = hits * 255 / (SUPERSAMPLE * SUPERSAMPLE);
                if (alpha < ALPHA_CUTOFF) alpha = 0;
                image.setPixelRGBA(r.x() + x, r.y() + y, alpha << 24 | 0xFFFFFF);
            }
        }
        Atlas.mark(r);
        return r;
    }

    private static boolean inside(Shape shape, double u, double v) {
        return switch (shape) {
            case STAR -> star(u, v);
            case CHEVRON_RIGHT -> segment(u, v, 0.36, 0.18, 0.68, 0.5) < 0.075 || segment(u, v, 0.68, 0.5, 0.36, 0.82) < 0.075;
            case CHEVRON_LEFT -> segment(u, v, 0.64, 0.18, 0.32, 0.5) < 0.075 || segment(u, v, 0.32, 0.5, 0.64, 0.82) < 0.075;
            case CHEVRON_DOWN -> segment(u, v, 0.18, 0.36, 0.5, 0.68) < 0.075 || segment(u, v, 0.5, 0.68, 0.82, 0.36) < 0.075;
            case GRIP -> {
                boolean in = false;
                for (double cx : new double[] {0.36, 0.64}) for (double cy : new double[] {0.22, 0.5, 0.78}) in |= Math.hypot(u - cx, v - cy) < 0.09;
                yield in;
            }
            case PENCIL -> segment(u, v, 0.26, 0.74, 0.74, 0.26) < 0.1 && !(u + v < 0.86 && segment(u, v, 0.26, 0.74, 0.36, 0.64) > 0.06) || segment(u, v, 0.16, 0.84, 0.24, 0.76) < 0.04;
            case UNDO -> arc(u, v, true);
            case REDO -> arc(1 - u, v, true);
            case DOT -> Math.hypot(u - 0.5, v - 0.5) < 0.4;
            case HALF_DOT -> {
                double d = Math.hypot(u - 0.5, v - 0.5);
                yield d < 0.4 && (u >= 0.5 || d > 0.31);
            }
            case PLUS -> segment(u, v, 0.5, 0.16, 0.5, 0.84) < 0.075 || segment(u, v, 0.16, 0.5, 0.84, 0.5) < 0.075;
            case PIN -> (v > 0.12 && v < 0.2 && u > 0.28 && u < 0.72) || (v >= 0.2 && v < 0.5 && u > 0.37 && u < 0.63)
                || (v >= 0.5 && v < 0.6 && u > 0.2 && u < 0.8) || segment(u, v, 0.5, 0.6, 0.5, 0.92) < 0.04;
            case CHECK -> segment(u, v, 0.18, 0.54, 0.42, 0.78) < 0.08 || segment(u, v, 0.42, 0.78, 0.84, 0.26) < 0.08;
            case ARROW -> segment(u, v, 0.14, 0.5, 0.86, 0.5) < 0.04 || segment(u, v, 0.62, 0.27, 0.86, 0.5) < 0.04 || segment(u, v, 0.62, 0.73, 0.86, 0.5) < 0.04;
            case CROSS -> segment(u, v, 0.2, 0.2, 0.8, 0.8) < 0.055 || segment(u, v, 0.8, 0.2, 0.2, 0.8) < 0.055;
            case MAGNIFIER -> {
                double d = Math.hypot(u - 0.42, v - 0.42);
                yield (d < 0.31 && d > 0.22) || segment(u, v, 0.64, 0.64, 0.88, 0.88) < 0.06;
            }
            case GEAR -> {
                double dx = u - 0.5, dy = v - 0.5, d = Math.hypot(dx, dy);
                double angle = Math.atan2(dy, dx) + Math.PI / 8;
                boolean tooth = ((angle + 2 * Math.PI) % (Math.PI / 4)) < Math.PI / 8;
                yield d > 0.15 && d < (tooth ? 0.43 : 0.32);
            }
        };
    }

    /** Дуга со стрелкой влево: значок отмены. Зеркальная даёт повтор. */
    private static boolean arc(double u, double v, boolean head) {
        double d = Math.hypot(u - 0.5, v - 0.58);
        boolean ring = d > 0.24 && d < 0.34 && (v < 0.58 || u > 0.5);
        return ring || segment(u, v, 0.22, 0.34, 0.14, 0.58) < 0.06 || segment(u, v, 0.14, 0.58, 0.4, 0.58) < 0.06;
    }

    private static double segment(double u, double v, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double t = Math.max(0, Math.min(1, ((u - x0) * dx + (v - y0) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(u - x0 - t * dx, v - y0 - t * dy);
    }

    private static boolean star(double u, double v) {
        boolean in = false;
        double px = 0, py = 0, firstX = 0, firstY = 0;
        for (int i = 0; i <= 10; i++) {
            double r = i % 2 == 0 ? 0.5 : 0.21;
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double x = 0.5 + r * Math.cos(a), y = 0.53 + r * Math.sin(a);
            if (i == 0) {
                firstX = x;
                firstY = y;
            } else if (((y > v) != (py > v)) && (u < (px - x) * (v - y) / (py - y) + x)) {
                in = !in;
            }
            px = x;
            py = y;
        }
        return in;
    }
}
