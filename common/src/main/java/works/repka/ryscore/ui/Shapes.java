package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * Скруглённые фигуры из заготовок: фигура один раз растеризуется в белый участок общего атласа
 * и рисуется девятью кусками, окрашенными цветом, в общей пачке ({@link Batch}). Пиксели те же, что при построчной заливке.
 */
final class Shapes {
    private static final Map<ShapeRaster.Plan, Atlas.Region> CACHE = new HashMap<>();
    private static int cacheGeneration = -1;
    private static boolean warnedTooBig;

    private Shapes() {}

    /** Рисует заготовку в прямоугольнике (x, y, w, h) пикселей экрана; матрица уже переведена в пиксели. */
    static void draw(GuiGraphics g, ShapeRaster.Plan plan, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        Atlas.Region region = region(plan);
        if (region == null) return;
        int[] sx = ShapeRaster.slices(w, plan.width, plan.stretchX);
        int[] sy = ShapeRaster.slices(h, plan.height, plan.stretchY);
        Matrix4f m = g.pose().last().pose();
        for (int j = 0; j < sy.length; j += 4) {
            if (sy[j + 1] <= 0) continue;
            float y0 = y + sy[j], y1 = y0 + sy[j + 1], v0 = region.v(sy[j + 2]), v1 = region.v(sy[j + 2] + sy[j + 3]);
            for (int i = 0; i < sx.length; i += 4) {
                if (sx[i + 1] <= 0) continue;
                float x0 = x + sx[i], x1 = x0 + sx[i + 1], u0 = region.u(sx[i + 2]), u1 = region.u(sx[i + 2] + sx[i + 3]);
                Batch.quad(g, m, x0, y0, x1, y1, u0, v0, u1, v1, argb, region.page());
            }
        }
    }

    private static Atlas.Region region(ShapeRaster.Plan plan) {
        if (cacheGeneration != Atlas.generation) {
            CACHE.clear();
            cacheGeneration = Atlas.generation;
        }
        Atlas.Region cached = CACHE.get(plan);
        if (cached != null) return cached;
        if (!Atlas.fits(plan.width, plan.height)) {
            if (!warnedTooBig) {
                warnedTooBig = true;
                org.slf4j.LoggerFactory.getLogger(Shapes.class).warn("Shape {}x{} is larger than an atlas page, not drawn", plan.width, plan.height);
            }
            return null;
        }
        ShapeRaster.Canvas canvas = plan.paint();
        Atlas.Region region = Atlas.place(canvas.width, canvas.height);
        if (cacheGeneration != Atlas.generation) {
            // атлас начат заново ради этой заготовки
            CACHE.clear();
            cacheGeneration = Atlas.generation;
        }
        NativeImage image = Atlas.pixels(region);
        for (int y = 0; y < canvas.height; y++) {
            for (int x = 0; x < canvas.width; x++) {
                int a = Math.round(canvas.alpha[y * canvas.width + x] * 255f);
                image.setPixelRGBA(region.x() + x, region.y() + y, a << 24 | 0xFFFFFF);
            }
        }
        Atlas.mark(region);
        CACHE.put(plan, region);
        return region;
    }
}
