package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * Много одинаковых ячеек за один вызов отрисовки. Ячейка один раз рисуется в текстуру в пикселях экрана,
 * дальше на каждую тратится один прямоугольник: длинный список остаётся таким же быстрым, как без подложек.
 */
public final class Tiles {
    /** Внешний вид ячейки. light и dark заданы только у ванильного бевеля. */
    public record Style(float radius, int fill, int border, int light, int dark, boolean beveled) {}

    private record Key(int pixels, int scale, Style style) {}

    private static final Map<Key, Atlas.Region> CACHE = new HashMap<>();
    private static int cacheGeneration = -1;

    private Tiles() {}

    /** @param xy координаты левых верхних углов подряд: x0, y0, x1, y1… в единицах GUI */
    public static void draw(GuiGraphics g, float size, Style style, int[] xy, int count) {
        if (count <= 0) return;
        double scale = Draw.scale(g);
        int pixels = Math.round(size * (float) scale);
        Atlas.Region r = region(pixels, scale, style);
        if (r == null) return;
        Matrix4f matrix = g.pose().last().pose();
        float u0 = r.u(0), v0 = r.v(0), u1 = r.u(pixels), v1 = r.v(pixels);
        for (int i = 0; i < count; i++) {
            float x = xy[i * 2], y = xy[i * 2 + 1];
            Batch.quad(g, matrix, x, y, x + size, y + size, u0, v0, u1, v1, 0xFFFFFFFF, r.page());
        }
    }

    private static Atlas.Region region(int pixels, double scale, Style style) {
        if (!Atlas.fits(pixels, pixels)) return null;
        if (cacheGeneration != Atlas.generation) {
            CACHE.clear();
            cacheGeneration = Atlas.generation;
        }
        Key key = new Key(pixels, (int) Math.round(scale * 64), style);
        Atlas.Region known = CACHE.get(key);
        if (known != null) return known;
        Atlas.Region r = Atlas.place(pixels, pixels);
        if (cacheGeneration != Atlas.generation) {
            CACHE.clear();
            cacheGeneration = Atlas.generation;
        }
        paint(Atlas.pixels(r), r.x(), r.y(), pixels, scale, style);
        Atlas.mark(r);
        CACHE.put(key, r);
        return r;
    }

    private static void paint(NativeImage image, int ox, int oy, int n, double scale, Style s) {
        int t = Math.max(1, (int) Math.round(scale * (s.beveled ? 1 : 0.5)));
        float r = Math.min(s.radius * (float) scale, n / 2f);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                image.setPixelRGBA(ox + x, oy + y, abgr(s.beveled ? bevelPixel(x, y, n, t, s) : roundPixel(x, y, n, r, t, s)));
            }
        }
    }

    private static int bevelPixel(int x, int y, int n, int t, Style s) {
        if (x < t && y < n - t || y < t && x < n - t) return s.dark;
        if (x >= n - t && y >= t || y >= n - t && x >= t) return s.light;
        return s.fill;
    }

    /** Расстояние до края скруглённого квадрата даёт сглаживание и рамку одной формулой. */
    private static int roundPixel(int x, int y, int n, float r, int t, Style s) {
        float half = n / 2f;
        float qx = Math.abs(x + 0.5f - half) - (half - r);
        float qy = Math.abs(y + 0.5f - half) - (half - r);
        float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
        float d = Math.min(Math.max(qx, qy), 0) + (float) Math.sqrt(ox * ox + oy * oy) - r;
        float outer = clamp(0.5f - d), inner = clamp(0.5f - (d + t));
        return Draw.alpha(Draw.mix(s.border, s.fill, inner), outer);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
    }
}
