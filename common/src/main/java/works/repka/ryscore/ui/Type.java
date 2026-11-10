package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
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
    private static final int PAGE = 1024;
    private static final int MAX_PAGES = 6;
    private static final int SUBPIXELS = 4;
    /** Браузер рисует светлый текст плотнее линейной маски: подобрано по снимкам макетов. */
    private static final double GAMMA = 0.82;

    private record Face(STBTTFontinfo info) {}

    private record Glyph(int page, float u0, float v0, float u1, float v1, int w, int h, int xoff, int yoff) {}

    private static final class Page {
        final DynamicTexture texture;
        final ResourceLocation id;
        int x, y, row;
        /** Изменённая область: загружается только она, полная страница весит 4 МБ. */
        int dirtyX0 = PAGE, dirtyY0 = PAGE, dirtyX1, dirtyY1;

        Page(int index) {
            texture = new DynamicTexture(new NativeImage(PAGE, PAGE, true));
            texture.getPixels().fillRect(0, 0, PAGE, PAGE, 0x00FFFFFF);
            id = ResourceLocation.fromNamespaceAndPath("ryscore", "type/" + index + "_" + Integer.toHexString(System.identityHashCode(this)));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        void mark(int left, int top, int w, int h) {
            dirtyX0 = Math.min(dirtyX0, left);
            dirtyY0 = Math.min(dirtyY0, top);
            dirtyX1 = Math.max(dirtyX1, left + w);
            dirtyY1 = Math.max(dirtyY1, top + h);
        }

        void upload() {
            if (dirtyX1 <= dirtyX0) return;
            texture.bind();
            texture.getPixels().upload(0, dirtyX0, dirtyY0, dirtyX0, dirtyY0, dirtyX1 - dirtyX0, dirtyY1 - dirtyY0, false, false);
            dirtyX0 = dirtyY0 = PAGE;
            dirtyX1 = dirtyY1 = 0;
        }
    }

    private static final Map<Weight, List<Face>> FACES = new EnumMap<>(Weight.class);
    private static final Map<Long, Glyph> GLYPHS = new HashMap<>();
    private static final List<Page> PAGES = new ArrayList<>();
    private static final byte[] GAMMA_TABLE = new byte[256];
    private static boolean failed;
    /** Растёт при сбросе атласа: глифы, найденные до сброса, больше не действительны. */
    private static int generation;

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
            if (in == null) throw new IOException("нет файла " + path);
            byte[] bytes = in.readAllBytes();
            // данные шрифта должны жить, пока жив STBTTFontinfo: память не освобождается намеренно
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            STBTTFontinfo info = STBTTFontinfo.malloc();
            if (!STBTruetype.stbtt_InitFont(info, buffer)) throw new IOException("не разобран " + path);
            return new Face(info);
        } catch (IOException e) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger(Type.class).error("Шрифт не загружен", e);
            return null;
        }
    }

    public static boolean available() {
        return !failed;
    }

    private static Face faceOf(Weight weight, int codepoint) {
        for (Face f : faces(weight)) if (STBTruetype.stbtt_FindGlyphIndex(f.info, codepoint) != 0) return f;
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
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer adv = stack.mallocInt(1), lsb = stack.mallocInt(1);
            STBTruetype.stbtt_GetCodepointHMetrics(face.info, codepoint, adv, lsb);
            return adv.get(0) * STBTruetype.stbtt_ScaleForMappingEmToPixels(face.info, em(designPx));
        }
    }

    /** Заголовочный шрифт только для строк, которые он покрывает целиком: иначе вся строка идёт Inter 600. */
    private static Weight resolved(String text, Weight weight) {
        if (weight != Weight.DISPLAY) return weight;
        List<Face> faces = faces(Weight.DISPLAY);
        if (faces.isEmpty()) return Weight.SEMIBOLD;
        Face display = faces.get(0);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (STBTruetype.stbtt_FindGlyphIndex(display.info, cp) == 0) return Weight.SEMIBOLD;
        }
        return Weight.DISPLAY;
    }

    public static float width(String text, float designPx, Weight requested) {
        Weight weight = resolved(text, requested);
        float w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            w += advance(cp, designPx, weight);
        }
        return w;
    }

    /** @param centerY вертикальная середина строки в единицах GUI */
    public static void draw(GuiGraphics g, String text, float x, float centerY, float designPx, Weight requested, int argb) {
        if (text.isEmpty() || (argb >>> 24) == 0) return;
        Weight weight = resolved(text, requested);
        double scale = Draw.scale();
        float size = (float) (em(designPx) * scale);
        int baseline = Math.round((centerY + CENTER_TO_BASELINE * em(designPx)) * (float) scale);
        int sizeKey = Math.round(size * 8);
        record Quad(Glyph glyph, float x, int y) {}
        List<Quad> quads = new ArrayList<>(text.length());
        float pen = (float) (x * scale);
        List<Runnable> fallback = null;
        int started = generation;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Face face = faceOf(weight, cp);
            if (face == null) {
                final float at = (float) (pen / scale);
                final String s = Character.toString(cp);
                if (fallback == null) fallback = new ArrayList<>();
                fallback.add(() -> drawMissing(g, s, at, centerY, designPx, argb));
                pen += (float) (advance(cp, designPx, weight) * scale);
                continue;
            }
            int whole = (int) Math.floor(pen);
            int sub = Math.min(SUBPIXELS - 1, (int) ((pen - whole) * SUBPIXELS));
            Glyph glyph = glyph(face, weight, cp, sizeKey, size, sub);
            if (glyph != null) quads.add(new Quad(glyph, whole, baseline));
            pen += (float) (advance(cp, designPx, weight) * scale);
        }
        if (generation != started) {
            draw(g, text, x, centerY, designPx, weight, argb);
            return;
        }
        g.flush();
        for (Page page : PAGES) page.upload();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = g.pose().last().pose();
        float inv = (float) (1 / scale);
        for (int p = 0; p < PAGES.size(); p++) {
            BufferBuilder buffer = null;
            for (Quad q : quads) {
                Glyph c = q.glyph;
                if (c.page != p) continue;
                if (buffer == null) buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float x0 = (q.x + c.xoff) * inv, y0 = (q.y + c.yoff) * inv, x1 = x0 + c.w * inv, y1 = y0 + c.h * inv;
                buffer.addVertex(matrix, x0, y0, 0).setUv(c.u0, c.v0).setColor(argb);
                buffer.addVertex(matrix, x0, y1, 0).setUv(c.u0, c.v1).setColor(argb);
                buffer.addVertex(matrix, x1, y1, 0).setUv(c.u1, c.v1).setColor(argb);
                buffer.addVertex(matrix, x1, y0, 0).setUv(c.u1, c.v0).setColor(argb);
            }
            if (buffer != null) {
                RenderSystem.setShaderTexture(0, PAGES.get(p).id);
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }
        }
        RenderSystem.disableBlend();
        if (fallback != null) fallback.forEach(Runnable::run);
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
        long key = ((long) weight.ordinal() << 60) | ((long) sub << 56) | ((long) sizeKey << 32) | cp;
        Glyph known = GLYPHS.get(key);
        if (known != null) return known;
        if (GLYPHS.containsKey(key)) return null;
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
        if (w <= 0 || h <= 0) {
            GLYPHS.put(key, null);
            return null;
        }
        int[] spot = place(w, h);
        Page page = PAGES.get(spot[0]);
        ByteBuffer bitmap = MemoryUtil.memAlloc(w * h);
        try {
            STBTruetype.stbtt_MakeGlyphBitmapSubpixel(info, bitmap, w, h, w, s, s, subX, 0, index);
            NativeImage image = page.texture.getPixels();
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int alpha = GAMMA_TABLE[bitmap.get(py * w + px) & 255] & 255;
                    image.setPixelRGBA(spot[1] + px, spot[2] + py, (alpha << 24) | 0xFFFFFF);
                }
            }
        } finally {
            MemoryUtil.memFree(bitmap);
        }
        page.mark(spot[1], spot[2], w, h);
        Glyph glyph = new Glyph(spot[0], spot[1] / (float) PAGE, spot[2] / (float) PAGE, (spot[1] + w) / (float) PAGE, (spot[2] + h) / (float) PAGE, w, h, x0, y0);
        GLYPHS.put(key, glyph);
        return glyph;
    }

    /** Простая укладка рядами: страница, x, y. */
    private static int[] place(int w, int h) {
        if (PAGES.isEmpty()) PAGES.add(new Page(0));
        Page page = PAGES.get(PAGES.size() - 1);
        if (page.x + w + 1 > PAGE) {
            page.x = 0;
            page.y += page.row + 1;
            page.row = 0;
        }
        if (page.y + h + 1 > PAGE) {
            if (PAGES.size() >= MAX_PAGES) {
                // масштаб менялся много раз: начинаем атлас заново
                for (Page old : PAGES) Minecraft.getInstance().getTextureManager().release(old.id);
                PAGES.clear();
                GLYPHS.clear();
                generation++;
            }
            PAGES.add(new Page(PAGES.size()));
            page = PAGES.get(PAGES.size() - 1);
        }
        int[] spot = {PAGES.size() - 1, page.x, page.y};
        page.x += w + 1;
        page.row = Math.max(page.row, h);
        return spot;
    }
}
