package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/** Значки из макетов: фигура задаётся формулой и рисуется в пикселях экрана, поэтому гладкая на любом масштабе. */
public final class Icons {
    /**
     * Фигуры значков. С {@code MAP} и дальше (1.9.0) значки взяты из макетов книги квестов: контур в квадрате 24
     * с обводкой 2, как в SVG макета ({@link IconPaths}).
     */
    public enum Shape {
        STAR, CROSS, MAGNIFIER, GEAR, ARROW, CHECK, PLUS, PIN, CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_DOWN, GRIP, PENCIL, UNDO, REDO, DOT, HALF_DOT,
        /** Карта: три узла и связи. */
        MAP,
        /** Список: строки с точками; заодно «сетка». */
        LIST,
        /** «Вся книга», задача «побывать». */
        COMPASS,
        /** «К моему квесту». */
        TARGET,
        /** Свернуть боковую панель. */
        SIDEBAR,
        /** Уменьшить масштаб. */
        MINUS,
        /** «Другой путь», ссылка. */
        ROUTE,
        /** Веха, титул, «Вне карты». */
        FLAG,
        /** Ошибка, замечание, врезка «важно». */
        WARN,
        /** «Откроет». */
        KEY,
        /** Видно игрокам, «Игрок увидит». */
        EYE,
        /** Команда. */
        USERS,
        /** Повторяемый квест, «Повторить». */
        REPEAT,
        /** Совет, особая награда. */
        SPARK,
        SAVE,
        ARCHIVE,
        /** «Из руки». */
        HAND,
        /** Показ в гайде. */
        PLAY,
        /** Задача «победить». */
        SKULL,
        /** Статистика, «где застревают». */
        CHART,
        /** Гайд. */
        BOOK,
        /** Дом, база (1.11). */
        HOME,
        /** Слои карты (1.11). */
        LAYERS,
        /** Приват, защита (1.11). */
        SHIELD,
        /** Выделение чанков, кирка (1.11). */
        PICK,
        /** Направление, стрелка игрока на карте (1.11). */
        NAV,
        /** Копировать координаты (1.11). */
        COPY,
        /** Прогрузка чанка (1.11). */
        ANCHOR,
        /** Ещё действия, меню (1.11). */
        DOTS
    }

    /** Контуры значков из макетов (viewBox 24, обводка 2). */
    private static final Map<Shape, double[]> PATHS = new java.util.EnumMap<>(Shape.class);

    static {
        PATHS.put(Shape.MAP, IconPaths.compile("circle 6 6 2.5", "circle 18 12 2.5", "circle 6 18 2.5",
            "M8.5 6H12a3 3 0 0 1 3 3v.6M8.5 18H12a3 3 0 0 0 3-3v-.6"));
        PATHS.put(Shape.LIST, IconPaths.compile("M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"));
        PATHS.put(Shape.COMPASS, IconPaths.compile("circle 12 12 9", "M15.5 8.5l-2 5-5 2 2-5 5-2z"));
        PATHS.put(Shape.TARGET, IconPaths.compile("circle 12 12 7", "circle 12 12 2", "M12 2v3M12 19v3M2 12h3M19 12h3"));
        PATHS.put(Shape.SIDEBAR, IconPaths.compile("rect 3.5 4.5 17 15 2.5", "M9.5 4.5v15"));
        PATHS.put(Shape.MINUS, IconPaths.compile("M5 12h14"));
        PATHS.put(Shape.ROUTE, IconPaths.compile("circle 6 18 2.5", "circle 18 6 2.5", "M8.5 18H14a3 3 0 0 0 3-3V8.5"));
        PATHS.put(Shape.FLAG, IconPaths.compile("M6 21V4M6 4h11l-2 4 2 4H6"));
        PATHS.put(Shape.WARN, IconPaths.compile("M12 4l9 16H3L12 4zM12 10v5M12 17.5h.01"));
        PATHS.put(Shape.KEY, IconPaths.compile("circle 8 15 3.5", "M10.5 12.5L19 4M15 8l2.5 2.5"));
        PATHS.put(Shape.EYE, IconPaths.compile("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", "circle 12 12 2.7"));
        PATHS.put(Shape.USERS, IconPaths.compile("circle 9 9 3.2", "M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M16 6.2a3 3 0 0 1 0 5.6M17.5 14.5c2 .8 3.5 2.9 3.5 5.5"));
        PATHS.put(Shape.REPEAT, IconPaths.compile("M4 12a8 8 0 0 1 14-5l2 2M20 5v4h-4M20 12a8 8 0 0 1-14 5l-2-2M4 19v-4h4"));
        PATHS.put(Shape.SPARK, IconPaths.compile("M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3zM19 16l.7 2.3L22 19l-2.3.7L19 22l-.7-2.3L16 19l2.3-.7L19 16z"));
        PATHS.put(Shape.SAVE, IconPaths.compile("M5 4h11l3 3v13H5V4zM8 4v5h7V4M8 20v-6h8v6"));
        PATHS.put(Shape.ARCHIVE, IconPaths.compile("M4 5h16v5H4zM6 10v9h12v-9M10 14h4"));
        PATHS.put(Shape.HAND, IconPaths.compile("M8 12V6a1.5 1.5 0 0 1 3 0v5M11 11V4.5a1.5 1.5 0 0 1 3 0V11M14 11V6a1.5 1.5 0 0 1 3 0v8a6 6 0 0 1-6 6h-1a5 5 0 0 1-4-2l-2.5-4a1.5 1.5 0 0 1 2.5-1.5L8 14"));
        PATHS.put(Shape.PLAY, IconPaths.compile("M7 5l12 7-12 7V5z"));
        PATHS.put(Shape.SKULL, IconPaths.compile("M5 11a7 7 0 0 1 14 0c0 2.5-1 4-2.5 5v3h-9v-3C6 15 5 13.5 5 11zM9.5 11.5h.01M14.5 11.5h.01M10.5 19v-2M13.5 19v-2"));
        PATHS.put(Shape.CHART, IconPaths.compile("M5 20V10M12 20V4M19 20v-7"));
        PATHS.put(Shape.BOOK, IconPaths.compile("M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3V4zM5 17a3 3 0 0 1 3-3h11"));
        // карта и приват (1.11), контуры из макетов _audit/map
        PATHS.put(Shape.HOME, IconPaths.compile("M4 11l8-7 8 7M6 10v10h12V10M10 20v-6h4v6"));
        PATHS.put(Shape.LAYERS, IconPaths.compile("M12 3l9 5-9 5-9-5 9-5zM3 12.5l9 5 9-5M3 17l9 5 9-5"));
        PATHS.put(Shape.SHIELD, IconPaths.compile("M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3z"));
        PATHS.put(Shape.PICK, IconPaths.compile("M5 5c5-2 10-1 14 3M12 6.5L4 20M12 6.5l1.5 1.5"));
        PATHS.put(Shape.NAV, IconPaths.compile("M12 3l7 17-7-4-7 4 7-17z"));
        PATHS.put(Shape.COPY, IconPaths.compile("rect 8 8 12 12 2", "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"));
        PATHS.put(Shape.ANCHOR, IconPaths.compile("circle 12 5.5 2", "M12 7.5V20M5 13a7 7 0 0 0 14 0M9 11h6"));
        PATHS.put(Shape.DOTS, IconPaths.compile("M6 12h.01M12 12h.01M18 12h.01"));
    }

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
        double[] path = PATHS.get(shape);
        if (path != null) return IconPaths.inside(path, u, v);
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
            default -> false;
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
