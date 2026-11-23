package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Текст из макетов: шрифт Inter, растеризованный в пикселях экрана. Размеры заданы в пикселях макета
 * (окно 1280×800 при GUI Scale 2), поэтому на любом масштабе текст чёткий и той же величины относительно окна.
 * Символы, которых нет в Inter, рисуются шрифтом игры.
 */
public final class Type {
    public enum Weight {
        REGULAR(400), MEDIUM(500), SEMIBOLD(600),
        /** Заголовки макетов: Space Grotesk 600 для строк из латиницы, строка с кириллицей целиком идёт Inter 600. */
        DISPLAY(600);

        final int value;

        Weight(int value) {
            this.value = value;
        }
    }

    /** От центра строки до базовой линии, в долях кегля: так центрирует браузер при flex-выравнивании. */
    private static final float CENTER_TO_BASELINE = 0.3638f;
    private static final int SUBPIXELS = 4;
    /** Порог прозрачности, ниже которого пиксель глифа не рисуется: так было у шейдера position_tex_color, по нему подобрана гамма. */
    private static final int ALPHA_CUTOFF = 26;
    /** Браузер рисует светлый текст плотнее линейной маски: подобрано по снимкам макетов. */
    private static final double GAMMA = 0.82;

    /** Лицо шрифта с кешами: метрики не меняются, а вызов в нативный шрифт дорог при тысячах символов за кадр. */
    private static final class Face {
        /** Кодпоинты ниже этого храним в массивах: латиница, кириллица и знаки. */
        static final int LOW = 0x500;
        final STBTTFontinfo info;
        /** Пикселей на единицу шрифта при кегле 1: масштаб линеен по кеглю. */
        final float unitScale;
        /** 0 — не проверено, 1 — глиф есть, 2 — нет. */
        final byte[] hasLow = new byte[LOW];
        /** Ширина в единицах шрифта; -1 — не измерено. */
        final int[] advanceLow = new int[LOW];
        final Map<Integer, Boolean> hasHigh = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<Integer, Integer> advanceHigh = new java.util.concurrent.ConcurrentHashMap<>();

        Face(STBTTFontinfo info) {
            this.info = info;
            this.unitScale = STBTruetype.stbtt_ScaleForMappingEmToPixels(info, 1f);
            java.util.Arrays.fill(advanceLow, -1);
        }

        boolean has(int cp) {
            if (cp >= 0 && cp < LOW) {
                byte known = hasLow[cp];
                if (known == 0) hasLow[cp] = known = STBTruetype.stbtt_FindGlyphIndex(info, cp) != 0 ? (byte) 1 : (byte) 2;
                return known == 1;
            }
            return hasHigh.computeIfAbsent(cp, c -> STBTruetype.stbtt_FindGlyphIndex(info, c) != 0);
        }

        int advanceUnits(int cp) {
            if (cp >= 0 && cp < LOW) {
                int known = advanceLow[cp];
                if (known < 0) advanceLow[cp] = known = measure(cp);
                return known;
            }
            return advanceHigh.computeIfAbsent(cp, this::measure);
        }

