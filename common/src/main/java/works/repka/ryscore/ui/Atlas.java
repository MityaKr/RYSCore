package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Общий атлас кора: глифы шрифта, заготовки фигур, значки и ячейки лежат на одних страницах.
 * Пока подряд рисуется одна страница, всё уходит в один вызов отрисовки ({@link Batch}).
 * Между участками остаётся прозрачная полоса в пиксель, чтобы соседние заготовки не просвечивали.
 */
final class Atlas {
    static final int PAGE = 1024;
    private static final int MAX_PAGES = 8;

    private static final class Page {
        final DynamicTexture texture;
        final ResourceLocation id;
        int x = 1, y = 1, row;
        /** Изменённая область: загружается только она, полная страница весит 4 МБ. */
        int dirtyX0 = PAGE, dirtyY0 = PAGE, dirtyX1, dirtyY1;

        Page(int index) {
            texture = new DynamicTexture(new NativeImage(PAGE, PAGE, true));
            texture.getPixels().fillRect(0, 0, PAGE, PAGE, 0x00FFFFFF);
            texture.upload();
            id = ResourceLocation.fromNamespaceAndPath("ryscore", "atlas/" + index + "_" + Integer.toHexString(System.identityHashCode(this)));
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

    /** Участок атласа: страница и левый верхний угол в пикселях страницы. */
    record Region(int page, int x, int y, int w, int h, int generation) {
        boolean valid() {
            return generation == Atlas.generation;
        }

        float u(float texel) {
            return (x + texel) / PAGE;
        }

        float v(float texel) {
            return (y + texel) / PAGE;
        }
    }

    private static final List<Page> PAGES = new ArrayList<>();
    /** Растёт при сбросе атласа: участки, выданные до сброса, больше не действительны. */
    static int generation;
    private static Region white;

    private Atlas() {}

    static boolean fits(int w, int h) {
        return w > 0 && h > 0 && w <= PAGE - 2 && h <= PAGE - 2;
    }

    /** Место под участок w×h; при переполнении атлас начинается заново, прежние участки недействительны. */
    static Region place(int w, int h) {
        if (PAGES.isEmpty()) PAGES.add(new Page(0));
        Page page = PAGES.get(PAGES.size() - 1);
        if (page.x + w + 1 > PAGE) {
            page.x = 1;
            page.y += page.row + 1;
            page.row = 0;
        }
        if (page.y + h + 1 > PAGE) {
            if (PAGES.size() >= MAX_PAGES) reset();
            PAGES.add(new Page(PAGES.size()));
            page = PAGES.get(PAGES.size() - 1);
        }
        Region region = new Region(PAGES.size() - 1, page.x, page.y, w, h, generation);
        page.x += w + 1;
        page.row = Math.max(page.row, h);
        return region;
    }

    /** Пиксели страницы для записи: после записи вызвать {@link #mark}. */
    static NativeImage pixels(Region region) {
        return PAGES.get(region.page).texture.getPixels();
    }

    static void mark(Region region) {
        PAGES.get(region.page).mark(region.x, region.y, region.w, region.h);
    }

    static ResourceLocation id(int page) {
        return PAGES.get(page).id;
    }

    /** Белый квадрат 3×3: заливки идут той же страницей, что фигуры и текст. Выборка берётся из центра. */
    static Region white() {
        if (white != null && white.valid()) return white;
        Region r = place(3, 3);
        NativeImage image = pixels(r);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) image.setPixelRGBA(r.x + x, r.y + y, 0xFFFFFFFF);
        mark(r);
        white = r;
        return r;
    }

    static void upload() {
        for (Page page : PAGES) page.upload();
    }

    private static void reset() {
        // уже накопленные квады ссылаются на старые страницы: рисуем их до освобождения
        Batch.flush();
        for (Page old : PAGES) Minecraft.getInstance().getTextureManager().release(old.id);
        PAGES.clear();
        white = null;
        generation++;
    }
}