        private int measure(int cp) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer adv = stack.mallocInt(1), lsb = stack.mallocInt(1);
                STBTruetype.stbtt_GetCodepointHMetrics(info, cp, adv, lsb);
                return adv.get(0);
            }
        }
    }

    private record Glyph(int page, float u0, float v0, float u1, float v1, int w, int h, int xoff, int yoff) {}

    /** Глиф без пикселей (пробел): хранится маркером, чтобы промах не искался второй раз. */
    private static final Glyph EMPTY = new Glyph(-1, 0, 0, 0, 0, 0, 0, 0, 0);

    private static final Map<Weight, List<Face>> FACES = new EnumMap<>(Weight.class);
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Glyph> GLYPHS = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private static final byte[] GAMMA_TABLE = new byte[256];
    private static boolean failed;
    /** Поколение атласа, к которому относятся глифы в {@link #GLYPHS}. */
    private static int glyphGeneration = -1;

    static {
        for (int i = 0; i < 256; i++) GAMMA_TABLE[i] = (byte) Math.round(255 * Math.pow(i / 255.0, GAMMA));
    }

    private Type() {}

    private static List<Face> faces(Weight weight) {
        return FACES.computeIfAbsent(weight, w -> {
            List<Face> list = new ArrayList<>();
            if (w == Weight.DISPLAY) {
                Face display = load("/assets/ryscore/fonts/spacegrotesk_latin_600.ttf");
                if (display != null) list.add(display);
            }
            for (String part : new String[] {"latin", "cyrillic"}) {
                Face face = load("/assets/ryscore/fonts/inter_" + part + "_" + w.value + ".ttf");
                if (face != null) list.add(face);
            }
            return list;
        });
    }

    private static Face load(String path) {
        try (InputStream in = Type.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("no file " + path);
            byte[] bytes = in.readAllBytes();
            // данные шрифта должны жить, пока жив STBTTFontinfo: память не освобождается намеренно
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            STBTTFontinfo info = STBTTFontinfo.malloc();
            if (!STBTruetype.stbtt_InitFont(info, buffer)) throw new IOException("not parsed: " + path);
            return new Face(info);
        } catch (IOException e) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger(Type.class).error("Font not loaded", e);
            return null;
        }
    }

    public static boolean available() {
        return !failed;
    }

    private static Face faceOf(Weight weight, int codepoint) {
        for (Face f : faces(weight)) if (f.has(codepoint)) return f;
        return null;
    }

    public static boolean supports(int codepoint) {
        return faceOf(Weight.REGULAR, codepoint) != null;
    }

    /** Кегль в единицах GUI. */
    private static float em(float designPx) {
        return designPx / 2f;
    }

    /** Ширина кодовой точки в единицах GUI: не зависит от масштаба. */
    public static float advance(int codepoint, float designPx, Weight weight) {
        Face face = faceOf(weight, codepoint);
        if (face == null && symbol(codepoint) != null) return em(designPx);
        if (face == null) return Minecraft.getInstance().font.width(Character.toString(codepoint)) * em(designPx) / 8f;
        return face.advanceUnits(codepoint) * face.unitScale * em(designPx);
    }

    /**
     * Каким начертанием на деле пойдёт строка: заголовочный шрифт только для строк, которые он покрывает целиком,
     * иначе вся строка идёт Inter 600. Нужен тем, кто меряет строку по символам.
     */
    public static Weight resolved(String text, Weight weight) {
        if (weight != Weight.DISPLAY) return weight;
        List<Face> faces = faces(Weight.DISPLAY);
        if (faces.isEmpty()) return Weight.SEMIBOLD;
        Face display = faces.get(0);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!display.has(cp)) return Weight.SEMIBOLD;
        }
        return Weight.DISPLAY;
    }

    public static float width(String text, float designPx, Weight requested) {
        return width(text, designPx, requested, 0f);
    }

    /** @param tracking межбуквенный интервал в единицах GUI, между символами (после последнего не добавляется) */
    public static float width(String text, float designPx, Weight requested, float tracking) {
        Weight weight = resolved(text, requested);
        float w = 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            w += advance(cp, designPx, weight);
            count++;
        }
        return count > 1 ? w + tracking * (count - 1) : w;
    }

    /** @param centerY вертикальная середина строки в единицах GUI */
    public static void draw(GuiGraphics g, String text, float x, float centerY, float designPx, Weight requested, int argb) {
        draw(g, text, x, centerY, designPx, requested, argb, 0f);
    }

    /**
     * @param centerY вертикальная середина строки в единицах GUI
     * @param tracking межбуквенный интервал в единицах GUI (подписи капсом); вся строка остаётся одним вызовом
     */
    public static void draw(GuiGraphics g, String text, float x, float centerY, float designPx, Weight requested, int argb, float tracking) {
        if (text.isEmpty() || (argb >>> 24) == 0) return;
        Weight weight = resolved(text, requested);
        double scale = Draw.scale(g);
        float size = (float) (em(designPx) * scale);
        int baseline = Math.round((centerY + CENTER_TO_BASELINE * em(designPx)) * (float) scale);
        int sizeKey = Math.round(size * 8);
        float pen = (float) (x * scale);
        float inv = (float) (1 / scale);
        Matrix4f matrix = g.pose().last().pose();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Face face = faceOf(weight, cp);
            if (face == null) {
                drawMissing(g, Character.toString(cp), (float) (pen / scale), centerY, designPx, argb);
                pen += (float) ((advance(cp, designPx, weight) + tracking) * scale);
                continue;
            }
            int whole = (int) Math.floor(pen);
            int sub = Math.min(SUBPIXELS - 1, (int) ((pen - whole) * SUBPIXELS));
            Glyph c = glyph(face, weight, cp, sizeKey, size, sub);
            if (c != null) {
                // глиф сразу в пачку: при сбросе атласа пачка рисуется до освобождения страниц
                float x0 = (whole + c.xoff) * inv, y0 = (baseline + c.yoff) * inv;
                Batch.quad(g, matrix, x0, y0, x0 + c.w * inv, y0 + c.h * inv, c.u0, c.v0, c.u1, c.v1, argb, c.page);
            }
            pen += (float) ((advance(cp, designPx, weight) + tracking) * scale);
        }
    }

    private record Symbol(Icons.Shape shape, float degrees) {}

    /** Знаки, которых нет в шрифте: пиксельный глиф при малом кегле теряет форму, поэтому они рисуются значками. */
    private static @org.jetbrains.annotations.Nullable Symbol symbol(int cp) {
        return switch (cp) {
            case '✓', '✔' -> new Symbol(Icons.Shape.CHECK, 0);
            case '✕', '✖', '✗' -> new Symbol(Icons.Shape.CROSS, 0);
            case '★' -> new Symbol(Icons.Shape.STAR, 0);
            case '→' -> new Symbol(Icons.Shape.ARROW, 0);
            case '↘' -> new Symbol(Icons.Shape.ARROW, 45);
            case '↓' -> new Symbol(Icons.Shape.ARROW, 90);
            case '↙' -> new Symbol(Icons.Shape.ARROW, 135);
            case '←' -> new Symbol(Icons.Shape.ARROW, 180);
            case '↖' -> new Symbol(Icons.Shape.ARROW, 225);
            case '↑' -> new Symbol(Icons.Shape.ARROW, 270);
            case '↗' -> new Symbol(Icons.Shape.ARROW, 315);
            case '‹' -> new Symbol(Icons.Shape.CHEVRON_LEFT, 0);
            case '›', '▸', '▶' -> new Symbol(Icons.Shape.CHEVRON_RIGHT, 0);
            case '▾', '▼' -> new Symbol(Icons.Shape.CHEVRON_DOWN, 0);
            case '●' -> new Symbol(Icons.Shape.DOT, 0);
            case '◐' -> new Symbol(Icons.Shape.HALF_DOT, 0);
            case '✎', '✏' -> new Symbol(Icons.Shape.PENCIL, 0);
            case '↶' -> new Symbol(Icons.Shape.UNDO, 0);
            case '↷' -> new Symbol(Icons.Shape.REDO, 0);
            case '⠿' -> new Symbol(Icons.Shape.GRIP, 0);
            default -> null;
        };
    }

    private static void drawMissing(GuiGraphics g, String s, float x, float centerY, float designPx, int argb) {
        Symbol symbol = symbol(s.codePointAt(0));
        if (symbol == null) {
            drawVanilla(g, s, x, centerY, designPx, argb);
            return;
        }
        float size = em(designPx);
        Icons.drawRotated(g, symbol.shape(), x, centerY - size / 2f, size, symbol.degrees(), argb);
    }

    private static void drawVanilla(GuiGraphics g, String s, float x, float centerY, float designPx, int argb) {
        float k = em(designPx) / 8f;
        g.pose().pushPose();
        g.pose().translate(x, centerY - 3.5f * k, 0);
        g.pose().scale(k, k, 1f);
        g.drawString(Minecraft.getInstance().font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    private static Glyph glyph(Face face, Weight weight, int cp, int sizeKey, float size, int sub) {
        if (glyphGeneration != Atlas.generation) {
            GLYPHS.clear();
            glyphGeneration = Atlas.generation;
        }
        long key = ((long) weight.ordinal() << 60) | ((long) sub << 56) | ((long) sizeKey << 32) | cp;
        Glyph known = GLYPHS.get(key);
        if (known != null) return known == EMPTY ? null : known;
        STBTTFontinfo info = face.info;
        int index = STBTruetype.stbtt_FindGlyphIndex(info, cp);
        float s = STBTruetype.stbtt_ScaleForMappingEmToPixels(info, size);
        float subX = sub / (float) SUBPIXELS;
        int x0, y0, w, h;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer a = stack.mallocInt(1), b = stack.mallocInt(1), c = stack.mallocInt(1), d = stack.mallocInt(1);
            STBTruetype.stbtt_GetGlyphBitmapBoxSubpixel(info, index, s, s, subX, 0, a, b, c, d);
            x0 = a.get(0);
            y0 = b.get(0);
            w = c.get(0) - x0;
            h = d.get(0) - y0;
        }
        if (w <= 0 || h <= 0 || !Atlas.fits(w, h)) {
            GLYPHS.put(key, EMPTY);
            return null;
        }
        Atlas.Region spot = Atlas.place(w, h);
        if (glyphGeneration != Atlas.generation) {
            // атлас начат заново ради этого глифа: прежние записи недействительны
            GLYPHS.clear();
            glyphGeneration = Atlas.generation;
        }
        ByteBuffer bitmap = MemoryUtil.memAlloc(w * h);
        try {
            STBTruetype.stbtt_MakeGlyphBitmapSubpixel(info, bitmap, w, h, w, s, s, subX, 0, index);
            NativeImage image = Atlas.pixels(spot);
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int alpha = GAMMA_TABLE[bitmap.get(py * w + px) & 255] & 255;
                    if (alpha < ALPHA_CUTOFF) alpha = 0;
                    image.setPixelRGBA(spot.x() + px, spot.y() + py, (alpha << 24) | 0xFFFFFF);
                }
            }
        } finally {
            MemoryUtil.memFree(bitmap);
        }
        Atlas.mark(spot);
        Glyph glyph = new Glyph(spot.page(), spot.u(0), spot.v(0), spot.u(w), spot.v(h), w, h, x0, y0);
        GLYPHS.put(key, glyph);
        return glyph;
    }
}
